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
public record RepeatRule(RepeatType type, Optional<Integer> declaredCooldownTicks,
                         Optional<TownsteadPeriod> period,
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

    /**
     * The rule a quest gets when it declares none: a cooldown of whatever
     * {@code defaultQuestCooldownTicks} says.
     */
    public static final RepeatRule DEFAULT =
            new RepeatRule(RepeatType.COOLDOWN, Optional.empty(), Optional.empty(), RepeatScope.GIVER, 24000);

    /** The pre-1.4.1 shape, for the three rules that take none of the period fields. */
    public RepeatRule(RepeatType type, int cooldownTicks) {
        this(type, Optional.of(cooldownTicks), Optional.empty(), RepeatScope.GIVER, 24000);
    }

    /**
     * How long this quest waits before it can be taken again from the same villager.
     *
     * <p>Falls back to {@code defaultQuestCooldownTicks} when the JSON did not say. That key was declared
     * and documented from the first release and read absolutely nowhere: the effective default came from a
     * hardcoded {@code 24000} in this codec, so a server owner who set it to a week got a day. Telling the
     * declared value apart from an omitted one is why this is an {@link Optional} rather than an
     * {@code int} — you cannot tell "the author wrote 24000" from "the author wrote nothing" otherwise.
     */
    public int cooldownTicks() {
        return declaredCooldownTicks.orElseGet(RepeatRule::configuredDefaultCooldown);
    }

    /**
     * Reads the configured default, falling back to the historical 24000 when no config is attached.
     *
     * <p>Forge throws rather than answering before a config file is bound, which is right for a running
     * game and wrong for a unit test constructing a quest. The literal is the value this codec used to
     * hardcode, so an unconfigured read behaves exactly as the mod always did.
     */
    private static int configuredDefaultCooldown() {
        try {
            return dev.otectus.mcaquests.McaQuestsConfig.COMMON.defaultQuestCooldownTicks.get();
        } catch (RuntimeException e) {
            return 24000;
        }
    }

    public static final Codec<RepeatRule> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RepeatType.CODEC.optionalFieldOf("type", RepeatType.COOLDOWN).forGetter(RepeatRule::type),
            Codec.INT.optionalFieldOf("cooldown_ticks").forGetter(RepeatRule::declaredCooldownTicks),
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
