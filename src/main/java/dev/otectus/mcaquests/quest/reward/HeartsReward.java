package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Adds MCA relationship hearts with the quest giver (spec section 15). The configured multiplier and
 * min/max clamp are applied here so the reward stays balanced. Must run last, after a successful
 * reward delivery (the caller guarantees ordering).
 */
public record HeartsReward(int amount) implements QuestReward {

    public static final Codec<HeartsReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(HeartsReward::amount)
    ).apply(instance, HeartsReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.HEARTS;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.hearts", effectiveAmount());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (villager == null) {
            return; // hearts need a giver; nothing to do if it has gone missing
        }
        McaCompat.addHearts(player, villager, effectiveAmount());
    }

    /** Amount after the configured multiplier and min/max clamp. */
    public int effectiveAmount() {
        int scaled = Math.round(amount * McaQuestsConfig.COMMON.heartsRewardMultiplier.get().floatValue());
        return Mth.clamp(scaled,
                McaQuestsConfig.COMMON.minHeartsReward.get(),
                McaQuestsConfig.COMMON.maxHeartsReward.get());
    }
}
