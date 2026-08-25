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

    public static NeedMutation delta(Need need, double amount) {
        return new NeedMutation(need, Mode.DELTA, amount);
    }

    public static NeedMutation target(Need need, double amount) {
        return new NeedMutation(need, Mode.TARGET, amount);
    }
}
