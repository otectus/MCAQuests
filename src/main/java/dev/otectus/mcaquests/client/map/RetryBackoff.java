package dev.otectus.mcaquests.client.map;

/**
 * When to try a failing backend again: 1, 2, 4, 8, 16, 32, then 60 seconds for as long as it keeps
 * failing.
 *
 * <p>Both halves of that matter. Something has to retry at all, because the old code treated a failed
 * publish as a success and never came back — a waypoint that did not appear never appeared. And the
 * retry has to slow down, because the alternative is a reflective call and a stack trace twenty times
 * a second for as long as a player has a mod version this build does not understand.
 *
 * <p>Pure and per-backend: JourneyMap failing must not slow Xaero down, and time arrives as a
 * parameter so the schedule can be tested without sleeping.
 */
public final class RetryBackoff {

    /** Doubling, then held at a minute. Seven steps is a little over two minutes to reach the cap. */
    private static final long[] DELAYS_MILLIS = {1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L};

    private int consecutiveFailures;
    private long nextAttemptAtMillis;

    /** Whether a backend that failed last time may be called again. Always true before a failure. */
    public boolean isDue(long nowMillis) {
        return consecutiveFailures == 0 || nowMillis >= nextAttemptAtMillis;
    }

    /** Records a failed pass and schedules the next attempt. */
    public void recordFailure(long nowMillis) {
        long delay = DELAYS_MILLIS[Math.min(consecutiveFailures, DELAYS_MILLIS.length - 1)];
        consecutiveFailures++;
        nextAttemptAtMillis = nowMillis + delay;
    }

    /** Back to trying every pass. */
    public void recordSuccess() {
        reset();
    }

    /** As {@link #recordSuccess()}, for a world epoch: the old failure was about a world that is gone. */
    public void reset() {
        consecutiveFailures = 0;
        nextAttemptAtMillis = 0L;
    }

    /** When the next attempt is allowed, or 0 when one is allowed now. */
    public long nextAttemptAtMillis() {
        return consecutiveFailures == 0 ? 0L : nextAttemptAtMillis;
    }

    /** How many passes in a row have failed, for diagnostics. */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }
}
