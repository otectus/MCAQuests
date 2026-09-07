package dev.otectus.mcaquests.quest.template;

import com.mojang.serialization.Codec;
import dev.otectus.mcaquests.data.StrictCodecs;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

/**
 * A template variable that resolves to a single integer. A base value is drawn uniformly from
 * {@code [min, max]} with the offer's {@link QuestContext#stableRandom stable RNG} (so the same offer
 * yields the same number within a pass), then optional <em>contextual</em> bonuses are added:
 * {@code per_player_level} × the player's XP level and {@code per_heart} × the giver's hearts. The
 * total is clamped to {@code limit} when present. This lets a template scale counts by player level or
 * scale a reward by relationship level without any hardcoded quest logic (spec: contextual selection).
 */
public record IntVariable(int min, int max, double perPlayerLevel, double perHeart, OptionalInt limit)
        implements TemplateVariable {

    public static final Codec<IntVariable> CODEC = RecordCodecBuilder.<IntVariable>create(instance -> instance.group(
            Codec.INT.fieldOf("min").forGetter(IntVariable::min),
            Codec.INT.fieldOf("max").forGetter(IntVariable::max),
            StrictCodecs.strictOptional(Codec.DOUBLE, "per_player_level", 0.0).forGetter(IntVariable::perPlayerLevel),
            StrictCodecs.strictOptional(Codec.DOUBLE, "per_heart", 0.0).forGetter(IntVariable::perHeart),
            StrictCodecs.strictOptional(Codec.INT, "limit").forGetter(v -> v.limit.isPresent() ? Optional.of(v.limit.getAsInt()) : Optional.empty())
    ).apply(instance, (min, max, perLevel, perHeart, limit) ->
            new IntVariable(min, max, perLevel, perHeart, limit.map(OptionalInt::of).orElse(OptionalInt.empty()))))
            .flatXmap(IntVariable::validate, IntVariable::validate);

    private static DataResult<IntVariable> validate(IntVariable var) {
        if (!Double.isFinite(var.perPlayerLevel) || !Double.isFinite(var.perHeart)) {
            return DataResult.error(() -> "int variable scaling coefficients must be finite");
        }
        if (var.max < var.min) {
            return DataResult.error(() -> "int variable 'max' (" + var.max + ") must be >= 'min' (" + var.min + ")");
        }
        if (var.limit.isPresent() && var.limit.getAsInt() < var.min) {
            return DataResult.error(() -> "int variable 'limit' (" + var.limit.getAsInt()
                    + ") must be >= 'min' (" + var.min + ")");
        }
        return DataResult.success(var);
    }

    @Override
    public String kindKey() {
        return "int";
    }

    @Override
    public Optional<ResolvedValue> representative() {
        return Optional.of(new ResolvedValue.IntValue(min));
    }

    @Override
    public Optional<ResolvedValue> resolve(String name, QuestContext context) {
        int value = resolveAmount(context.stableRandom("var:" + name),
                perPlayerLevel == 0.0 ? 0 : context.player().experienceLevel,
                perHeart == 0.0 ? 0 : context.hearts());
        return Optional.of(new ResolvedValue.IntValue(value));
    }

    int resolveAmount(java.util.Random random, int level, int hearts) {
        long width = (long) max - min + 1L;
        long rolled = max <= min ? min : min + (width <= Integer.MAX_VALUE
                ? random.nextInt((int) width) : random.nextLong(width));
        double base = rolled;
        if (perPlayerLevel != 0.0) {
            base += Math.floor(perPlayerLevel * level);
        }
        if (perHeart != 0.0) {
            base += Math.floor(perHeart * hearts);
        }
        // Finite authored coefficients can still overflow their products. Use exact decimal
        // arithmetic only for that exceptional range, including opposite terms that cancel.
        if (!Double.isFinite(base)) {
            if (!Double.isFinite(perPlayerLevel) || !Double.isFinite(perHeart)) return min;
            var scaled = java.math.BigDecimal.valueOf(rolled)
                    .add(java.math.BigDecimal.valueOf(perPlayerLevel).multiply(java.math.BigDecimal.valueOf(level))
                            .setScale(0, java.math.RoundingMode.FLOOR))
                    .add(java.math.BigDecimal.valueOf(perHeart).multiply(java.math.BigDecimal.valueOf(hearts))
                            .setScale(0, java.math.RoundingMode.FLOOR));
            return scaled.max(java.math.BigDecimal.valueOf(min))
                    .min(java.math.BigDecimal.valueOf(limit.orElse(Integer.MAX_VALUE))).intValue();
        }
        double value = Math.max(min, base);
        if (limit.isPresent()) {
            value = Math.min(value, limit.getAsInt());
        }
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
