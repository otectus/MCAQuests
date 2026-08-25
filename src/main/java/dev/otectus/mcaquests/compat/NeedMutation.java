package dev.otectus.mcaquests.compat;

/**
 * A requested change to one Townstead need (Townstead spec §4.3).
 *
 * <p>Deliberately a plain value: the bridge decides what is legal, clamps to Townstead's range, and
 * reports what actually landed. Nothing here assumes a range, because Townstead owns them.
 *
 * <p>{@link Need#ENERGY} is the player-facing inverse of {@link Need#FATIGUE} — Townstead stores
 * fatigue where <em>lower is more rested</em>, which reads backwards in a reward ("+6 fatigue" for a
 * good night's sleep). Reward JSON may say either; {@code energy} is negated on the way in so pack
 * authors can write the number they mean.
 */
public record NeedMutation(Need need, Mode mode, double amount) {

    public enum Need {
        HUNGER,
        SATURATION,
        THIRST,
        QUENCHED,
        /** Townstead's raw fatigue value: lower is more rested. */
        FATIGUE,
        /** Inverted {@link #FATIGUE}: higher is more rested. */
        ENERGY
    }

    public enum Mode {
        /** Add {@code amount} to the current value. */
        DELTA,
        /** Set the value to {@code amount}. */
        TARGET
    }

    /**
     * Clamps a value to the range Townstead keeps that need in.
     *
     * <p>The ranges are not the same -- hunger runs to 100 while thirst, quenched and fatigue run to
     * 20 -- so a single shared bound would silently truncate three quarters of every hunger reward or
     * let a thirst value run five times past its ceiling. {@link Need#ENERGY} clamps to the fatigue
     * range because it is the same axis read from the other end.
     *
     * <p>{@link Need#SATURATION} has no published ceiling, so it is only floored at zero rather than
     * being given an invented upper bound.
     */
    public static double clamp(Need need, double value) {
        int max = switch (need) {
            case HUNGER -> TownsteadNeedsView.HUNGER_MAX;
            case THIRST -> TownsteadNeedsView.THIRST_MAX;
            case QUENCHED -> TownsteadNeedsView.QUENCHED_MAX;
            case FATIGUE, ENERGY -> TownsteadNeedsView.FATIGUE_MAX;
            case SATURATION -> Integer.MAX_VALUE;
        };
        return Math.max(0.0D, Math.min(max, value));
    }

    public static NeedMutation delta(Need need, double amount) {
        return new NeedMutation(need, Mode.DELTA, amount);
    }

    public static NeedMutation target(Need need, double amount) {
        return new NeedMutation(need, Mode.TARGET, amount);
    }
}
