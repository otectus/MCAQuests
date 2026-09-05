package dev.otectus.mcaquests.compat.bountiful;

import java.util.UUID;

/**
 * One successfully cashed-in Bountiful bounty, as reported to MCA: Quests.
 *
 * <p>Flat and primitive on purpose: it crosses from a guarded hook into ordinary quest code, so it
 * must carry no reference to anything Bountiful owns and must stay valid after the bounty item has
 * been consumed. The rarity travels as its <em>name</em> rather than as a {@link BountyRarity}
 * because the value may be a rank this build of MCA: Quests has never heard of, and losing that to
 * {@code UNKNOWN} at the boundary would throw away the only evidence of it.
 *
 * <p>Nothing emits one yet — the cash-in hook that does is a later step — but the listener interface
 * on {@link BountifulBridge} needs the shape to exist, and defining it here keeps the bridge's
 * contract fixed before anything implements it.
 *
 * @param playerId        who cashed the bounty in
 * @param serverGameTime  the server tick it happened on, used for the short dedupe window
 * @param rarity          Bountiful's own rarity name, or the empty string when it could not be read
 * @param objectiveCount  how many objectives the bounty listed
 * @param dedupeKey       a stable per-bounty key, so one cash-in reported twice is credited once
 */
public record BountyCompletion(UUID playerId, long serverGameTime, String rarity, int objectiveCount,
                               String dedupeKey) {

    /** The rarity as this build understands it; an unrecognised name reads as {@code UNKNOWN}. */
    public BountyRarity parsedRarity() {
        return BountyRarity.fromName(rarity);
    }
}
