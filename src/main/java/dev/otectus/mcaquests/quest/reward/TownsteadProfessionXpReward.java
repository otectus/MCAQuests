package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Awards Townstead profession experience (Townstead spec §5.5).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_profession_xp",
 *   "target": "giver",
 *   "profession": "minecraft:farmer",
 *   "amount": 35,
 *   "respect_daily_cap": true
 * }
 * }</pre>
 *
 * <p><b>Bypassing the daily cap takes two keys.</b> The pack must ask for it <em>and</em> the server
 * must allow it through {@code compat.townstead.allowUncappedProfessionXp}. A repeatable quest with an
 * uncapped award would let a player outrun the progression pacing Townstead deliberately sets, and a
 * datapack alone should not be able to decide that for someone else's server.
 */
public record TownsteadProfessionXpReward(TownsteadTarget target, String profession, int amount,
                                          boolean respectDailyCap) implements TownsteadReward {

    public static final Codec<TownsteadProfessionXpReward> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadProfessionXpReward::target),
                    Codec.STRING.fieldOf("profession").forGetter(TownsteadProfessionXpReward::profession),
                    ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TownsteadProfessionXpReward::amount),
                    StrictCodecs.strictOptional(Codec.BOOL, "respect_daily_cap", true)
                            .forGetter(TownsteadProfessionXpReward::respectDailyCap)
            ).apply(instance, TownsteadProfessionXpReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.TOWNSTEAD_PROFESSION_XP;
    }

    @Override
    public TownsteadCapability capability() {
        return TownsteadCapability.AWARD_PROFESSION_XP;
    }

    @Override
    public boolean enabledByConfig() {
        return McaQuestsConfig.COMMON.townsteadProfessionXpRewardsEnabled.get();
    }

    /** The cap applies unless the pack asks to skip it and the server permits it. */
    private boolean effectiveRespectCap() {
        return respectDailyCap || !McaQuestsConfig.COMMON.townsteadAllowUncappedProfessionXp.get();
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (!enabledByConfig()) {
            return;
        }
        Entity subject = resolveTarget(player, villager).orElse(null);
        if (subject == null) {
            return;
        }
        TownsteadMutationResult result = TownsteadBridge.Holder.get()
                .awardProfessionXp(subject, profession, amount, effectiveRespectCap());
        if (!result.succeeded()) {
            reportFailure(result);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.townstead_profession_xp", amount, profession);
    }
}
