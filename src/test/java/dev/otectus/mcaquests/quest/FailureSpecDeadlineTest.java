package dev.otectus.mcaquests.quest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code deadline_time} is documented as a world time-of-day — "before sunrise" — and was computed in
 * game time, which sleeping and {@code /time set} do not advance. A player who slept a night through
 * saw {@code guard/dawn_defense} expire at an arbitrary hour, with a countdown that agreed with the
 * wrong answer. The value is still an absolute game time (that is what the deadline is compared against
 * and what the client is sent); only the clock it is measured on changed.
 */
class FailureSpecDeadlineTest {

    private static FailureSpec timeOfDay(int deadlineTimeOfDay) {
        return new FailureSpec(Optional.empty(), Optional.of(deadlineTimeOfDay), Optional.empty(),
                false, 0, Optional.empty(), false);
    }

    private static FailureSpec afterTicks(int ticks) {
        return new FailureSpec(Optional.of(ticks), Optional.empty(), Optional.empty(),
                false, 0, Optional.empty(), false);
    }

    @Test
    @DisplayName("a time-of-day deadline is the remaining world-clock ticks, converted to game time")
    void timeOfDayCountsDownOnTheWorldClock() {
        // Accepted at day time 6000 (midday), due at 23000 (just before sunrise): 17000 ticks of clock.
        OptionalLong startDay = OptionalLong.of(6000L);

        assertEquals(OptionalLong.of(101000L),
                timeOfDay(23000).deadlineGameTime(0L, startDay, 100000L, 22000L),
                "1000 ticks of world clock left is 1000 ticks of game time from now");
        assertEquals(OptionalLong.of(100000L),
                timeOfDay(23000).deadlineGameTime(0L, startDay, 100000L, 23000L),
                "the deadline has arrived the moment the world clock reaches it");
    }

    @Test
    @DisplayName("sleeping through the night fires the deadline instead of moving it")
    void sleepingFiresTheDeadline() {
        OptionalLong startDay = OptionalLong.of(6000L);
        // One night's sleep: ten ticks of game time, the world clock jumped past the deadline.
        OptionalLong deadline = timeOfDay(23000).deadlineGameTime(0L, startDay, 100010L, 23000L);
        assertTrue(deadline.isPresent() && deadline.getAsLong() <= 100010L,
                "a slept-through deadline must have fired, not been pushed a day out");
    }

    @Test
    @DisplayName("deadline_ticks is elapsed time and is unaffected by the world clock")
    void elapsedDeadlineIsUnchanged() {
        assertEquals(OptionalLong.of(500L),
                afterTicks(500).deadlineGameTime(0L, OptionalLong.of(6000L), 100000L, 22000L));
        assertEquals(OptionalLong.of(500L), afterTicks(500).deadlineGameTime(0L));
    }

    @Test
    @DisplayName("a quest accepted before 1.5.1 keeps its game-time deadline")
    void absentStartDayReproducesTheOldAnswer() {
        FailureSpec spec = timeOfDay(23000);
        assertEquals(spec.deadlineGameTime(6000L),
                spec.deadlineGameTime(6000L, OptionalLong.empty(), 100000L, 22000L),
                "with no frozen world clock the deadline is the one the quest was given");
    }
}
