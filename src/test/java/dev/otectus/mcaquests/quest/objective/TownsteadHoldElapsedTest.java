package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a Townstead hold objective may credit for one poll.
 *
 * <p>Both hold objectives used to add a flat twenty ticks per poll while the polls themselves run every
 * {@code townsteadPollIntervalTicks} (10–1200). At the top of that range a {@code hold_ticks: 600}
 * objective wanted thirty real minutes, and {@code reset_on_false} made it near-unfinishable. Time is now
 * measured, exactly as {@code schedule_streak} already measured it.
 */
class TownsteadHoldElapsedTest {

    private static final int INTERVAL = 1200;

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("the first poll credits one interval, so a hold starts moving immediately")
    void firstPollCreditsAnInterval() {
        ObjectiveProgress progress = new ObjectiveProgress();

        assertEquals(INTERVAL, TownsteadObjectives.elapsedSincePoll(progress, 5000L, INTERVAL));
    }

    @Test
    @DisplayName("later polls credit the real gap since the previous one")
    void laterPollsCreditTheRealGap() {
        ObjectiveProgress progress = new ObjectiveProgress();
        TownsteadObjectives.elapsedSincePoll(progress, 5000L, INTERVAL);

        assertEquals(INTERVAL, TownsteadObjectives.elapsedSincePoll(progress, 5000L + INTERVAL, INTERVAL));
        assertEquals(300L, TownsteadObjectives.elapsedSincePoll(progress, 5300L + INTERVAL, INTERVAL));
    }

    @Test
    @DisplayName("a long gap is capped at four intervals, so a lagging server cannot hand over a hold")
    void aLongGapIsCapped() {
        ObjectiveProgress progress = new ObjectiveProgress();
        TownsteadObjectives.elapsedSincePoll(progress, 0L, INTERVAL);

        assertEquals(INTERVAL * 4L, TownsteadObjectives.elapsedSincePoll(progress, 100000L, INTERVAL));
    }

    @Test
    @DisplayName("a backwards clock credits nothing rather than subtracting")
    void aBackwardsClockCreditsNothing() {
        ObjectiveProgress progress = new ObjectiveProgress();
        TownsteadObjectives.elapsedSincePoll(progress, 10000L, INTERVAL);

        assertEquals(0L, TownsteadObjectives.elapsedSincePoll(progress, 500L, INTERVAL));
        // ...and the clock is re-stamped, so the world it woke up in is the one it measures from.
        assertEquals(200L, TownsteadObjectives.elapsedSincePoll(progress, 700L, INTERVAL));
    }
}
