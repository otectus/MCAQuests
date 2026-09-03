package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Restores (or reduces) one of a villager Townstead need (Townstead spec §5.5).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_needs",
 *   "target": "giver",
 *   "need": "energy",
 *   "amount": 6
 * }
 * }</pre>
 *
 * <p>{@code amount} is a change by default and an absolute value with {@code "mode": "target"}. Values
 * are clamped to whatever range Townstead keeps the need in, and the result reports what actually
 * landed rather than what was asked for -- the ranges differ per need, so "restore 50" means very
 * different things for hunger and for thirst.
 *
 * <p>Thirst is refused outright when Townstead has it gated behind a thirst mod that is not installed,
 * because writing a number into a need nothing simulates would read back unchanged and look broken.
 */
public record TownsteadNeedsReward(TownsteadTarget target, NeedMutation.Need need,
                                   NeedMutation.Mode mode, double amount) implements TownsteadReward {

    private static final Map<String, NeedMutation.Need> NEEDS = Arrays.stream(NeedMutation.Need.values())
            .collect(Collectors.toUnmodifiableMap(n -> n.name().toLowerCase(Locale.ROOT), Function.identity()));

    private static final Codec<NeedMutation.Need> NEED_CODEC = Codec.STRING.flatXmap(
            raw -> {
                NeedMutation.Need need = NEEDS.get(raw.toLowerCase(Locale.ROOT));
                return need != null ? DataResult.success(need) : DataResult.error(
                        () -> "Unknown Townstead need " + raw + "; expected one of " + NEEDS.keySet());
            },
            need -> DataResult.success(need.name().toLowerCase(Locale.ROOT)));

    private static final Codec<NeedMutation.Mode> MODE_CODEC = Codec.STRING.flatXmap(
            raw -> switch (raw.toLowerCase(Locale.ROOT)) {
                case "delta" -> DataResult.success(NeedMutation.Mode.DELTA);
                case "target" -> DataResult.success(NeedMutation.Mode.TARGET);
                default -> DataResult.error(() -> "Unknown Townstead need mode " + raw
                        + "; expected delta or target");
            },
            mode -> DataResult.success(mode.name().toLowerCase(Locale.ROOT)));

    public static final MapCodec<TownsteadNeedsReward> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadNeedsReward::target),
                    NEED_CODEC.fieldOf("need").forGetter(TownsteadNeedsReward::need),
                    StrictCodecs.strictOptional(MODE_CODEC, "mode", NeedMutation.Mode.DELTA)
                            .forGetter(TownsteadNeedsReward::mode),
                    Codec.DOUBLE.fieldOf("amount").forGetter(TownsteadNeedsReward::amount)
            ).apply(instance, TownsteadNeedsReward::new));

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.TOWNSTEAD_NEEDS;
    }

    @Override
    public TownsteadCapability capability() {
        return TownsteadCapability.MUTATE_NEEDS;
    }

    @Override
    public boolean enabledByConfig() {
        return McaQuestsConfig.COMMON.townsteadNeedRewardsEnabled.get();
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
                .changeNeeds(subject, new NeedMutation(need, mode, amount));
        if (!result.succeeded()) {
            reportFailure(result);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.townstead_needs",
                (int) Math.round(amount),
                Component.translatableWithFallback(
                        "mcaquests.townstead.need." + need.name().toLowerCase(Locale.ROOT),
                        need.name().toLowerCase(Locale.ROOT)));
    }
}
