package dev.otectus.mcaquests.compat;

/**
 * How long a waypoint the <em>player</em> drops survives on a given map.
 *
 * <p>Three values rather than a boolean, because the two supported mods genuinely differ and the
 * difference is visible: JourneyMap can write a waypoint the player will still have next week, while
 * Xaero's third-party store is rebuilt on every world load, so the honest offer there is a waypoint
 * for this session — which is worth making, and worth labelling as what it is.
 */
public enum PinSupport {
    /** No player-owned waypoint at all; the quest log hides the button. */
    NONE,
    /** The waypoint lasts until the player leaves the world. */
    SESSION,
    /** The map mod saves it, and only the player takes it away. */
    PERSISTENT
}
