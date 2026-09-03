package dev.otectus.mcaquests.client.map;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Something the map cares about has changed."
 *
 * <p>The map layer used to rebuild and diff its whole desired set on every client tick, twenty times a
 * second, to discover what it already knew: that guidance only changes when a packet arrives, when the
 * player changes world, or when they change a setting. This is that discovery, made once by whoever
 * caused it.
 *
 * <p><b>Not one Minecraft type here, deliberately.</b> JourneyMap's plugin callback registers a backend
 * — and so sets this flag — from its own parallel dispatch worker, which may run before the client has
 * finished setup. An {@link AtomicBoolean} is safe from any thread; anything that touched
 * {@code Minecraft} would not be.
 *
 * <p>Starts set, so the first tick after class load reconciles whatever guidance already arrived.
 */
public final class MapSyncDirtyFlag {

    private static final AtomicBoolean DIRTY = new AtomicBoolean(true);

    private MapSyncDirtyFlag() {
    }

    /** Marks the desired waypoint set as possibly out of date. Callable from any thread. */
    public static void set() {
        DIRTY.set(true);
    }

    /** Reads and clears in one step, so two consumers cannot both act on one change. */
    public static boolean consume() {
        return DIRTY.getAndSet(false);
    }

    /** Whether a reconcile is pending, without consuming it. */
    public static boolean isSet() {
        return DIRTY.get();
    }
}
