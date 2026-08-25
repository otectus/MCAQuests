package dev.otectus.mcaquests.compat;

/**
 * A villager's Townstead needs, normalised into MCA: Quests' own vocabulary (Townstead spec §2.3).
 *
 * <p>Field-for-field the public {@code TownsteadNeedsSnapshot}, with one addition: {@link #energy()},
 * the inverse of {@link #fatigue()}. Townstead stores fatigue where <em>lower is more rested</em>,
 * which is the opposite of how a quest reads ("rest until they have their energy back"), so pack
 * authors get both and can write whichever way round makes the definition legible.
 */
public record TownsteadNeedsView(
        int hunger,
        float saturation,
        float hungerExhaustion,
        int thirst,
        int quenched,
        float thirstExhaustion,
        int fatigue,
        boolean collapsed,
        boolean gated) {

    /**
     * Townstead's fatigue ceiling, and so the zero point for {@link #energy()}. Asserted by the
     * binding probe against the real jar rather than trusted: if Townstead ever widens the range,
     * the probe fails loudly instead of every energy objective silently drifting.
     */
    public static final int FATIGUE_MAX = 20;

    /** Rested-ness on a rising scale: {@code FATIGUE_MAX} when fully rested, {@code 0} when spent. */
    public int energy() {
        return FATIGUE_MAX - fatigue;
    }

    /** True when thirst is actually being simulated — Townstead gates it behind a thirst mod. */
    public boolean thirstActive() {
        return !gated;
    }
}
