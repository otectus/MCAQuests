package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.otectus.mcaquests.compat.TownsteadPeriod;

import java.util.Locale;
import java.util.Optional;

/**
 * How often a quest can be repeated (spec section 11; period rules from Life of the Town §5.6).
 *
 * <p>{@code once}, {@code cooldown} and {@code repeatable} are unchanged and decode exactly as they
 * always did. {@code period} is the addition: it ties repetition to the loaded Townstead calendar, so
 * "once a season" means once a season on this server rather than once per a tick count guessed at
 * authoring time.
 */
public record RepeatRule(RepeatType type, int cooldownTicks, Optional<TownsteadPeriod> period,
                         RepeatScope scope, int fallbackCooldownTicks) {

    public enum RepeatType {
        ONCE,
        COOLDOWN,
        REPEATABLE,
        /** Once per Townstead calendar period; see {@link TownsteadPeriod}. */
        PERIOD;

        public static final Codec<RepeatType> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown repeat type: " + name);
                    }
                },
                type -> DataResult.success(type.name().toLowerCase(Locale.ROOT)));
    }

    /** Whether a period is counted once for the world or once per giver, matching cooldown scoping. */
    public enum RepeatScope {
        GLOBAL,
        GIVER;

        public static final Codec<RepeatScope> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown repeat scope '" + name
                                + "'; expected global or giver");
                    }
                },
                scope -> DataResult.success(scope.name().toLowerCase(Locale.ROOT)));
    }

    public static final RepeatRule DEFAULT = new RepeatRule(RepeatType.COOLDOWN, 24000);

    /** The pre-1.4.1 shape, for the three rules that take none of the period fields. */
    public RepeatRule(RepeatType type, int cooldownTicks) {
        this(type, cooldownTicks, Optional.empty(), RepeatScope.GIVER, 24000);
    }

    public static final Codec<RepeatRule> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RepeatType.CODEC.optionalFieldOf("type", RepeatType.COOLDOWN).forGetter(RepeatRule::type),
            Codec.INT.optionalFieldOf("cooldown_ticks", 24000).forGetter(RepeatRule::cooldownTicks),
            TownsteadPeriod.CODEC.optionalFieldOf("period").forGetter(RepeatRule::period),
            RepeatScope.CODEC.optionalFieldOf("scope", RepeatScope.GIVER).forGetter(RepeatRule::scope),
            Codec.INT.optionalFieldOf("fallback_cooldown_ticks", 24000)
                    .forGetter(RepeatRule::fallbackCooldownTicks)
    ).apply(instance, RepeatRule::new));

    /**
     * Validated at parse time: a {@code period} rule without a period would silently behave as an
     * ordinary cooldown, which is exactly the sort of near-miss a seasonal quest would hide for a whole
     * in-game year before anyone noticed.
     */
    public static final Codec<RepeatRule> CODEC = BASE_CODEC.flatXmap(RepeatRule::validate, DataResult::success);

    private static DataResult<RepeatRule> validate(RepeatRule rule) {
        if (rule.type == RepeatType.PERIOD && rule.period.isEmpty()) {
            return DataResult.error(() -> "repeat type 'period' needs a 'period' of "
                    + "townstead_week, season or year");
        }
        if (rule.type != RepeatType.PERIOD && rule.period.isPresent()) {
            return DataResult.error(() -> "repeat 'period' is only meaningful with type 'period'");
        }
        if (rule.fallbackCooldownTicks < 0) {
            return DataResult.error(() -> "repeat 'fallback_cooldown_ticks' must not be negative");
        }
        return DataResult.success(rule);
    }

    public boolean isRepeatable() {
        return type != RepeatType.ONCE;
    }

    /** True when eligibility is decided by a calendar token rather than by a tick deadline. */
    public boolean isPeriodic() {
        return type == RepeatType.PERIOD && period.isPresent();
    }
}
