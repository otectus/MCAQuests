package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Gives raw experience points (spec section 15). */
public record XpReward(int amount) implements QuestReward {

    public static final Codec<XpReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(XpReward::amount)
    ).apply(instance, XpReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.XP;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.xp", effectiveAmount());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        player.giveExperiencePoints(effectiveAmount());
    }

    /** Amount after {@code xpRewardMultiplier}. Shown and granted through the same value, so the card never lies. */
    public int effectiveAmount() {
        return Math.max(0, (int) Math.round(amount * McaQuestsConfig.COMMON.xpRewardMultiplier.get()));
    }
}
