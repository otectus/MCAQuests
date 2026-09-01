package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Small shared helpers for the NPC/village objective types: resolving the quest giver from the
 * persisted snapshot, proximity tests, and hostile-mob classification. Kept here so the objective
 * classes and {@code QuestProgressEvents} share one implementation.
 */
public final class ObjectiveSupport {

    private ObjectiveSupport() {
    }

    /** The quest giver entity if it is currently loaded in {@code level}, else empty. */
    public static Optional<Entity> giver(ServerLevel level, ActiveQuest active) {
        return Optional.ofNullable(level.getEntity(active.villagerUuid()));
    }

    /**
     * Resolves {@code target}, <b>locking its UUID into {@code progress} on the first resolution</b> so every
     * later call — the objective line, the highlight, and the credit check — means the same villager for the
     * rest of the quest.
     *
     * <p>Only {@code "mode": "family"} is locked. That is the mode that needs it:
     * {@code McaCompat.findGiverRelative} prefers whichever relative happens to be loaded, so an unlocked
     * family target silently moves between a giver's children as chunks load and unload.
     * {@code self} and {@code uuid} already name exactly one villager, and {@code profession} is deliberately
     * left live — pinning "a weaponsmith" to one smith would dead-end the quest if that smith dies or moves
     * away, where re-resolving simply finds another.
     *
     * <p>Quests accepted before the binding existed have no locked id and bind here on their next resolution,
     * which is why this locks lazily rather than relying on the bind at accept.
     *
     * <p>Returns empty when the bound villager is not currently loaded — the objective pauses rather than
     * failing, matching every other target resolution in this package.
     */
    public static Optional<LivingEntity> resolveLocked(VillagerTarget target, ServerPlayer player,
                                                       ActiveQuest active, ObjectiveProgress progress,
                                                       ServerLevel level) {
        return resolveLocked(target, player, active, progress, level, false);
    }

    /**
     * As {@link #resolveLocked}, but {@code lockEveryMode} pins whichever villager resolved regardless of the
     * selector's mode. Used by the escort objective, whose escortee must not change identity part-way through
     * even when it was chosen by profession — you are walking one specific person somewhere.
     */
    public static Optional<LivingEntity> resolveLocked(VillagerTarget target, ServerPlayer player,
                                                       ActiveQuest active, ObjectiveProgress progress,
                                                       ServerLevel level, boolean lockEveryMode) {
        UUID locked = progress.targetUuid();
        if (locked != null) {
            return target.resolve(player, active, level, locked);
        }
        Optional<LivingEntity> resolved = target.resolve(player, active, level);
        if (lockEveryMode || target.mode() == VillagerTarget.Mode.FAMILY) {
            resolved.ifPresent(entity -> progress.setTargetUuid(entity.getUUID()));
        }
        return resolved;
    }

    /**
     * Why the villager this objective bound can no longer be found, if they cannot.
     *
     * <p>A quest that has bound a target and then lost them used to sit in the log forever at 0/1 with no
     * explanation: the objective simply never resolved, so it never progressed and never failed. This
     * turns that silence into a sentence, routed through the existing {@code unavailableReason} channel so
     * it renders as a reason line instead of a counter and inherits 1.4.0's suspend-do-not-fail semantics
     * — the deadline stops running down, the quest stays abandonable, and it resumes if they come back.
     *
     * <p>Two states count as lost, and only two:
     * <ul>
     *   <li><b>Deceased.</b> Definite, and never reversible.</li>
     *   <li><b>No body anywhere and on no village roll</b>, for a target that asserted they could be
     *   found. Being merely unloaded is <em>not</em> lost — that is the whole reason the roll is
     *   consulted, and a {@code require: missing} target is supposed to be in exactly this state.</li>
     * </ul>
     *
     * <p>Anything else, including "MCA could not be read", answers empty. A quest is never accused of
     * losing its target on the strength of an unreadable answer.
     */
    public static Optional<Component> boundTargetLost(VillagerTarget target, ActiveQuest active,
                                                      ObjectiveProgress progress, ServerLevel level) {
        UUID bound = progress.targetUuid();
        if (bound == null || level.getEntity(bound) != null) {
            return Optional.empty();
        }
        Optional<RelativeCandidate> who =
                McaCompat.describeVillager(level, level.getEntity(active.villagerUuid()), bound);
        if (who.isEmpty()) {
            return Optional.empty();
        }
        RelativeCandidate villager = who.get();
        Component name = Component.literal(villager.name() == null ? "" : villager.name());
        if (villager.deceased()) {
            return Optional.of(Component.translatable("mcaquests.status.target_died", name));
        }
        if (target.requiresExistence() && !villager.residentAnywhere()) {
            return Optional.of(Component.translatable("mcaquests.status.target_lost", name));
        }
        return Optional.empty();
    }

    /** True when {@code candidate} is the villager {@code target} means, honouring an already-locked binding. */
    public static boolean matchesLocked(VillagerTarget target, LivingEntity candidate, ServerPlayer player,
                                        ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        return target.matches(candidate, player, active, level, progress.targetUuid());
    }

    /** Names the villager {@code target} means, honouring an already-locked binding. */
    public static Component describeLocked(VillagerTarget target, ServerPlayer player, ActiveQuest active,
                                           ObjectiveProgress progress, ServerLevel level) {
        return target.describeResolved(player, active, level, progress.targetUuid());
    }

    /** True for a hostile mob (anything implementing vanilla {@link Enemy}). */
    public static boolean isHostile(Entity entity) {
        return entity instanceof Enemy;
    }

    /** True when {@code entity} is within {@code radius} blocks of the block center {@code pos}. */
    public static boolean withinRadius(Entity entity, BlockPos pos, double radius) {
        double dx = (pos.getX() + 0.5D) - entity.getX();
        double dy = (pos.getY() + 0.5D) - entity.getY();
        double dz = (pos.getZ() + 0.5D) - entity.getZ();
        return (dx * dx + dy * dy + dz * dz) <= radius * radius;
    }

    /**
     * True when {@code entity} is within {@code radius} blocks of the block center {@code pos}
     * <em>horizontally</em> (the Y delta is ignored). Used for "reached this place" arrival tests where a
     * resolved anchor's Y (e.g. an MCA village center) may differ from where the entity actually walks, so
     * a 3D check could make arrival impossible.
     */
    public static boolean withinRadiusXZ(Entity entity, BlockPos pos, double radius) {
        double dx = (pos.getX() + 0.5D) - entity.getX();
        double dz = (pos.getZ() + 0.5D) - entity.getZ();
        return (dx * dx + dz * dz) <= radius * radius;
    }

    /** True when the two entities are within {@code radius} blocks of each other. */
    public static boolean withinRadius(Entity a, Entity b, double radius) {
        return a.distanceToSqr(b) <= radius * radius;
    }

    /** Total count of items matching {@code item} in the player's inventory. */
    public static int countMatching(ServerPlayer player, ItemTarget item) {
        int found = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (item.matches(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Removes up to {@code amount} matching items from the player's inventory; returns the number consumed. */
    public static int consumeMatching(ServerPlayer player, ItemTarget item, int amount) {
        int remaining = amount;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (item.matches(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return amount - remaining;
    }

    // ---------------------------------------------------------------------------------------------
    // "Did the player actually travel?" — the guard on escort_entity / reach_location.
    //
    // Without it, a quest whose subject is ALREADY at the destination is satisfied by the first poll a
    // second after accept, and can be handed straight back to the giver standing right there for
    // currency, XP and hearts — repeatable every cooldown. "Walk me to my bed", offered at night by a
    // villager already at their bed, was the worst case.
    //
    // Two layers use these helpers. At offer time, QuestObjective#isTriviallySatisfied stops such a
    // quest being offered at all. At runtime, the latch below refuses to credit arrival until the
    // subject has genuinely been away from the destination — which is what covers a quest that never
    // passed the offer gate, such as a chain stage or one granted by command.
    // ---------------------------------------------------------------------------------------------

    /** {@code progress.extra()} key: the subject began this quest away from its destination. */
    private static final String K_STARTED_AWAY = "startedAway";

    /**
     * How soon after accept the latch may still be initialised from the subject's position. Beyond it a
     * quest is assumed to be mid-journey and is latched <em>armed</em>, so introducing this guard can
     * never strand a quest a player was already part-way through when the mod updated.
     */
    private static final long ARM_WINDOW_TICKS = 100L;

    /** The journey distance an objective asks for, falling back to the configured default. */
    public static int effectiveMinJourney(Optional<Integer> minJourney) {
        return minJourney.orElseGet(() -> McaQuestsConfig.COMMON.minEscortJourney.get());
    }

    /**
     * True when {@code subject} is close enough to {@code dest} that going there is not a journey: inside
     * the village border for a village anchor, or within whichever is larger of the objective's own
     * arrival radius and {@code minJourney} for any other anchor.
     *
     * <p>Deliberately reuses the village-border test that arrival itself uses, so "already there" and
     * "has arrived" can never disagree about a village anchor.
     */
    public static boolean withinJourneyOf(Entity subject, ServerLevel level, LocationAnchor.Resolved dest,
                                          int arrivalRadius, int minJourney) {
        if (subject == null) {
            return false;
        }
        if (dest.villageId().isPresent() && McaCompat.villageExists(level, dest.villageId().getAsInt())
                && McaCompat.isWithinVillage(level, dest.villageId().getAsInt(), subject.blockPosition())) {
            return true;
        }
        return withinRadiusXZ(subject, dest.pos(), Math.max(arrivalRadius, minJourney));
    }

    /**
     * Whether arrival may be credited yet, initialising and maintaining the one-way "started away" latch
     * in {@code progress}.
     *
     * <p>Semantics, in order:
     * <ul>
     *   <li>No latch yet, and the quest was accepted within {@link #ARM_WINDOW_TICKS}: set it from where
     *   the subject is standing now. This is the real decision — a subject already at the destination
     *   starts <em>disarmed</em>.</li>
     *   <li>No latch yet, and the quest is older: latch <em>armed</em>. The quest predates this guard, so
     *   it must complete exactly as it always would have.</li>
     *   <li>Latched disarmed, but the subject is now outside the journey distance: latch armed. The
     *   journey has genuinely begun, and arriving from here is worth the reward.</li>
     * </ul>
     */
    public static boolean isJourneyArmed(ObjectiveProgress progress, ActiveQuest active, ServerLevel level,
                                         Entity subject, LocationAnchor.Resolved dest,
                                         int arrivalRadius, int minJourney) {
        return isJourneyArmed(progress, level.getGameTime(), active.startGameTime(),
                withinJourneyOf(subject, level, dest, arrivalRadius, minJourney));
    }

    /**
     * The latch itself, over plain values — the world lookups live in the overload above. Split out so
     * the decision table can be tested directly, without a live {@link ServerLevel}.
     *
     * @param gameTime        the level's current game time
     * @param questStartTime  when the owning quest was accepted
     * @param withinJourney   whether the subject is currently too close to the destination to count
     */
    public static boolean isJourneyArmed(ObjectiveProgress progress, long gameTime, long questStartTime,
                                         boolean withinJourney) {
        CompoundTag extra = progress.extra();
        if (!extra.contains(K_STARTED_AWAY)) {
            boolean freshlyAccepted = gameTime - questStartTime <= ARM_WINDOW_TICKS;
            extra.putBoolean(K_STARTED_AWAY, !freshlyAccepted || !withinJourney);
        }
        if (extra.getBoolean(K_STARTED_AWAY)) {
            return true;
        }
        if (!withinJourney) {
            extra.putBoolean(K_STARTED_AWAY, true); // one-way: the journey has begun
            return true;
        }
        return false;
    }
}
