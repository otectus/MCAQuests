package dev.otectus.mcaquests.compat;

/**
 * The outcome of one Townstead mutation (Townstead spec §3.5, §4.3, §4.4).
 *
 * <p>Every mutation on {@link TownsteadBridge} returns one of these instead of throwing, and every
 * one is produced by <em>re-reading</em> Townstead after the write rather than by trusting the
 * request: {@link #before()} and {@link #after()} are observed values, and {@link #applied()} is the
 * amount that actually landed once Townstead's own clamps and caps had their say. A reward that asks
 * for 120 XP against a 40-point daily remainder reports {@code requested=120, applied=40} and
 * {@link Reason#SUCCESS} — partial application is success, not failure.
 *
 * <p>{@link #oldTier()} and {@link #newTier()} are meaningful only for profession XP; they are equal
 * for every other mutation.
 */
public record TownsteadMutationResult(
        Reason reason,
        int requested,
        int applied,
        double before,
        double after,
        int oldTier,
        int newTier) {

    /**
     * Why a mutation ended the way it did. The failure constants are the ones spec §3.5 names, and
     * they are what {@code /mcaquests compat townstead explain} reports.
     */
    public enum Reason {
        /** The mutation ran. {@link #applied()} may still be less than {@link #requested()}. */
        SUCCESS,
        /** Legal, but nothing needed changing (already fed, already knows the skill). */
        NO_CHANGE,
        /** Townstead is not installed. */
        MOD_ABSENT,
        /** Townstead is installed but the capability this needs did not bind. */
        CAPABILITY_MISSING,
        /** The target villager could not be resolved, is unloaded, or is dead. */
        TARGET_MISSING,
        /** The feature is switched off — a disabled config toggle, or Townstead's own gate. */
        FEATURE_GATED,
        /** The request itself was out of range or malformed. */
        INVALID_VALUE,
        /** The daily cap was already exhausted, so nothing could be awarded. */
        DAILY_CAP,
        /** Something threw inside the bridge. Logged once per session; never propagated. */
        INTERNAL_ERROR
    }

    /** True when the mutation was legal, whether or not it needed to change anything. */
    public boolean succeeded() {
        return reason == Reason.SUCCESS || reason == Reason.NO_CHANGE;
    }

    /** True when Townstead accepted less than was asked for. */
    public boolean capped() {
        return reason == Reason.SUCCESS && applied < requested;
    }

    public static TownsteadMutationResult success(int requested, int applied, double before, double after) {
        return new TownsteadMutationResult(Reason.SUCCESS, requested, applied, before, after, 0, 0);
    }

    public static TownsteadMutationResult xp(int requested, int applied, int beforeXp, int afterXp,
                                             int oldTier, int newTier) {
        return new TownsteadMutationResult(Reason.SUCCESS, requested, applied, beforeXp, afterXp,
                oldTier, newTier);
    }

    public static TownsteadMutationResult noChange(double value) {
        return new TownsteadMutationResult(Reason.NO_CHANGE, 0, 0, value, value, 0, 0);
    }

    /**
     * A mutation that never ran. Carries no numbers, because none were observed.
     *
     * <p>Counted here rather than at each call site: every refusal in the integration is built through
     * this one factory, so it is the only place that cannot be forgotten when a new one is added.
     */
    public static TownsteadMutationResult failed(Reason reason) {
        if (reason == Reason.CAPABILITY_MISSING) {
            TownsteadCounters.capabilityMiss();
        } else if (reason != Reason.MOD_ABSENT) {
            // An absent mod is not a failure, it is the normal state of most servers.
            TownsteadCounters.mutationFailure();
        }
        return new TownsteadMutationResult(reason, 0, 0, 0.0D, 0.0D, 0, 0);
    }
}
