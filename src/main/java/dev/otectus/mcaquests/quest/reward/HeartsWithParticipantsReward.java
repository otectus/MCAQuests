package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Project reward: adds MCA hearts between the contributing players and the participating villagers
 * (spec 0.4.0). The amount/recipients need the full project context, so this is delivered by the
 * project reward distributor (which iterates participants and resolves villagers, queuing hearts for
 * unloaded ones); {@link #grant} is a no-op fallback. {@code includeResidents} extends the recipient
 * villagers from just sponsors to every village resident.
 */
public record HeartsWithParticipantsReward(int amount, boolean includeResidents) implements QuestReward {

    public static final Codec<HeartsWithParticipantsReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(HeartsWithParticipantsReward::amount),
            Codec.BOOL.lenientOptionalFieldOf("include_residents", false).forGetter(HeartsWithParticipantsReward::includeResidents)
    ).apply(instance, HeartsWithParticipantsReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.HEARTS_WITH_PARTICIPANTS;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.hearts_with_participants", effectiveAmount());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        // Distributed by ProjectRewardDistributor with full participant/village context.
    }

    public int effectiveAmount() {
        int scaled = Math.round(amount * McaQuestsConfig.COMMON.heartsRewardMultiplier.get().floatValue());
        return Mth.clamp(scaled,
                McaQuestsConfig.COMMON.minHeartsReward.get(),
                McaQuestsConfig.COMMON.maxHeartsReward.get());
    }
}
