package dev.otectus.mcaquests.compat;

import java.util.Optional;

/**
 * The answer to a {@link ReputationProfileQuery}, in Minecraft and Java types only.
 *
 * <p>Three-valued on purpose, mirroring MCA: Reputation's {@code ProfileQueryResult}: <b>yes</b>,
 * <b>no</b>, and <b>cannot say</b>. Collapsing the third into "no" is the mistake this type exists to
 * prevent — a disabled feature, a migrating save or an unresolvable giver would then read as a
 * negative fact about the player, and the authored fallback a pack wrote for exactly that case would
 * never run.
 *
 * @param availability why this answer is or is not usable
 * @param matched      the predicate's verdict; meaningful only when {@link #isAvailable()}
 * @param reason       a short machine-readable token for logs, straight from Reputation when it came
 *                     from there
 */
public record ReputationProfileMatch(Availability availability, boolean matched,
                                     Optional<String> reason) {

    /** Mirrors Reputation's {@code ProfileAvailability}, plus the no-mod case. */
    public enum Availability {

        /** A real answer. {@link ReputationProfileMatch#matched()} means what it says. */
        AVAILABLE,
        /** Profiles are switched off in Reputation's config. */
        DISABLED,
        /** This installation cannot answer: no Reputation, or a build without the profile feature. */
        UNSUPPORTED,
        /** No village, no giver, or an invalid query — nothing to evaluate against. */
        UNRESOLVED,
        /** The save is open read-only, so nothing may be reconciled to answer. */
        READ_ONLY,
        /** A migration is in flight; the profile is not yet its final shape. */
        MIGRATING,
        /** The clause needs a complete history and this save's history is partial. */
        INCOMPLETE_HISTORY,
        /** Something threw. Never silently a "no". */
        ERROR
    }

    public ReputationProfileMatch {
        availability = availability == null ? Availability.ERROR : availability;
        reason = reason == null ? Optional.empty() : reason;
    }

    public static ReputationProfileMatch available(boolean matched) {
        return new ReputationProfileMatch(Availability.AVAILABLE, matched, Optional.empty());
    }

    public static ReputationProfileMatch available(boolean matched, String reason) {
        return new ReputationProfileMatch(Availability.AVAILABLE, matched, Optional.ofNullable(reason));
    }

    public static ReputationProfileMatch unavailable(Availability availability, String reason) {
        return new ReputationProfileMatch(
                availability == null || availability == Availability.AVAILABLE
                        ? Availability.UNRESOLVED
                        : availability,
                false, Optional.ofNullable(reason));
    }

    /** What a backend with no profile support answers. */
    public static ReputationProfileMatch unsupported() {
        return unavailable(Availability.UNSUPPORTED, "no_profile_support");
    }

    public boolean isAvailable() {
        return availability == Availability.AVAILABLE;
    }

    /** The verdict, or the authored fallback when there is no verdict to be had. */
    public boolean orElse(boolean fallback) {
        return isAvailable() ? matched : fallback;
    }
}
