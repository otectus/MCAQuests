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
 * Project reward: adds MCA hearts between the contributing player and the project's sponsor villager
 * (spec 0.4.0). Behaves like {@code HeartsReward} but is documented as the sponsor; the project reward
 * distributor passes the resolved sponsor as the {@code villager}. Respects the same config clamp.
 */
public record HeartsWithSponsorReward(int amount) implements QuestReward {

    public static final Codec<HeartsWithSponsorReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(HeartsWithSponsorReward::amount)
    ).apply(instance, HeartsWithSponsorReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.HEARTS_WITH_SPONSOR;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.hearts_with_sponsor", effectiveAmount());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (villager == null) {
            return;
        }
        McaCompat.addHearts(player, villager, effectiveAmount());
    }

    public int effectiveAmount() {
        int scaled = Math.round(amount * McaQuestsConfig.COMMON.heartsRewardMultiplier.get().floatValue());
        return Mth.clamp(scaled,
                McaQuestsConfig.COMMON.minHeartsReward.get(),
                McaQuestsConfig.COMMON.maxHeartsReward.get());
    }
}
