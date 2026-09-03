package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.QuestGuidanceS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.situation.QuestDefinitions;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.PlayerQuestData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out where each of a player's quests is sending them, and pushes the set to them when it
 * changes.
 *
 * <h2>Every quest answers; one of them gets the beam</h2>
 *
 * <p>The first version of this asked one quest and stopped. That was right for the marker — a beam
 * per quest is five beacons for five quests, which is the mistake highlighting used to make — but it
 * was quietly wrong for the tracker, which lists every active quest and could therefore only tell the
 * player where <em>one</em> of them was going. The two answers are now separated:
 *
 * <ol>
 *   <li><b>{@link #forQuest} asks one quest</b>, walking its objectives in the order the pack
 *       declared them and taking the first that is neither satisfied nor suspended and can say where
 *       to go. Declaration order is not an arbitrary choice: it is how the log already lists
 *       objectives and how the bundled quests are written — {@code nether_relay} is <em>reach the
 *       Nether, then kill blazes, then bring back the rods</em>, which is the route. So each quest's
 *       destination walks itself forward as its steps land.</li>
 *   <li><b>{@link #snapshot} asks all of them</b> and marks one as primary: the quest the player is
 *       following if it could answer, otherwise the first that could. That one gets the beam and the
 *       villager outline; the rest get a line of tracker text and a map waypoint.</li>
 * </ol>
 *
 * <h2>A quest answers with a place, never with a person alone</h2>
 *
 * <p>This is the fix for a bug of the same shape as the one the fall-through was introduced for. The
 * focus used to count an objective as "the answer" when it produced <em>either</em> a target
 * <em>or</em> a villager to outline, and turning a focus into something sendable then mapped over the
 * target — so an objective with a villager and no place sent an <b>empty</b> payload, which is how a
 * marker is taken away, and returned without asking anything else. An unstaged {@code escort_entity}
 * is exactly that combination in the window before its first poll freezes the destination, and
 * permanently when the destination anchor cannot resolve at all. One escort could therefore switch
 * the marker off for every other quest the player held.
 *
 * <p>So a quest answers only when it has a place, and the walk carries on past an objective that
 * knows a person but not where to find one. The villager to outline still comes from the same
 * objective that named the place, which is the invariant this class exists to hold: the beam and the
 * outline are decided by one walk of one quest and cannot end up on different ones.
 *
 * <h2>Cost</h2>
 *
 * <p>Recomputed on the same once-a-second poll the highlight uses, and diffed against what the player
 * was last sent — a marker on a bed is the same marker a second later, so the common case is zero
 * packets. Asking every quest instead of one raises the ceiling on world searches, so the pass runs
 * inside {@link LocateCache#beginPass()}, which caps real searches at {@code guidanceSearchesPerPass}
 * and leaves the quests that did not get a turn to try again next pass rather than recording a miss
 * they never made.
 */
public final class GuidanceService {

    /** Last snapshot sent to each player, so an unchanged answer costs nothing. Cleared on logout. */
    private static final Map<UUID, GuidanceSnapshot> LAST_SENT = new ConcurrentHashMap<>();

    /** Players whose guidance a mutation has invalidated, recomputed once at end of tick. */
    private static final GuidanceDirtySet DIRTY = new GuidanceDirtySet();

    private GuidanceService() {
    }

    /**
     * Recomputes {@code player}'s guidance and sends it if it differs from what they were last sent.
     *
     * @return the villager the marked quest wants outlined right now, so the highlight and the marker
     *         are decided by one walk of one quest and cannot disagree about which villager matters
     */
    public static Optional<LivingEntity> update(ServerPlayer player, ServerLevel level,
                                                PlayerQuestData data) {
        Walk walk = walk(player, level, data);
        send(player, walk.snapshot());
        return walk.markedVillager();
    }

    /**
     * Where every one of {@code player}'s quests would send them right now, without sending anything.
     *
     * <p>Exists for {@code /mcaquests debug guidance}. A destination that does not appear has several
     * possible causes — an objective with no place attached, a search that found nothing in range, a
     * config switched off — and none of them are distinguishable from the outside, which is how the
     * first version of this shipped looking broken when it was merely silent.
     */
    public static GuidanceSnapshot snapshot(ServerPlayer player, ServerLevel level,
                                            PlayerQuestData data) {
        return walk(player, level, data).snapshot();
    }

    /**
     * Where one quest is sending the player right now, or empty when it cannot say.
     *
     * <p>The single entry point for "ask this quest": the marker, the tracker, the map waypoints and
     * {@code /mcaquests debug guidance} all come through here, so none of them can develop its own
     * idea of what a quest is about. The debug command used to fake this by building a throwaway
     * {@link PlayerQuestData} that held one quest and asking the whole service about it.
     */
    public static Optional<ActiveGuidance> forQuest(ServerPlayer player, ActiveQuest active,
                                                    ServerLevel level) {
        return focusOf(player, active, level).asGuidance(active);
    }

    /** One walk over every active quest: the snapshot to send, and who to outline. */
    private record Walk(GuidanceSnapshot snapshot, Optional<LivingEntity> markedVillager) {
    }

    private static Walk walk(ServerPlayer player, ServerLevel level, PlayerQuestData data) {
        Optional<ActiveQuest> tracked = data.trackedQuest();
        List<ActiveQuest> active = data.active();
        List<Optional<ActiveGuidance>> answers = new ArrayList<>(active.size());
        List<Optional<LivingEntity>> villagers = new ArrayList<>(active.size());
        int trackedIndex = -1;
        LocateCache.beginPass();
        try {
            for (int i = 0; i < active.size(); i++) {
                ActiveQuest quest = active.get(i);
                if (tracked.isPresent() && tracked.get() == quest) {
                    trackedIndex = i;
                }
                Focus focus = focusOf(player, quest, level);
                answers.add(focus.asGuidance(quest));
                villagers.add(focus.villager());
            }
        } finally {
            LocateCache.endPass();
        }
        // Which quest takes the marker is a rule about a list of answers, not about the world, so it
        // lives on the snapshot where it can be exercised without a running server.
        GuidanceSnapshot snapshot = GuidanceSnapshot.select(answers, trackedIndex);
        Optional<LivingEntity> outlined = snapshot.primaryGuidance()
                .map(primary -> villagers.get(indexOf(answers, primary)))
                .orElseGet(Optional::empty);
        return new Walk(snapshot, outlined);
    }

    /** Where {@code guidance} sat in the per-quest answers, so the outline comes from the same quest. */
    private static int indexOf(List<Optional<ActiveGuidance>> answers, ActiveGuidance guidance) {
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i).filter(guidance::equals).isPresent()) {
                return i;
            }
        }
        return 0; // unreachable: the snapshot is built from these answers
    }

    /** One quest's focus, or {@link Focus#NONE} when its definition has gone. */
    private static Focus focusOf(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return QuestDefinitions.resolve(active.questId())
                .map(base -> focus(player, active, active.resolve(base), level))
                .orElse(Focus.NONE);
    }

    /**
     * What this quest is currently about: a place to draw, and a villager to outline.
     *
     * <p>They are computed together and returned together on purpose. An escort, for instance, wants
     * the escortee outlined and the <em>destination</em> marked at the same time — "who am I walking,
     * and where am I walking them" — and any arrangement that worked them out in two places would let
     * the outline and the marker drift onto different quests.
     */
    private record Focus(Optional<GuidanceTarget> target, Optional<LivingEntity> villager) {

        private static final Focus NONE = new Focus(Optional.empty(), Optional.empty());

        /** This focus as something to send, tagged with the quest it turned out to be about. */
        Optional<ActiveGuidance> asGuidance(ActiveQuest active) {
            return target.map(t -> new ActiveGuidance(active.questId(), active.villagerUuid(), t));
        }
    }

    private static Focus focus(ServerPlayer player, ActiveQuest active, QuestDefinition def,
                               ServerLevel level) {
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            ObjectiveProgress progress = active.progress(i);
            if (objective.isSatisfied(player, progress)) {
                continue;
            }
            // A suspended objective is one whose subject cannot be read at all — an uninstalled
            // companion mod, a villager who has died. Pointing at it would be pointing at a hole.
            if (objective.unavailableReason(player, active, progress, level).isPresent()) {
                continue;
            }
            Optional<GuidanceTarget> target = objective.guidance(player, active, progress, level);
            // Keep walking when this objective knows a person but not a place. Stopping here was the
            // bug: the quest then sent an empty payload, which removes the marker, and nothing else
            // was asked. See the class javadoc.
            if (target.isEmpty()) {
                continue;
            }
            Optional<LivingEntity> villager = objective
                    instanceof dev.otectus.mcaquests.quest.objective.VillagerTargeted targeted
                    ? targeted.highlightTarget(player, active, progress, level)
                    : Optional.empty();
            return new Focus(target, villager);
        }
        return readyToHandIn(player, active, def, level);
    }

    /**
     * The villager to hand the quest back to, once there is nothing left to do.
     *
     * <p>Only reached when every objective is satisfied, unreadable or placeless, which is close
     * enough to the moment the player needs to be told where to walk. A quest that completes itself,
     * or one a datapack lets any villager accept, points at nobody: there is no journey to make.
     */
    private static Focus readyToHandIn(ServerPlayer player, ActiveQuest active, QuestDefinition def,
                                       ServerLevel level) {
        if (!isComplete(player, active, def)) {
            return Focus.NONE;
        }
        TurnInMode mode = def.turnIn().mode();
        if (mode == TurnInMode.SELF_COMPLETE || mode == TurnInMode.ANY_VILLAGER) {
            return Focus.NONE;
        }
        if (!(level.getEntity(active.villagerUuid()) instanceof LivingEntity giver)
                || !McaCompat.isMcaVillager(giver)) {
            return Focus.NONE;
        }
        return new Focus(Optional.of(GuidanceTarget.ofEntity(giver, GuidanceKind.VILLAGER,
                active.villagerName())), Optional.of(giver));
    }

    private static boolean isComplete(ServerPlayer player, ActiveQuest active, QuestDefinition def) {
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).isSatisfied(player, active.progress(i))) {
                return false;
            }
        }
        return true;
    }

    /** Sends {@code snapshot} to {@code player} if it differs from what they were last sent. */
    public static void send(ServerPlayer player, GuidanceSnapshot snapshot) {
        if (!shouldSend(player.getUUID(), snapshot)) {
            return;
        }
        remember(player.getUUID(), snapshot);
        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new QuestGuidanceS2CPacket(snapshot));
    }

    /**
     * Whether {@code snapshot} is news to this player.
     *
     * <p>The suppression rule on its own, because it is the one thing in the recompute path that can
     * silently swallow a legitimate update: after a death the destinations are recomputed from scratch
     * and can be byte-identical to the ones the player had before dying, which is exactly when the
     * client has nothing left and most needs to be told. {@link #forget} is the counterpart, and the
     * respawn hook calls it for this reason.
     */
    public static boolean shouldSend(UUID playerId, GuidanceSnapshot snapshot) {
        GuidanceSnapshot previous = LAST_SENT.get(playerId);
        return previous == null || !previous.equals(snapshot);
    }

    /** Records what a player was last sent. The counterpart of {@link #shouldSend}, and its only writer. */
    static void remember(UUID playerId, GuidanceSnapshot snapshot) {
        LAST_SENT.put(playerId, snapshot);
    }

    /**
     * Notes that {@code player}'s destinations may have changed, for the recompute at end of tick.
     *
     * <p>Called from the mutations that can change where a player is being sent, so a quest accepted,
     * turned in, abandoned or followed moves the marker and the map on the same tick as the click
     * rather than up to a second later. The once-a-second pass stays as the safety net for the
     * objectives that are inherently polled — an escort walking, a location being reached — since
     * nothing about those is an event anybody could mark on.
     */
    public static void markDirty(ServerPlayer player) {
        DIRTY.mark(player.getUUID());
    }

    /** Every player marked since the last drain; empty on the overwhelming majority of ticks. */
    public static Set<UUID> drainDirty() {
        return DIRTY.drain();
    }

    /**
     * Clears every destination the player currently has, unconditionally.
     *
     * <p>Sent rather than merely forgotten, so a player who turns the feature off or finishes their
     * last quest does not keep a beacon standing in a field and a waypoint on their map.
     */
    public static void clear(ServerPlayer player) {
        send(player, GuidanceSnapshot.EMPTY);
    }

    /**
     * Drops the remembered answer so the next pass resends from scratch.
     *
     * <p>Needed on logout (otherwise the map grows an entry per player forever) and on a dimension
     * change, because a target carries an entity network id and those are per-dimension and reused —
     * a remembered overworld id could otherwise suppress a legitimate send in the Nether.
     */
    public static void forget(UUID playerId) {
        LAST_SENT.remove(playerId);
    }
}
