package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Escort a villager (the giver by default) to a {@link LocationAnchor}. Two movement modes:
 * <ul>
 *   <li><b>follow</b> (default): the player leads and the villager trails them (MCA {@code FOLLOW}).</li>
 *   <li><b>lead</b> ({@code "lead": true}): the villager walks to the destination itself, pausing whenever
 *       the player is farther than {@code wait_distance} blocks — so the player must stay close to guard
 *       it. {@code follow} is ignored while leading.</li>
 * </ul>
 * The destination is <b>resolved and frozen on the first poll</b> (so a {@code nearest_village}/villager
 * anchor never drifts as the player or target moves). Arrival is <b>border-aware</b>: for a village anchor
 * the villager only needs to be <em>inside the village border</em> (not within a small radius of its
 * center); for every other anchor it is a horizontal (Y-ignored) distance within {@code radius}. Arrival
 * sticks the objective complete and stops the lead/follow.
 *
 * <p>Evaluation (freeze, staging, arrival, follow re-arm) runs once per second; the {@code lead}
 * <em>actuation</em> ({@link #drive}) runs <b>every tick</b>, re-issuing the walk target so MCA's
 * per-tick brain behaviors cannot steal it (which previously made led villagers stutter). An unloaded
 * villager/anchor pauses the objective — it never false-completes or false-fails (pair with
 * {@code failure.deadline_time} for "before nightfall" and {@code failure.fail_on_giver_death} so it fails
 * if the escorted villager dies).
 */
public record EscortEntityObjective(VillagerTarget villager, LocationAnchor destination,
                                    int radius, boolean follow, boolean lead, int waitDistance,
                                    Optional<Boolean> stageUntilNear, Optional<Integer> minJourney)
        implements QuestObjective, VillagerTargeted {

    public static final MapCodec<EscortEntityObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VillagerTarget.CODEC.optionalFieldOf("villager", VillagerTarget.SELF).forGetter(EscortEntityObjective::villager),
            LocationAnchor.MAP_CODEC.fieldOf("destination").forGetter(EscortEntityObjective::destination),
            Codec.intRange(1, 64).optionalFieldOf("radius", 6).forGetter(EscortEntityObjective::radius),
            Codec.BOOL.optionalFieldOf("follow", true).forGetter(EscortEntityObjective::follow),
            Codec.BOOL.optionalFieldOf("lead", false).forGetter(EscortEntityObjective::lead),
            Codec.intRange(1, 64).optionalFieldOf("wait_distance", 6).forGetter(EscortEntityObjective::waitDistance),
            Codec.BOOL.optionalFieldOf("stage_until_near").forGetter(EscortEntityObjective::stageUntilNear),
            Codec.intRange(0, 512).optionalFieldOf("min_journey").forGetter(EscortEntityObjective::minJourney)
    ).apply(instance, EscortEntityObjective::new));

    /** How far the escortee must start from the destination, defaulting to {@code minEscortJourney}. */
    public int effectiveMinJourney() {
        return ObjectiveSupport.effectiveMinJourney(minJourney);
    }

    /**
     * An escort whose escortee is already at the destination is not a quest, it is a free reward, so the
     * offer is withheld while that is true. Answers {@code false} when the escortee or the anchor cannot
     * be resolved: a quest that might be doable beats one silently withheld.
     */
    @Override
    public boolean isTriviallySatisfied(QuestContext context) {
        Optional<LivingEntity> escortee = villager.resolveFrom(context.player(), context.villager(), context.level());
        if (escortee.isEmpty()) {
            return false;
        }
        return destination.resolveTarget(context.player(), context.villager(), context.level())
                .map(dest -> ObjectiveSupport.withinJourneyOf(escortee.get(), context.level(), dest,
                        radius, effectiveMinJourney()))
                .orElse(false);
    }

    /** Never offered when this objective's destination is a place the giver does not have. */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return destination.unofferableReason(context);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.ESCORT_ENTITY;
    }

    @Override
    public Component describe() {
        String key = lead ? "mcaquests.objective.lead_entity" : "mcaquests.objective.escort_entity";
        return Component.translatable(key, villager.describe(), destination.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        String key = lead ? "mcaquests.objective.lead_entity" : "mcaquests.objective.escort_entity";
        return Component.translatable(key, villager.describeResolved(player, active, level),
                destination.describe(player, active, level));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        String key = lead ? "mcaquests.objective.lead_entity" : "mcaquests.objective.escort_entity";
        return Component.translatable(key,
                ObjectiveSupport.describeLocked(villager, player, active, progress, level),
                destination.describe(player, active, level));
    }

    @Override
    public VillagerTarget targetSelector() {
        return villager;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.count() >= 1 ? Optional.empty() : resolveLocked(player, active, progress, level);
    }

    /**
     * Two answers, in the order the player needs them.
     *
     * <p>A <em>staged</em> escort has a first half the player must do before the escort begins: the
     * escortee is held safe and motionless somewhere out in the world, and nothing happens until the
     * player walks to them. While that is true the marker is the person. Once they are engaged — and
     * for every unstaged escort, from the first poll — the marker is the destination, and the
     * escortee is the thing glowing beside the player instead. That is the whole of "who am I
     * escorting, and where am I taking them", which the mod could previously answer only in words.
     *
     * <p>The destination read here is the <em>frozen</em> one, the same position {@link #drive} walks
     * the villager to, so the marker and the villager can never disagree about where home is.
     *
     * <p>Until it is frozen, the answer is the escortee. That window is under a second for a normal
     * escort — the first poll freezes it — but it never closes at all when the destination anchor
     * cannot resolve, which is an ordinary state: a {@code home_village} or {@code bed} anchor needs
     * the giver loaded, and a {@code townstead_building} one needs the building surveyed. Answering
     * "nothing" there was not merely unhelpful, it was actively harmful: guidance treated a quest that
     * named a person but no place as having answered, and sent an empty payload that removed the
     * marker for <em>every</em> quest the player held. {@code GuidanceService} no longer lets an
     * objective do that, and this no longer tries to: the person you are walking is a true answer to
     * "where next" even before the mod knows where you are walking them.
     */
    @Override
    public Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= 1) {
            return Optional.empty();
        }
        if (isStaged() && !progress.extra().getBoolean(K_ENGAGED)) {
            return escortee(player, active, progress, level);
        }
        return frozenDest(progress)
                .map(dest -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(
                        dest.pos(), level, destination.guidanceKind(dest.villageId().isPresent()),
                        destination.describe(player, active, level), radius, false))
                .or(() -> escortee(player, active, progress, level));
    }

    /** The person being walked, as something to draw. */
    private Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> escortee(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        return resolveLocked(player, active, progress, level)
                .map(escortee -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofEntity(
                        escortee, dev.otectus.mcaquests.quest.guidance.GuidanceKind.VILLAGER,
                        McaCompat.getVillagerDisplayName(escortee)));
    }

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * A <em>staged</em> escort holds a non-giver escortee invulnerable and motionless at its spot until the
     * player reaches it, then engages — the escortee becomes mortal, the escort begins, and its death fails
     * the quest. Auto-enabled for a {@code lead} escort of a villager other than the giver;
     * {@code stage_until_near} overrides (true forces staging, false disables it).
     */
    public boolean isStaged() {
        return stageUntilNear.orElse(lead && villager.mode() != VillagerTarget.Mode.SELF);
    }

    // --- progress.extra() keys -------------------------------------------------------------------
    private static final String K_FROZEN = "destFrozen";   // boolean: destination has been resolved+frozen
    private static final String K_DEST_X = "destX";
    private static final String K_DEST_Y = "destY";
    private static final String K_DEST_Z = "destZ";
    private static final String K_VILLAGE_ID = "destVillageId"; // present only for village anchors
    private static final String K_ENGAGED = "engaged";     // boolean: staged escort has begun
    private static final String K_LEADING = "leading";     // boolean: drive() is actively pushing the escortee

    /** Hysteresis band on the {@code wait_distance} leash so it does not thrash lead/stop at the boundary. */
    private static final double LEASH_BUFFER = 4.0D;

    /** Per-second poll: freeze the destination, stage the escortee, keep/release follow, and stick arrival. */
    public void poll(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= 1) {
            return;
        }
        if (!isStaged()) {
            pollUnstaged(player, active, progress, level);
            return;
        }
        Optional<LivingEntity> target = resolveLocked(player, active, progress, level);
        if (target.isEmpty()) {
            return; // unloaded — pause; the hold is re-asserted when it reloads
        }
        LivingEntity escortee = target.get();
        if (!progress.extra().getBoolean(K_ENGAGED)) {
            // Phase A: hold the escortee safe and motionless until the player reaches it.
            if (escortee.level() == level && ObjectiveSupport.withinRadius(escortee, player, waitDistance)) {
                McaCompat.releaseVillagerHold(escortee);
                progress.extra().putBoolean(K_ENGAGED, true); // one-way latch — the escort has truly begun
            } else {
                McaCompat.holdVillagerInPlace(escortee);
                return;
            }
        }
        // Phase B: engaged — the escortee is mortal now; freeze the destination and stick on arrival.
        McaCompat.releaseVillagerHold(escortee); // idempotent; heals an A→B crossing across a restart
        evalArrival(player, active, escortee, progress, level);
    }

    /** Non-staged escort: the legacy player-leads/villager-follows path, or a lead escort without staging. */
    private void pollUnstaged(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        Optional<LivingEntity> target = resolveLocked(player, active, progress, level);
        if (target.isEmpty()) {
            return; // unloaded — pause, do not fail
        }
        LivingEntity escorted = target.get();
        if (lead) {
            evalArrival(player, active, escorted, progress, level); // drive() handles the movement per tick
            return;
        }
        // follow mode: the player leads and the villager trails via MCA's persistent FOLLOW state.
        Optional<FrozenDest> dest = ensureFrozenDest(player, active, progress, level);
        if (dest.isEmpty()) {
            return; // destination not resolvable yet — pause
        }
        if (isArmed(active, escorted, progress, level, dest.get()) && hasArrived(escorted, level, dest.get())) {
            progress.setCount(1);
            if (follow) {
                McaCompat.setQuestGiverFollow(player, escorted, false);
            }
        } else if (follow) {
            McaCompat.setQuestGiverFollow(player, escorted, true); // idempotent re-arm (FOLLOW is persistent)
        }
    }

    /**
     * Per-tick lead actuation: re-issues the escortee's walk target every tick so MCA's per-tick brain
     * behaviors cannot steal it (the once-per-second {@link #poll} cannot keep up). Cheap — it resolves the
     * locked escortee and reads the <em>frozen</em> destination from {@code extra}, doing no anchor or
     * village lookups. A {@code wait_distance} leash with hysteresis decides when to lead vs. stand and wait.
     */
    public void drive(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= 1 || !lead) {
            return;
        }
        if (isStaged() && !progress.extra().getBoolean(K_ENGAGED)) {
            return; // still held in place during Phase A
        }
        Optional<FrozenDest> dest = frozenDest(progress);
        if (dest.isEmpty()) {
            return; // poll() has not frozen the destination yet
        }
        UUID locked = progress.targetUuid();
        if (locked == null || !(level.getEntity(locked) instanceof LivingEntity escortee)
                || !McaCompat.isMcaVillager(escortee) || escortee.level() != level) {
            return;
        }
        CompoundTag extra = progress.extra();
        boolean wasLeading = extra.getBoolean(K_LEADING);
        double playerSqr = escortee.distanceToSqr(player);
        double startSqr = (double) waitDistance * waitDistance;
        double stopSqr = (waitDistance + LEASH_BUFFER) * (waitDistance + LEASH_BUFFER);
        boolean leading = wasLeading ? playerSqr <= stopSqr : playerSqr <= startSqr;
        if (leading != wasLeading) {
            extra.putBoolean(K_LEADING, leading);
            if (!leading) {
                McaCompat.stopVillagerLeading(escortee); // pause once on the leading→paused edge
            }
        }
        if (leading) {
            McaCompat.leadVillagerTo(player, escortee, dest.get().pos(),
                    McaQuestsConfig.COMMON.leadVillagerSpeed.get());
        }
    }

    /**
     * Freezes the destination if needed and sticks the objective complete once the escortee has arrived —
     * provided the escort was a real journey. A quest that reaches {@code accept} without passing the
     * offer gate (a chain stage, a command) can begin with the escortee already standing at the
     * destination, and crediting that would pay the reward for nothing; see
     * {@link ObjectiveSupport#isJourneyArmed}.
     */
    private void evalArrival(ServerPlayer player, ActiveQuest active, LivingEntity escortee,
                             ObjectiveProgress progress, ServerLevel level) {
        Optional<FrozenDest> dest = ensureFrozenDest(player, active, progress, level);
        if (dest.isEmpty() || !isArmed(active, escortee, progress, level, dest.get())) {
            return;
        }
        if (hasArrived(escortee, level, dest.get())) {
            progress.setCount(1);
            progress.extra().putBoolean(K_LEADING, false);
            McaCompat.stopVillagerLeading(escortee);
        }
    }

    /** Bridges the frozen destination back to a {@link LocationAnchor.Resolved} for the journey latch. */
    private boolean isArmed(ActiveQuest active, LivingEntity escortee, ObjectiveProgress progress,
                            ServerLevel level, FrozenDest dest) {
        return ObjectiveSupport.isJourneyArmed(progress, active, level, escortee,
                new LocationAnchor.Resolved(dest.pos(), dest.villageId()), radius, effectiveMinJourney());
    }

    /**
     * Arrival test: for a village anchor the escortee need only be <em>inside the village border</em>;
     * otherwise it is a horizontal (Y-ignored) distance within {@code radius} of the frozen position.
     */
    private boolean hasArrived(LivingEntity escortee, ServerLevel level, FrozenDest dest) {
        if (escortee.level() != level) {
            return false;
        }
        if (dest.villageId().isPresent() && McaCompat.villageExists(level, dest.villageId().getAsInt())) {
            return McaCompat.isWithinVillage(level, dest.villageId().getAsInt(), escortee.blockPosition());
        }
        return ObjectiveSupport.withinRadiusXZ(escortee, dest.pos(), radius);
    }

    /** A destination resolved once at accept and frozen in {@code extra}: a position and optional village id. */
    private record FrozenDest(BlockPos pos, OptionalInt villageId) {
    }

    /** Reads the frozen destination from {@code extra}, or empty if it has not been frozen yet. */
    private Optional<FrozenDest> frozenDest(ObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        if (!extra.getBoolean(K_FROZEN)) {
            return Optional.empty();
        }
        OptionalInt villageId = extra.contains(K_VILLAGE_ID) ? OptionalInt.of(extra.getInt(K_VILLAGE_ID))
                : OptionalInt.empty();
        BlockPos pos = new BlockPos(extra.getInt(K_DEST_X), extra.getInt(K_DEST_Y), extra.getInt(K_DEST_Z));
        return Optional.of(new FrozenDest(pos, villageId));
    }

    /**
     * Returns the frozen destination, resolving and freezing it on first success. Empty while the anchor
     * cannot resolve yet (unloaded giver / despawned village) so the caller pauses and retries next poll.
     */
    private Optional<FrozenDest> ensureFrozenDest(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        Optional<FrozenDest> existing = frozenDest(progress);
        if (existing.isPresent()) {
            return existing;
        }
        Optional<LocationAnchor.Resolved> resolved = destination.resolveTarget(player, active, level);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = resolved.get().pos();
        CompoundTag extra = progress.extra();
        extra.putInt(K_DEST_X, pos.getX());
        extra.putInt(K_DEST_Y, pos.getY());
        extra.putInt(K_DEST_Z, pos.getZ());
        resolved.get().villageId().ifPresent(id -> extra.putInt(K_VILLAGE_ID, id));
        extra.putBoolean(K_FROZEN, true);
        return Optional.of(new FrozenDest(pos, resolved.get().villageId()));
    }

    /**
     * Resolves the escortee, locking its UUID on first resolution so the same villager is held/led/guarded.
     * Locks for <em>every</em> selector mode, not just {@code family}: you are walking one specific person to
     * a destination, so the escortee must not change identity part-way through even when it was picked by
     * profession.
     */
    private Optional<LivingEntity> resolveLocked(ServerPlayer player, ActiveQuest active,
                                                 ObjectiveProgress progress, ServerLevel level) {
        return ObjectiveSupport.resolveLocked(villager, player, active, progress, level, true);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        String prefix = "Quest '" + questId + "': objective[" + index + "]";
        villager.validate(prefix + " villager", errors);
        destination.validate(prefix + " destination", errors);
        validateDestinationBelongsToEscortee(prefix, errors);
    }

    /**
     * Refuses an escort that walks one villager to another villager's bed.
     *
     * <p>{@code bed} and {@code workstation} resolve the <em>giver's</em> home unless the anchor names
     * somebody, which is right for the common "walk me home" quest where the giver is the person being
     * walked. It is wrong, and silently so, the moment the escortee is somebody else: the shipped
     * "one last walk" quest told the player to see an ageing parent "safely back to their bed" and
     * sent them to the parent's child's house instead. Nothing failed — the escort completed at the
     * wrong building — so the only way to notice was to know both villagers' addresses.
     *
     * <p>Reported as an error rather than a warning because there is no reading of it that is
     * deliberate: an author who genuinely means the giver's bed can say so by naming the giver, and
     * one who means the escortee's can say that. The ambiguous form is the one nobody wants.
     */
    private void validateDestinationBelongsToEscortee(String prefix, List<String> errors) {
        if (destination.type() != LocationAnchor.Type.BED
                && destination.type() != LocationAnchor.Type.WORKSTATION) {
            return;
        }
        if (villager.mode() == VillagerTarget.Mode.SELF) {
            return; // giver and escortee are the same villager; there is nothing to disambiguate
        }
        if (destination.villager().isEmpty()) {
            errors.add(prefix + " escorts a villager other than the giver to a '"
                    + destination.type().name().toLowerCase(java.util.Locale.ROOT)
                    + "' anchor that names nobody, so it resolves to the GIVER's."
                    + " Add \"villager\" to the destination to say whose it is.");
        }
    }
}
