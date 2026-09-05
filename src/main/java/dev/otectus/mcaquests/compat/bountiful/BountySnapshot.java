package dev.otectus.mcaquests.compat.bountiful;

/**
 * Everything MCA: Quests reads off one Bountiful bounty, copied out at the moment it is read.
 *
 * <p>A snapshot rather than a handle to Bountiful's object, for the reason every cross-mod read here
 * is a snapshot: the object belongs to another mod, its lifetime is theirs, and a cash-in consumes it
 * — so anything held past the read would be a reference to a bounty that no longer exists.
 *
 * @param rarity         the rank, or {@link BountyRarity#UNKNOWN} when the reader is not bound
 * @param objectiveCount how many objectives the bounty listed, or {@code 0} when unreadable
 * @param rewardCount    how many rewards it listed, or {@code 0} when unreadable
 */
public record BountySnapshot(BountyRarity rarity, int objectiveCount, int rewardCount) {

    /** The snapshot for a bounty nothing could be read from. Never null, so callers need no guard. */
    public static BountySnapshot unknown() {
        return new BountySnapshot(BountyRarity.UNKNOWN, 0, 0);
    }
}
