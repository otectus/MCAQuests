package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * What became of one {@link ReputationAward}, in Minecraft and Java types only.
 *
 * <p>From 1.6.6 the canonical backend delivers an award through MCA: Reputation's
 * {@code deliver(IncidentDelivery)} rather than its plain {@code record(...)}, and a delivery answers
 * with a <em>typed</em> outcome instead of a boolean. This record is that answer translated into types
 * the always-loaded side of the bridge may name.
 *
 * <p>The distinction the old boolean could not carry is the one that matters: "the village already
 * settled this exact operation" ({@link Status#DUPLICATE}), "accepted, but it produced no public
 * story" ({@link Status#ACCEPTED_NO_PUBLIC_INCIDENT}), "refused, do not retry"
 * ({@link Status#REFUSED_INVALID}) and "refused for now, a retry could work"
 * ({@link Status#REFUSED_CAPACITY}) were all a single {@code false}. A quest reward that treats the
 * second as a failure would re-pay itself, and one that treats the fourth as terminal would lose a
 * deed the player really did.
 *
 * @param status        what the ledger decided
 * @param newScore      the player's resulting standing, or {@code 0} when nothing could be read
 * @param retryable     true when the same operation key may be delivered again later
 * @param appliedProfile the social profile the deed was actually recorded under, when profiles are live
 */
public record ReputationDeliveryResult(Status status, int newScore, boolean retryable,
                                       Optional<ResourceLocation> appliedProfile) {

    /**
     * The outcomes MCA: Reputation's {@code ReceiptOutcome} can report, plus
     * {@link #UNAVAILABLE} for "this installation has no canonical ledger at all".
     */
    public enum Status {

        /** Recorded, and standing moved if the deed was worth anything. */
        APPLIED,
        /** This exact operation was already settled; the first answer stands. */
        DUPLICATE,
        /** Accepted and accounted for, but no public incident came of it (e.g. nobody saw it). */
        ACCEPTED_NO_PUBLIC_INCIDENT,
        /** The integration is switched off. Retryable: switching it on makes the deed deliverable. */
        REFUSED_DISABLED,
        /** Malformed or impossible. Terminal — retrying cannot help. */
        REFUSED_INVALID,
        /** The ledger is full right now. Retryable. */
        REFUSED_CAPACITY,
        /** No canonical ledger on this installation (Reputation absent, or the bridge is disabled). */
        UNAVAILABLE;

        /** True when the deed reached the ledger in some accepted form. */
        public boolean accepted() {
            return this == APPLIED || this == DUPLICATE || this == ACCEPTED_NO_PUBLIC_INCIDENT;
        }
    }

    public ReputationDeliveryResult {
        status = status == null ? Status.UNAVAILABLE : status;
        appliedProfile = appliedProfile == null ? Optional.empty() : appliedProfile;
    }

    public static ReputationDeliveryResult of(Status status, int newScore, boolean retryable) {
        return new ReputationDeliveryResult(status, newScore, retryable, Optional.empty());
    }

    public static ReputationDeliveryResult applied(int newScore) {
        return of(Status.APPLIED, newScore, false);
    }

    /** What a backend with no ledger answers. Never retryable: there is nothing to retry into. */
    public static ReputationDeliveryResult unavailable() {
        return of(Status.UNAVAILABLE, 0, false);
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public boolean accepted() {
        return status.accepted();
    }
}
