package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * Rolls a loot table and gives the results, dropping any overflow (spec section 15). Gated by
 * {@code allowLootTableRewards}.
 */
public record LootTableReward(ResourceLocation lootTable) implements QuestReward {

    public static final Codec<LootTableReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("loot_table").forGetter(LootTableReward::lootTable)
    ).apply(instance, LootTableReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.LOOT_TABLE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.loot_table");
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!McaQuestsConfig.COMMON.allowLootTableRewards.get()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        LootTable table = level.getServer().getLootData().getLootTable(lootTable);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ADVANCEMENT_REWARD);
        for (ItemStack stack : table.getRandomItems(params)) {
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }
}
