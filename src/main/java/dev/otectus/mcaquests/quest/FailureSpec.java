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
                          boolean blockRetry) {

    private static final long DAY_LENGTH = 24000L;

    public static final Codec<FailureSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("deadline_ticks").forGetter(FailureSpec::deadlineTicks),
            Codec.intRange(0, (int) DAY_LENGTH).optionalFieldOf("deadline_time").forGetter(FailureSpec::deadlineTimeOfDay),
            WeatherCondition.Weather.CODEC.optionalFieldOf("require_weather").forGetter(FailureSpec::requireWeather),
            Codec.BOOL.optionalFieldOf("fail_on_giver_death", false).forGetter(FailureSpec::failOnGiverDeath),
            Codec.INT.optionalFieldOf("failure_hearts", 0).forGetter(FailureSpec::failureHearts),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("retry_after").forGetter(FailureSpec::retryAfterTicks),
            Codec.BOOL.optionalFieldOf("block_retry", false).forGetter(FailureSpec::blockRetry)
    ).apply(instance, FailureSpec::new));

    /** Whether this spec declares at least one trigger; a spec with none can never fire (a datapack error). */
    public boolean hasTrigger() {
        return deadlineTicks.isPresent() || deadlineTimeOfDay.isPresent()
                || requireWeather.isPresent() || failOnGiverDeath;
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

    /** The failure reason reported for a fired time deadline ({@code TIME_WINDOW} if any time-of-day is set). */
    public QuestFailedEvent.Reason timeDeadlineReason() {
        return deadlineTimeOfDay.isPresent() ? QuestFailedEvent.Reason.TIME_WINDOW
                : QuestFailedEvent.Reason.TIME_LIMIT;
    }
}
