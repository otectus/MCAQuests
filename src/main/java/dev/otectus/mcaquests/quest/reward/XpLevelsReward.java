package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Gives experience levels (spec section 15). */
public record XpLevelsReward(int levels) implements QuestReward {

    public static final Codec<XpLevelsReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("levels").forGetter(XpLevelsReward::levels)
    ).apply(instance, XpLevelsReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.XP_LEVELS;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.xp_levels", levels);
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        player.giveExperienceLevels(levels);
    }
}
