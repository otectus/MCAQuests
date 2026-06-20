package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Project reward: adds independent mod-side village reputation for the project's scope (spec 0.4.0).
 * Reputation is keyed by scope identity in {@code ProjectSavedData} and applied once per phase by the
 * project reward distributor; {@link #grant} is a no-op. Usable as a quest reward too: see the
 * {@code village_reputation} special-case in {@code QuestManager.completeQuest}.
 */
public record VillageReputationReward(int amount) implements QuestReward {

    public static final Codec<VillageReputationReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(VillageReputationReward::amount)
    ).apply(instance, VillageReputationReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.VILLAGE_REPUTATION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.village_reputation", amount);
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        // Village-scoped; applied by ProjectRewardDistributor / QuestManager.completeQuest with scope context.
    }
}
