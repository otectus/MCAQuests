package dev.otectus.mcaquests.compat;

/**
 * What one map mod can be asked to do, so that nothing has to special-case a backend by name.
 *
 * @param automaticWaypoints   whether the backend takes quest-owned waypoints at all
 * @param pins                 how durable a player-dropped waypoint is here
 * @param currentDimensionOnly whether the backend's waypoints carry no dimension of their own, so the
 *                             reconciler must withhold every spec for anywhere else. Xaero's
 *                             third-party waypoint has no dimension field; this flag is how that fact
 *                             reaches the one class that knows which dimension the player is in,
 *                             without a backend importing a client type to ask
 */
public record MapBackendCapabilities(
        boolean automaticWaypoints,
        PinSupport pins,
        boolean currentDimensionOnly) {
}
