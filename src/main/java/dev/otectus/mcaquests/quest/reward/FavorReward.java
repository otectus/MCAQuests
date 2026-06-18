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
 * Adds MCA relationship hearts ("favor") with the quest giver (spec section 15). The configured
 * multiplier and min/max clamp are applied here so favor stays balanced. Must run last, after a
 * successful reward delivery (the caller guarantees ordering).
 */
public record FavorReward(int amount) implements QuestReward {

    public static final Codec<FavorReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("amount").forGetter(FavorReward::amount)
    ).apply(instance, FavorReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.FAVOR;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.favor", effectiveAmount());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (villager == null) {
            return; // favor needs a giver; nothing to do if it has gone missing
        }
        McaCompat.addFavor(player, villager, effectiveAmount());
    }

    /** Amount after the configured multiplier and min/max clamp. */
    public int effectiveAmount() {
        int scaled = Math.round(amount * McaQuestsConfig.COMMON.favorRewardMultiplier.get().floatValue());
        return Mth.clamp(scaled,
                McaQuestsConfig.COMMON.minFavorReward.get(),
                McaQuestsConfig.COMMON.maxFavorReward.get());
    }
}
