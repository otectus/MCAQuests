package dev.otectus.mcaquests.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A stretch of the Townstead calendar a quest may repeat once per (spec §5.6).
 *
 * <p>The point of this is that "once a season" must mean once a season <em>on this server</em>. A
 * fixed tick cooldown cannot: Townstead calendar profiles are datapack-defined, so a season may be
 * three days or thirty, and a hard-coded 96,000 ticks would be a week on one world and a year on
 * another. So a completion records a <b>token</b> naming the period it happened in, and the quest is
 * eligible again exactly when the live token differs.
 *
 * <p>The token includes the calendar profile id, so switching profiles mid-world does not silently
 * collide "spring of year 3" under two different definitions of spring. Nothing here assumes four
 * seasons, seven-day weeks or a fixed year length — every component is read from the live calendar.
 */
public enum TownsteadPeriod {

    /**
     * One turn of the calendar's own week. Uses the day-of-week to find which week the current
     * day-of-year falls in, so a profile with a five-day week works without special-casing.
     */
    TOWNSTEAD_WEEK("townstead_week"),
    SEASON("season"),
    YEAR("year");

    private static final Map<String, TownsteadPeriod> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TownsteadPeriod::id, Function.identity()));

    /**
     * The same three periods spelled as a situation trigger writes them: a season <em>transition</em>
     * is written {@code "season"}, not {@code "townstead_week"}-style, because a trigger names an axis
     * rather than a repeat rule. {@code week} is accepted alongside {@code townstead_week} so a pack
     * author does not have to remember which surface they are on.
     */
    public static final Codec<TownsteadPeriod> TRANSITION_CODEC = Codec.STRING.flatXmap(
            raw -> {
                String normalised = raw.toLowerCase(Locale.ROOT);
                TownsteadPeriod period = BY_NAME.get(normalised.equals("week") ? "townstead_week" : normalised);
                return period != null ? DataResult.success(period) : DataResult.error(
                        () -> "Unknown calendar transition '" + raw + "'; expected week, season or year");
            },
            period -> DataResult.success(period == TOWNSTEAD_WEEK ? "week" : period.id()));

    public static final Codec<TownsteadPeriod> CODEC = Codec.STRING.flatXmap(
            raw -> {
                TownsteadPeriod period = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                return period != null ? DataResult.success(period) : DataResult.error(
                        () -> "Unknown repeat period '" + raw + "'; expected one of " + BY_NAME.keySet());
            },
            period -> DataResult.success(period.id()));

    private final String id;

    TownsteadPeriod(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * The value this period currently reads as, for a transition signal: the season name, the week
     * marker, or the year. Empty when the calendar cannot answer -- which the detector treats as
     * "do not observe" rather than as a change.
     */
    public String currentValue(TownsteadCalendarView calendar) {
        if (calendar == null) {
            return "";
        }
        return switch (this) {
            case YEAR -> String.valueOf(calendar.year());
            case SEASON -> calendar.season();
            case TOWNSTEAD_WEEK -> String.valueOf(weekIndex(calendar));
        };
    }

    /**
     * The token naming the period this calendar reading falls in, in the form
     * {@code profile/period/year/index}.
     *
     * <p>Empty when the calendar cannot be read. <b>That is never treated as a new period</b>: an
     * unreadable calendar must not hand out a second reward, so the caller falls back to a plain tick
     * cooldown for an offer and suspends anything already accepted.
     */
    public Optional<String> token(TownsteadCalendarView calendar) {
        if (calendar == null || calendar.profileId().isEmpty()) {
            return Optional.empty();
        }
        String index = switch (this) {
            case YEAR -> "";
            case SEASON -> calendar.season();
            case TOWNSTEAD_WEEK -> String.valueOf(weekIndex(calendar));
        };
        if (this == SEASON && index.isEmpty()) {
            return Optional.empty(); // a profile with no seasons cannot answer a per-season question
        }
        return Optional.of(calendar.profileId() + '/' + id + '/' + calendar.year() + '/' + index);
    }

    /**
     * Which week of the year this day falls in, derived from the day-of-year and the calendar's own
     * day-of-week rather than from an assumed week length.
     *
     * <p>{@code dayOfYear - dayOfWeek} is the day the current week began, so every day of one week maps
     * to the same value whatever that week's length is, and the week boundary is wherever the loaded
     * profile puts it. Both fields are 1-based in Townstead's snapshot, so the subtraction leaves a
     * stable non-negative marker rather than a true week ordinal — which is all a token needs.
     */
    private static int weekIndex(TownsteadCalendarView calendar) {
        return Math.max(0, calendar.dayOfYear() - Math.max(0, calendar.dayOfWeek()));
    }
}
