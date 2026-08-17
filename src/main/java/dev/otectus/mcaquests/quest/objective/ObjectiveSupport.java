package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
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
}
