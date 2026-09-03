package dev.otectus.mcaquests.compat;

/**
 * Why every automatic waypoint is being taken away.
 *
 * <p>Carried through to the backends so a diagnostic can say which lifecycle event emptied the map,
 * and so a backend that must treat one case differently has what it needs to. Every value is a moment
 * at which the map the waypoints went into may no longer be the map the player is looking at.
 */
public enum ClearCause {
    /** The client joined a world. Start from a known-empty map rather than from an assumption. */
    LOGIN,
    /** The client left. Nothing may survive into the next world. */
    LOGOUT,
    /** The client level object was replaced without a dimension change — a server switch, or a resync. */
    LEVEL_CHANGE,
    /** The player changed dimension; coordinates from the old one mean nothing here. */
    DIMENSION_CHANGE,
    /** Respawn, or a dimension travel that rebuilt the local player. */
    CLONE,
    /** The player turned the feature, or this one backend, off. */
    DISABLED
}
