package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Possess a number of matching items (spec section 14). Possession-based like {@code item_delivery}
 * but never consumed — completion is computed live from the inventory.
 */
public record ObtainItemObjective(ItemTarget target, int count) implements QuestObjective {

    public static final Codec<ObtainItemObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemTarget.MAP_CODEC.forGetter(ObtainItemObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ObtainItemObjective::count)
    ).apply(instance, ObtainItemObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.OBTAIN_ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.obtain_item", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(countInInventory(player), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return countInInventory(player) >= count;
    }

    private int countInInventory(ServerPlayer player) {
        int found = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (target.matches(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
