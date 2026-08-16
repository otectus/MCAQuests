package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** Applies a status effect (spec section 15). Duration is in ticks. */
public record EffectReward(MobEffect effect, int duration, int amplifier) implements QuestReward {

    public static final Codec<EffectReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("effect").forGetter(EffectReward::effect),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("duration", 600).forGetter(EffectReward::duration),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectReward::amplifier)
    ).apply(instance, EffectReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.EFFECT;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.effect", effect.getDisplayName());
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }
}
