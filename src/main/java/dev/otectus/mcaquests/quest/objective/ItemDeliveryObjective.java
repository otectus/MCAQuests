package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "Bring me N of an item." Possession-based: completion is the player currently holding {@code count}
 * of {@code item}; items are consumed (if {@code consume}) only at turn-in (spec sections 14, 19).
 */
public record ItemDeliveryObjective(Item item, int count, boolean consume) implements QuestObjective {

    public static final Codec<ItemDeliveryObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemDeliveryObjective::item),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(ItemDeliveryObjective::count),
            Codec.BOOL.lenientOptionalFieldOf("consume", true).forGetter(ItemDeliveryObjective::consume)
    ).apply(instance, ItemDeliveryObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.ITEM_DELIVERY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.item_delivery", count, item.getDescription());
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

    @Override
    public void consumeOnTurnIn(ServerPlayer player, ObjectiveProgress progress) {
        if (!consume) {
            return;
        }
        int remaining = count;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private int countInInventory(ServerPlayer player) {
        int found = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }
}
