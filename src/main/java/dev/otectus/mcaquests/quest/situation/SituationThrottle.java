package dev.otectus.mcaquests.quest.situation;

/**
 * Pure throttle decision for opening a new situation (0.8.0). Kept side-effect-free and free of any
 * Minecraft/registry state so it is unit-testable; the {@code SituationManager} (a later phase) supplies
 * the live counts/timestamps from {@code SituationSavedData} and acts on the result.
 */
public final class SituationThrottle {

    private SituationThrottle() {
    }

    /**
     * Whether a definition may open another instance in a village right now.
     *
     * @param openInVillage   how many situations are already open in this village
     * @param maxConcurrent   the per-village concurrency cap (config)
     * @param cooldownUntil   the game time before which this definition may not re-open here
     * @param globalCdUntil   the game time before which <em>any</em> situation may not open here (anti-spam)
     * @param now             the current game time
     * @return a {@link Decision} describing whether opening is allowed, and if not, why
     */
    public static Decision evaluate(int openInVillage, int maxConcurrent, long cooldownUntil,
                                    long globalCdUntil, long now) {
        if (maxConcurrent > 0 && openInVillage >= maxConcurrent) {
            return Decision.CAPPED;
        }
        if (now < cooldownUntil) {
            return Decision.ON_COOLDOWN;
        }
        if (now < globalCdUntil) {
            return Decision.GLOBAL_COOLDOWN;
        }
        return Decision.ALLOWED;
    }

    /** The outcome of a throttle check; non-{@link #ALLOWED} values are the reason a situation was suppressed. */
    public enum Decision {
        ALLOWED,
        CAPPED,
        ON_COOLDOWN,
        GLOBAL_COOLDOWN;

        public boolean allowed() {
            return this == ALLOWED;
        }
    }
}
