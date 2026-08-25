package dev.otectus.mcaquests.compat;

/**
 * How much profession experience an award may actually grant (Townstead spec §4.4).
 *
 * <p>Pure arithmetic, deliberately separated from the reflective plumbing that fetches the numbers.
 * The rules it encodes — reset the daily counter on a new world day, take the smallest of the request,
 * the remaining daily cap and the room below the maximum — are the part that would be silently and
 * expensively wrong, and they are unreachable for a test while they live inside a method that needs
 * bound Townstead handles to get anywhere.
 *
 * <p>This is the general path, used for every profession Townstead has no {@code ProfessionXpType}
 * constant for. The four it does have go through Townstead's own {@code ProfessionProgress.addXp}
 * instead, because reimplementing maths a mod already does for itself is how the two come to disagree.
 */
public final class TownsteadXpMath {

    /** Why an award landed the way it did. */
    public enum Outcome {
        /** Some or all of the request was granted. */
        GRANTED,
        /** Nothing was granted because today's allowance is already spent. */
        DAILY_CAP,
        /** Nothing was granted because this profession is already at its maximum. */
        AT_MAX,
        /** The request itself was not a positive number. */
        INVALID
    }

    /**
     * @param applied    experience actually granted, never more than requested
     * @param newXp      the resulting total
     * @param newXpToday the resulting daily counter, already reset if the day rolled over
     */
    public record Award(Outcome outcome, int applied, int newXp, int newXpToday) {

        public boolean granted() {
            return outcome == Outcome.GRANTED;
        }
    }

    private TownsteadXpMath() {
    }

    /**
     * Works out what a request is allowed to become.
     *
     * @param recordedDay the world day the stored daily counter belongs to; a different {@code worldDay}
     *                    resets it, which is what stops yesterday's allowance limiting today
     * @param dailyCap    Townstead's cap for this profession; {@code 0} or less means uncapped
     */
    public static Award award(int requested, int currentXp, int maxXp, int xpToday, long recordedDay,
                              long worldDay, int dailyCap, boolean respectDailyCap) {
        if (requested <= 0) {
            return new Award(Outcome.INVALID, 0, currentXp, xpToday);
        }
        // A new day wipes the counter before anything is measured against it.
        int spentToday = recordedDay == worldDay ? Math.max(0, xpToday) : 0;

        int room = Math.max(0, maxXp - currentXp);
        if (room <= 0) {
            return new Award(Outcome.AT_MAX, 0, currentXp, spentToday);
        }

        int allowed = Math.min(requested, room);
        if (respectDailyCap && dailyCap > 0) {
            allowed = Math.min(allowed, Math.max(0, dailyCap - spentToday));
        }
        if (allowed <= 0) {
            return new Award(Outcome.DAILY_CAP, 0, currentXp, spentToday);
        }
        return new Award(Outcome.GRANTED, allowed, currentXp + allowed, spentToday + allowed);
    }

    /**
     * The tick to record as the last tier-up: {@code now} only when the tier actually rose.
     *
     * <p>Preserving the old value otherwise matters because Townstead uses it to pace what a villager
     * may do next; refreshing it on every award would quietly reset that timer each time a player
     * handed in a quest.
     */
    public static long tierUpTick(int tierBefore, int tierAfter, long previousTick, long now) {
        return tierAfter > tierBefore ? now : previousTick;
    }
}
