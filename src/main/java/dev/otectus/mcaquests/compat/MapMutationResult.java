package dev.otectus.mcaquests.compat;

/**
 * What one attempt to change one waypoint actually did.
 *
 * <p>Replaces a {@code void} that could not fail. Both integrations can decline a waypoint without
 * throwing, and the old code recorded its intent as its outcome - so a waypoint that never appeared
 * looked, from the next tick onwards, exactly like one that had. Every state that is worth retrying is
 * separated here from every state that is not.
 */
public enum MapMutationResult {
    /** The backend changed something and read it back, or at least completed without throwing. */
    APPLIED,
    /** The backend already holds exactly this spec. No call was made. */
    UNCHANGED,
    /** Nothing is wrong; the map is not ready yet. The reconciler will come back. */
    RETRY_LATER,
    /** This backend can only draw the dimension the player is in, and this spec is elsewhere. */
    SKIPPED_OTHER_DIMENSION,
    /** The backend does not do this at all - a persistent pin on a session-only store. */
    UNSUPPORTED,
    /** A call threw. Retried with backoff, and reported to diagnostics. */
    FAILED
}
