package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.event.QuestFailedEvent;
import dev.otectus.mcaquests.quest.condition.leaf.WeatherCondition;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Optional failure-state metadata on a {@link QuestDefinition}. A quest with no {@code failure} block
 * can never fail (it lives until completed or abandoned); this adds author-controlled ways for a quest
 * to expire, plus the outcome applied when it does.
 *
 * <p><b>Triggers</b> (any may be combined; the first to fire wins):
 * <ul>
 *   <li>{@code deadline_ticks} — fail this many ticks after acceptance.</li>
 *   <li>{@code deadline_time} — fail when the world clock next reaches this time-of-day (0..24000)
 *       after acceptance, e.g. {@code 23000} for "before sunrise".</li>
 *   <li>{@code require_weather} — the quest demands a weather (clear / rain / thunder); it fails the
 *       moment the weather stops matching, e.g. {@code "rain"} for "while it's raining".</li>
 *   <li>{@code fail_on_giver_death} — fail if the giver dies, independent of the global
 *       {@code failQuestIfGiverDies} config.</li>
 * </ul>
 *
 * <p><b>Outcome</b>:
 * <ul>
 *   <li>{@code failure_hearts} — hearts delta applied to the giver on failure (negative = penalty).</li>
 *   <li>{@code retry_after} — cooldown (ticks) before the quest can be offered again.</li>
 *   <li>{@code block_retry} — if true, the quest is locked permanently after a failure.</li>
 * </ul>
 *
 * <p>The failure dialogue line is the quest's {@code dialogue.failed} entry (see
 * {@link QuestDefinition#FAILED}). A recovery quest is expressed with the existing
 * {@code mcaquests:quest_failed} condition — no field here.
 */
public record FailureSpec(Optional<Integer> deadlineTicks,
                          Optional<Integer> deadlineTimeOfDay,
                          Optional<WeatherCondition.Weather> requireWeather,
                          boolean failOnGiverDeath,
                          int failureHearts,
                          Optional<Integer> retryAfterTicks,
                          boolean blockRetry,
                          boolean failOnTargetLost) {

    /** The pre-{@code fail_on_target_lost} shape, so code that builds a spec directly keeps compiling. */
    public FailureSpec(Optional<Integer> deadlineTicks, Optional<Integer> deadlineTimeOfDay,
                       Optional<WeatherCondition.Weather> requireWeather, boolean failOnGiverDeath,
                       int failureHearts, Optional<Integer> retryAfterTicks, boolean blockRetry) {
        this(deadlineTicks, deadlineTimeOfDay, requireWeather, failOnGiverDeath, failureHearts,
                retryAfterTicks, blockRetry, false);
    }

    private static final long DAY_LENGTH = 24000L;

    public static final Codec<FailureSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("deadline_ticks").forGetter(FailureSpec::deadlineTicks),
            Codec.intRange(0, (int) DAY_LENGTH).optionalFieldOf("deadline_time").forGetter(FailureSpec::deadlineTimeOfDay),
            WeatherCondition.Weather.CODEC.optionalFieldOf("require_weather").forGetter(FailureSpec::requireWeather),
            Codec.BOOL.optionalFieldOf("fail_on_giver_death", false).forGetter(FailureSpec::failOnGiverDeath),
            Codec.INT.optionalFieldOf("failure_hearts", 0).forGetter(FailureSpec::failureHearts),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("retry_after").forGetter(FailureSpec::retryAfterTicks),
            Codec.BOOL.optionalFieldOf("block_retry", false).forGetter(FailureSpec::blockRetry),
            // Default false: 1.4.0's contract is that a quest you cannot currently play is suspended, not
            // taken away. A pack that would rather close the story than leave it open says so here.
            Codec.BOOL.optionalFieldOf("fail_on_target_lost", false).forGetter(FailureSpec::failOnTargetLost)
    ).apply(instance, FailureSpec::new));

    /** Whether this spec declares at least one trigger; a spec with none can never fire (a datapack error). */
    public boolean hasTrigger() {
        return deadlineTicks.isPresent() || deadlineTimeOfDay.isPresent()
                || requireWeather.isPresent() || failOnGiverDeath || failOnTargetLost;
    }

    /** Whether a time-based deadline ({@code deadline_ticks} or {@code deadline_time}) is configured. */
    public boolean hasTimeDeadline() {
        return deadlineTicks.isPresent() || deadlineTimeOfDay.isPresent();
    }

    /**
     * The absolute game-time at which this quest expires, given the game-time it was accepted, or empty
     * when no time deadline is configured. When both {@code deadline_ticks} and {@code deadline_time}
     * are set, the earlier of the two wins (whichever runs out first). The {@code deadline_time}
     * variant resolves to the next occurrence of that time-of-day at or after acceptance; landing
     * exactly on the boundary grants a full day rather than expiring instantly.
     */
    public OptionalLong deadlineGameTime(long startGameTime) {
        long target = Long.MAX_VALUE;
        boolean any = false;
        if (deadlineTicks.isPresent()) {
            target = Math.min(target, startGameTime + deadlineTicks.get());
            any = true;
        }
        if (deadlineTimeOfDay.isPresent()) {
            long startTod = Math.floorMod(startGameTime, DAY_LENGTH);
            long delta = Math.floorMod(deadlineTimeOfDay.get() - startTod, DAY_LENGTH);
            if (delta == 0L) {
                delta = DAY_LENGTH; // accepted exactly at the boundary -> give a full day
            }
            target = Math.min(target, startGameTime + delta);
            any = true;
        }
        return any ? OptionalLong.of(target) : OptionalLong.empty();
    }

    /**
     * The absolute game-time this quest expires at, measuring {@code deadline_time} on the world clock.
     *
     * <p>{@code deadline_time} is a time of day, so it has to be read from the clock the player can see:
     * sleeping through a night and {@code /time set} both move the world clock without moving game time,
     * and computing "before sunrise" in game time made it land at an arbitrary hour afterwards. The
     * answer is still an absolute game time -- the deadline is compared against, and sent to the client
     * as, game time -- so this only converts the remaining day-ticks at the moment it is asked.
     *
     * <p>{@code startDayTime} is empty on a quest accepted before 1.5.1; that quest keeps the old
     * game-time behaviour rather than having its deadline retargeted while it is held.
     */
    public OptionalLong deadlineGameTime(long startGameTime, OptionalLong startDayTime,
                                         long nowGameTime, long nowDayTime) {
        long target = Long.MAX_VALUE;
        boolean any = false;
        if (deadlineTicks.isPresent()) {
            target = Math.min(target, startGameTime + deadlineTicks.get());
            any = true;
        }
        if (deadlineTimeOfDay.isPresent()) {
            long anchor = startDayTime.isPresent() ? startDayTime.getAsLong() : startGameTime;
            long deadline = startDayTime.isPresent()
                    ? nowGameTime + (anchor + timeOfDayDelta(anchor) - nowDayTime)
                    : startGameTime + timeOfDayDelta(anchor);
            target = Math.min(target, deadline);
            any = true;
        }
        return any ? OptionalLong.of(target) : OptionalLong.empty();
    }

    /** Ticks from {@code start} to the next occurrence of {@code deadline_time}; a full day on the boundary. */
    private long timeOfDayDelta(long start) {
        long startTod = Math.floorMod(start, DAY_LENGTH);
        long delta = Math.floorMod(deadlineTimeOfDay.get() - startTod, DAY_LENGTH);
        return delta == 0L ? DAY_LENGTH : delta; // accepted exactly at the boundary -> give a full day
    }

    /** The failure reason reported for a fired time deadline ({@code TIME_WINDOW} if any time-of-day is set). */
    public QuestFailedEvent.Reason timeDeadlineReason() {
        return deadlineTimeOfDay.isPresent() ? QuestFailedEvent.Reason.TIME_WINDOW
                : QuestFailedEvent.Reason.TIME_LIMIT;
    }
}
