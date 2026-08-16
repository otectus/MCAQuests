package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Project reward: unlocks (seeds) a follow-up project on completion (spec 0.4.0). Handled by the
 * project reward distributor, which starts the target project in the same scope; {@link #grant} is a
 * no-op.
 */
public record UnlockReward(ResourceLocation target) implements QuestReward {

    public static final Codec<UnlockReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("target").forGetter(UnlockReward::target)
    ).apply(instance, UnlockReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.UNLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.unlock");
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        // Handled by ProjectRewardDistributor with scope context.
    }
}
