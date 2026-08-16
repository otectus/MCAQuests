package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/** Gives an item stack; inserts into the inventory or drops at the player if full (spec section 15). */
public record ItemReward(Item item, int count) implements QuestReward {

    public static final Codec<ItemReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemReward::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ItemReward::count)
    ).apply(instance, ItemReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.item", item.getDescription(), count);
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        int remaining = count;
        int maxStack = new ItemStack(item).getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            // giveItemToPlayer inserts what it can and drops the remainder at the player's feet.
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, give));
            remaining -= give;
        }
        McaQuests.LOGGER.debug("Granted {}x {} to {}", count, item, player.getGameProfile().getName());
    }
}
