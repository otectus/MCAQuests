package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.Set;

/**
 * An objective that reads Townstead state, and therefore cannot be evaluated when Townstead is absent
 * or when the specific capability it needs did not bind (Townstead spec §10.1).
 *
 * <p>Implementing this is the whole of what an objective must do to be save-safe. The suspension
 * behaviour — keep progress, stop polling, never auto-fail, never read as complete, show the reason,
 * stay abandonable, resume the original baseline — falls out of
 * {@link #unavailableReason} being consulted at the four points where a quest could otherwise advance
 * or die.
 *
 * <p><b>Capability-precise, not mod-precise.</b> An objective declares only what it actually reads, so
 * a Townstead point release that moved one internal method suspends the handful of quests that needed
 * it rather than every Townstead quest in the world.
 *
 * <p>Suspension is recomputed every pass rather than persisted. A stored flag would need migrating,
 * could go stale against a capability set that changed underneath it, and would have to be cleared by
 * something — where "ask the bridge" is always current and needs no bookkeeping at all.
 */
public interface TownsteadObjective extends QuestObjective {

    /**
     * Every capability this objective reads. All of them must be bound for it to run; a missing one
     * suspends the quest rather than failing it.
     */
    Set<TownsteadCapability> requiredCapabilities();

    @Override
    default Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return Optional.of(Component.translatable("mcaquests.objective.townstead.unavailable"));
        }
        for (TownsteadCapability capability : requiredCapabilities()) {
            if (!bridge.has(capability)) {
                return Optional.of(Component.translatable(
                        "mcaquests.objective.townstead.capability_missing"));
            }
        }
        return Optional.empty();
    }

    /**
     * Take the starting reading, once, at the moment the quest is accepted.
     *
     * <p>Called from {@code QuestManager.accept} rather than left to the first poll, because a second
     * of gameplay is long enough to eat: a player who accepts "feed them back up" and immediately hands
     * over bread would otherwise have that bread counted as the starting state and lose the credit for
     * it. Objectives with nothing to freeze ignore this.
     */
    default void freezeBaseline(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                                ServerLevel level) {
    }


    /**
     * The villager this objective is about, when it is about one.
     *
     * <p>Half the Townstead objectives name a resident — "keep them rested", "get them to journeyman"
     * — and half are about the settlement as a whole. The ones that name somebody say so by overriding
     * this; the rest inherit the village answer below. Nothing else needs to know which is which.
     */
    default Optional<Entity> townsteadSubject(ServerPlayer player, ActiveQuest active,
                                              ObjectiveProgress progress, ServerLevel level) {
        return Optional.empty();
    }

    /**
     * Where a Townstead objective is sending the player: the resident it names, or the village.
     *
     * <p>Sixty-one bundled objectives point somewhere for the first time here. Every one of them was
     * previously a sentence about a place with no place attached — "a week kept well", "the whole
     * flock", "first shift" — and the tracker's answer to *where* was nothing at all, in the family of
     * quests where the village <em>is</em> the subject.
     *
     * <p>The village is the giver's home village rather than the nearest one, because a Townstead
     * quest is about the settlement that asked for it. A giver who is unloaded, or who lives nowhere,
     * yields nothing rather than a guess: the answer would be a marker on a place the quest is not
     * about, and the player would walk to it.
     */
    @Override
    default Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                              ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress) || !townsteadReady()) {
            return Optional.empty();
        }
        Optional<Entity> subject = townsteadSubject(player, active, progress, level);
        if (subject.isPresent()) {
            return subject.map(villager -> GuidanceTarget.ofEntity(villager, GuidanceKind.VILLAGER,
                    McaCompat.getVillagerDisplayName(villager)));
        }
        return homeVillage(active, level);
    }

    /** The giver's home village centre, named, as something to draw. */
    private static Optional<GuidanceTarget> homeVillage(ActiveQuest active, ServerLevel level) {
        /* Wide, because a village is a place you arrive in rather than stand on. */
        final int arriveRadius = 24;
        Entity giver = level.getEntity(active.villagerUuid());
        if (giver == null) {
            return Optional.empty();
        }
        Component name = McaCompat.getHomeVillageName(giver)
                .<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("mcaquests.anchor.village"));
        return McaCompat.getHomeVillageCenter(giver).map(centre -> GuidanceTarget.ofPos(centre, level,
                GuidanceKind.VILLAGE, name, arriveRadius, false));
    }

    /** Convenience for the objective's own guards: true when every declared capability is available. */
    default boolean townsteadReady() {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return false;
        }
        for (TownsteadCapability capability : requiredCapabilities()) {
            if (!bridge.has(capability)) {
                return false;
            }
        }
        return true;
    }
}
