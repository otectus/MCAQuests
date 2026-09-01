package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.target.FrozenLocation;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One accepted, in-flight quest stored on the player (spec section 16). Holds a snapshot of the
 * giver's identity so the quest survives the villager unloading, moving, or dying.
 */
public final class ActiveQuest {

    private final ResourceLocation questId;
    private final UUID villagerUuid;
    private final Component villagerName;
    @Nullable
    private final ResourceLocation villagerProfession;
    private final ResourceLocation dimension;
    private final long startGameTime;
    private final List<ObjectiveProgress> progress;
    /** Frozen template values for a quest accepted from a template (spec: chosen values must not reroll). */
    @Nullable
    private final ResolvedTemplate template;
    /**
     * The open situation this quest was accepted from (0.8.0), or {@code null} for an ordinary quest.
     * Links the per-player quest back to the village-shared {@code SituationInstance} so completion can
     * resolve it. Additive and optional — absent on pre-0.8.0 saves.
     */
    @Nullable
    private final UUID situationInstance;
    /**
     * Reward amounts rolled once at accept time, keyed by index into the quest's reward list — currently
     * only {@code mcaquests:currency}. Persisted so re-opening the menu, reconnecting, or reloading the
     * world all show and pay the same number; there is deliberately no code path that rolls a second time.
     * Absent on pre-1.1.0 saves and on quests with no randomized reward, in which case nothing is written.
     */
    private final Map<Integer, Integer> frozenRewards = new HashMap<>();

    /**
     * Destinations this quest has committed to, keyed by {@link LocationAnchor#fingerprint()}.
     *
     * <p>Held on the quest rather than on each objective's progress for one reason: two objectives
     * that ask for the same thing must get the same answer. "Place six lanterns at the dock" and
     * "place twelve chains at the dock" are one instruction about one dock, and per-objective storage
     * would let them bind to different docks the moment a second one qualified.
     */
    private final Map<String, FrozenLocation> frozenLocations = new HashMap<>();
    private boolean rewardClaimed;
    private boolean readyNotified;
    /**
     * Game ticks this quest has spent unplayable because an optional mod one of its objectives reads
     * was absent (Townstead spec 10.1). Subtracted from "now" when a deadline is checked, so a quest
     * suspended for three in-game days is not instantly failed the moment the mod comes back.
     *
     * <p>Deliberately <em>not</em> folded into {@code startGameTime}: a failure spec may set
     * {@code deadline_time_of_day}, which anchors on the time of day the quest was accepted, and moving
     * the start would retarget that deadline to a different hour of the day instead of merely
     * postponing it. Offsetting the comparison freezes the clock, which is what suspension means, and
     * is correct for both deadline kinds.
     *
     * <p>Absent on saves written before 1.4.0, and on every quest that was never suspended.
     */
    private long suspendedTicks;

    /**
     * Lifecycle phases already announced to Townstead, one bit per
     * {@code TownsteadLifecycle.Phase} (Townstead spec 7.1). Persisted so a reconnect or a restart
     * cannot make a villager react a second time to something that happened days ago. Absent on saves
     * written before 1.4.0 and on every quest that never reached a phase.
     */
    private final java.util.BitSet dispatchedPhases = new java.util.BitSet();

    /** Lazily-built concretized definition, derived from {@link #template}; not persisted. */
    @Nullable
    private transient QuestDefinition resolvedCache;

    public ActiveQuest(ResourceLocation questId, UUID villagerUuid, Component villagerName,
                       @Nullable ResourceLocation villagerProfession, ResourceLocation dimension,
                       long startGameTime, List<ObjectiveProgress> progress, @Nullable ResolvedTemplate template,
                       @Nullable UUID situationInstance) {
        this.questId = questId;
        this.villagerUuid = villagerUuid;
        this.villagerName = villagerName;
        this.villagerProfession = villagerProfession;
        this.dimension = dimension;
        this.startGameTime = startGameTime;
        this.progress = progress;
        this.template = template;
        this.situationInstance = situationInstance;
    }

    /** Fresh acceptance with empty progress for each objective. */
    public static ActiveQuest create(ResourceLocation questId, UUID villagerUuid, Component villagerName,
                                     @Nullable ResourceLocation villagerProfession, ResourceLocation dimension,
                                     long startGameTime, int objectiveCount, @Nullable ResolvedTemplate template) {
        return create(questId, villagerUuid, villagerName, villagerProfession, dimension,
                startGameTime, objectiveCount, template, null);
    }

    /** Fresh acceptance linked to an open situation (0.8.0); {@code situationInstance} may be {@code null}. */
    public static ActiveQuest create(ResourceLocation questId, UUID villagerUuid, Component villagerName,
                                     @Nullable ResourceLocation villagerProfession, ResourceLocation dimension,
                                     long startGameTime, int objectiveCount, @Nullable ResolvedTemplate template,
                                     @Nullable UUID situationInstance) {
        List<ObjectiveProgress> progress = new ArrayList<>();
        for (int i = 0; i < objectiveCount; i++) {
            progress.add(new ObjectiveProgress());
        }
        return new ActiveQuest(questId, villagerUuid, villagerName, villagerProfession, dimension,
                startGameTime, progress, template, situationInstance);
    }

    /**
     * The definition to use for this active quest: the concretized template (objectives/rewards filled
     * from the frozen {@link #template} values) when this came from a template, otherwise {@code base}
     * unchanged. Cached so the per-second progress tick does not re-parse JSON. Falls back to
     * {@code base} if the stored values can no longer be substituted (e.g. a datapack changed).
     */
    public QuestDefinition resolve(QuestDefinition base) {
        if (template == null || base.template().isEmpty()) {
            return base;
        }
        if (resolvedCache == null) {
            resolvedCache = base.template()
                    .flatMap(spec -> spec.toConcrete(template))
                    .map(base::withConcrete)
                    .orElse(base);
        }
        return resolvedCache;
    }

    /**
     * The placeholder resolver for dialogue/title, carrying the reserved {@code {player}} token. Built
     * fresh each call because {@code playerName} is per-recipient; it only wraps the value map (cheap).
     */
    public PlaceholderResolver textResolver(@Nullable String playerName) {
        return template == null
                ? PlaceholderResolver.forPlayerName(playerName)
                : new PlaceholderResolver(template, playerName);
    }

    /** {@link #textResolver(String)} using {@code player}'s MCA name (username fallback) for {@code {player}}. */
    public PlaceholderResolver textResolver(ServerPlayer player) {
        return textResolver(McaCompat.getPlayerName(player));
    }

    public ResourceLocation questId() {
        return questId;
    }

    public UUID villagerUuid() {
        return villagerUuid;
    }

    public Component villagerName() {
        return villagerName;
    }

    @Nullable
    public ResourceLocation villagerProfession() {
        return villagerProfession;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public long startGameTime() {
        return startGameTime;
    }

    /** The open situation this quest was accepted from (0.8.0), or empty for an ordinary quest. */
    public java.util.Optional<UUID> situationInstance() {
        return java.util.Optional.ofNullable(situationInstance);
    }

    public ObjectiveProgress progress(int index) {
        return progress.get(index);
    }

    /**
     * Rolls and stores {@code amount} for reward {@code index} if nothing is stored yet, and returns the
     * stored value either way. Idempotent by construction: the first writer wins, so a duplicated accept
     * packet or a re-entrant call can never replace an amount the player has already been shown.
     */
    public int freezeReward(int index, int amount) {
        return frozenRewards.computeIfAbsent(index, i -> amount);
    }

    /** The amount frozen for reward {@code index}, or empty if that reward has no frozen value. */
    public OptionalInt frozenReward(int index) {
        Integer value = frozenRewards.get(index);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public boolean rewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean claimed) {
        this.rewardClaimed = claimed;
    }

    /** Whether the player has already been notified (toast) that this quest is ready to turn in. */
    /**
     * Records that a lifecycle phase has been announced, and reports whether this call is the one that
     * did it. The single guard behind "every phase fires at most once".
     */
    public boolean markPhaseDispatched(int phase) {
        if (dispatchedPhases.get(phase)) {
            return false;
        }
        dispatchedPhases.set(phase);
        return true;
    }

    /** Ticks spent suspended so far. See the field javadoc for why this is not folded into the start. */
    public long suspendedTicks() {
        return suspendedTicks;
    }

    /** Accrues suspended time. Called once per polling pass while an objective reports unavailable. */
    public void addSuspendedTicks(long delta) {
        if (delta > 0L) {
            this.suspendedTicks += delta;
        }
    }

    /**
     * "Now", with suspended time removed — the value every deadline comparison must use so a quest is
     * never failed for time that passed while it could not be played.
     */
    public long effectiveNow(long gameTime) {
        return gameTime - suspendedTicks;
    }

    /** The destination already chosen for this anchor fingerprint, or null if none has been. */
    @Nullable
    public FrozenLocation frozenLocation(String fingerprint) {
        return frozenLocations.get(fingerprint);
    }

    /**
     * Records a chosen destination, the first time only. Later calls are ignored rather than
     * overwriting: a frozen anchor that could be re-frozen would move the map marker under a player
     * mid-journey, which is the whole thing freezing exists to prevent.
     */
    public void freezeLocation(String fingerprint, FrozenLocation location) {
        frozenLocations.putIfAbsent(fingerprint, location);
    }

    /**
     * Any frozen destination that named a registered building, for the context line. Returns the first
     * because a single quest that froze two different building families is a shape no bundled content
     * uses and one the card has no room to show twice.
     */
    @Nullable
    public FrozenLocation anyFrozenBuilding() {
        for (FrozenLocation location : frozenLocations.values()) {
            if (location.family().isPresent()) {
                return location;
            }
        }
        return null;
    }

    public boolean readyNotified() {
        return readyNotified;
    }

    public void setReadyNotified(boolean notified) {
        this.readyNotified = notified;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId.toString());
        tag.putUUID("villager", villagerUuid);
        tag.putString("villager_name", Component.Serializer.toJson(villagerName));
        if (villagerProfession != null) {
            tag.putString("profession", villagerProfession.toString());
        }
        tag.putString("dimension", dimension.toString());
        tag.putLong("start", startGameTime);
        tag.putBoolean("claimed", rewardClaimed);
        tag.putBoolean("ready_notified", readyNotified);
        if (suspendedTicks != 0L) {
            tag.putLong("suspended_ticks", suspendedTicks);
        }
        if (!dispatchedPhases.isEmpty()) {
            tag.putByteArray("townstead_phases", dispatchedPhases.toByteArray());
        }
        ListTag list = new ListTag();
        for (ObjectiveProgress p : progress) {
            list.add(p.save());
        }
        tag.put("progress", list);
        if (template != null) {
            tag.put("template", template.save());
        }
        if (situationInstance != null) {
            tag.putUUID("situation", situationInstance);
        }
        if (!frozenRewards.isEmpty()) {
            CompoundTag frozen = new CompoundTag();
            frozenRewards.forEach((index, amount) -> frozen.putInt(String.valueOf(index), amount));
            tag.put("frozen_rewards", frozen);
        }
        if (!frozenLocations.isEmpty()) {
            CompoundTag locations = new CompoundTag();
            frozenLocations.forEach((fingerprint, location) -> locations.put(fingerprint, location.save()));
            tag.put("frozen_locations", locations);
        }
        return tag;
    }

    public static ActiveQuest load(CompoundTag tag) {
        Component name = Component.Serializer.fromJson(tag.getString("villager_name"));
        ResourceLocation profession = tag.contains("profession")
                ? new ResourceLocation(tag.getString("profession")) : null;
        List<ObjectiveProgress> progress = new ArrayList<>();
        ListTag list = tag.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            progress.add(ObjectiveProgress.load(list.getCompound(i)));
        }
        ResolvedTemplate template = tag.contains("template")
                ? ResolvedTemplate.load(tag.getCompound("template")) : null;
        UUID situationInstance = tag.contains("situation") ? tag.getUUID("situation") : null;
        ActiveQuest quest = new ActiveQuest(
                new ResourceLocation(tag.getString("quest")),
                tag.getUUID("villager"),
                name != null ? name : Component.empty(),
                profession,
                new ResourceLocation(tag.getString("dimension")),
                tag.getLong("start"),
                progress,
                template,
                situationInstance);
        quest.rewardClaimed = tag.getBoolean("claimed");
        quest.readyNotified = tag.getBoolean("ready_notified");
        quest.suspendedTicks = tag.getLong("suspended_ticks"); // 0 when absent
        if (tag.contains("townstead_phases", Tag.TAG_BYTE_ARRAY)) {
            quest.dispatchedPhases.or(java.util.BitSet.valueOf(tag.getByteArray("townstead_phases")));
        }
        if (tag.contains("frozen_locations", Tag.TAG_COMPOUND)) {
            CompoundTag locations = tag.getCompound("frozen_locations");
            for (String fingerprint : locations.getAllKeys()) {
                FrozenLocation location = FrozenLocation.load(locations.getCompound(fingerprint));
                if (location != null) {
                    quest.frozenLocations.put(fingerprint, location);
                }
            }
        }
        if (tag.contains("frozen_rewards", Tag.TAG_COMPOUND)) {
            CompoundTag frozen = tag.getCompound("frozen_rewards");
            for (String key : frozen.getAllKeys()) {
                try {
                    quest.frozenRewards.put(Integer.parseInt(key), frozen.getInt(key));
                } catch (NumberFormatException ignored) {
                    // Drop an unparseable index rather than failing the whole quest load.
                }
            }
        }
        return quest;
    }
}
