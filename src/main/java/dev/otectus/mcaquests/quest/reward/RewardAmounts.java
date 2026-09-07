package dev.otectus.mcaquests.quest.reward;

/** Numeric reward normalization shared by display and payout. */
final class RewardAmounts {
    private RewardAmounts() { }

    static int positiveScaled(int amount, double multiplier) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, Math.round(amount * multiplier)));
    }

    /** Vanilla adds XP into int fields; avoid wrapping the player's existing total before its clamp. */
    static int remainingCapacity(int current, int amount) {
        return (int) Math.min(Math.max(0, amount), Math.max(0L, (long) Integer.MAX_VALUE - current));
    }

    static int hearts(int amount, double multiplier, int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = maximum; // The configured maximum remains the ceiling even for an inverted pair.
        return (int) Math.max(low, Math.min(high, Math.round(amount * multiplier)));
    }
}
