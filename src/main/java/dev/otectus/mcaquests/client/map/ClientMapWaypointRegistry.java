package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every map mod that has actually turned up, keyed by backend id.
 *
 * <p>Replaces the single-slot {@code MapWaypointBridge.Holder}. A holder can only hold the composite
 * that was built at client setup, which is the wrong shape for JourneyMap's documented lifecycle: its
 * plugin callback arrives when JourneyMap decides, on a thread JourneyMap chooses, before or after our
 * own setup ran. A keyed concurrent map accepts a backend whenever it appears, and lets the two
 * integrations be added, removed and configured independently.
 *
 * <p>{@link #register} sets {@link MapSyncDirtyFlag}, so a backend that binds after the first guidance
 * packet still gets the waypoints that packet asked for instead of waiting for the next one.
 */
public final class ClientMapWaypointRegistry {

    private static final Map<String, MapWaypointBackend> BACKENDS = new ConcurrentHashMap<>();

    private ClientMapWaypointRegistry() {
    }

    /** Adds or replaces the backend under {@code id}. Callable from any thread. */
    public static void register(String id, MapWaypointBackend backend) {
        if (id == null || backend == null) {
            return;
        }
        BACKENDS.put(id, backend);
        MapSyncDirtyFlag.set();
    }

    /** Removes a backend, returning it so a caller can clear it down first. */
    @Nullable
    public static MapWaypointBackend unregister(String id) {
        MapWaypointBackend removed = BACKENDS.remove(id);
        if (removed != null) {
            MapSyncDirtyFlag.set();
        }
        return removed;
    }

    /** Live view, safe to iterate: the map is concurrent and the reconciler walks it every pass. */
    public static Collection<MapWaypointBackend> backends() {
        return Collections.unmodifiableCollection(BACKENDS.values());
    }

    /** True on a dedicated server, and for the many players with no minimap at all. */
    public static boolean isEmpty() {
        return BACKENDS.isEmpty();
    }

    /**
     * The most durable pin any installed map can offer.
     *
     * <p>What the quest log's star button is: with JourneyMap the pin is saved, with only Xaero it
     * lasts the session, with neither there is nothing to press. Unusable backends do not count — a
     * button that silently does nothing is worse than no button.
     */
    public static PinSupport bestPinSupport() {
        PinSupport best = PinSupport.NONE;
        for (MapWaypointBackend backend : BACKENDS.values()) {
            if (!backend.isUsable()) {
                continue;
            }
            PinSupport pins = backend.capabilities().pins();
            if (pins.ordinal() > best.ordinal()) {
                best = pins;
            }
        }
        return best;
    }

    /** Empties the registry. For tests, and for a client that has left the game entirely. */
    public static void resetForTest() {
        BACKENDS.clear();
    }
}
