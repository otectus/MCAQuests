package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

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
