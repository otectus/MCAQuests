package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Loads the Xaero's Minimap backend, if Xaero is installed, and registers it.
 *
 * <p>The seam in the shape {@code TownsteadCompat} already uses: the implementation class is named by
 * a <b>dotted</b> string and reached through {@code Class.forName}, so no bytecode in this
 * always-loaded class refers to {@code compat.map} and the static-link byte scan needs no exemption
 * for it. Nothing there is loaded unless Xaero is actually installed.
 *
 * <p><b>Xaero only.</b> JourneyMap arrives the other way round: it discovers its own annotated plugin
 * and calls it, so nothing here needs to — or may — look for it. This class exists because Xaero ships
 * no API, no annotations and no service loader, so somebody has to go and find it.
 *
 * <p><b>Client only.</b> Called from the client setup event, never from common setup: a waypoint lives
 * on one player's map, both supported mods are client mods, and a dedicated server has no business
 * loading either binding. On a server the registry simply stays empty.
 */
public final class MapWaypointCompat {

    private static final String XAERO_MOD_ID = "xaerominimap";
    private static final String XAERO_BACKEND = "dev.otectus.mcaquests.compat.map.XaeroWaypoints";
    /** The id the backend is registered under, and the id its config key and diagnostics use. */
    private static final String XAERO_BACKEND_ID = "xaero";

    private static boolean initialised;

    private MapWaypointCompat() {
    }

    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        initialise(XAERO_MOD_ID, XAERO_BACKEND_ID, XAERO_BACKEND);
        initialise("map_atlases", "map_atlases",
                "dev.otectus.mcaquests.compat.mapatlases.MapAtlasesWaypointBackend");
    }

    private static void initialise(String modId, String backendId, String backendClass) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            Method resolve = Class.forName(backendClass).getMethod("resolve");
            MapWaypointBackend backend = (MapWaypointBackend) resolve.invoke(null);
            if (backend == null) {
                return;
            }
            ClientMapWaypointRegistry.register(backendId, backend);
            McaQuests.LOGGER.info("[MCA: Quests] Map backend {}: {} ({})", backendId,
                    backend.status().binding(), backend.modVersion().orElse("version unknown"));
        } catch (Throwable t) {
            // A Xaero build this one does not recognise disables its waypoints and nothing else.
            McaQuests.LOGGER.error("[MCA: Quests] Map backend {} failed to start", backendId, t);
        }
    }

    /** Empties the registry and allows a fresh {@link #init()}. For tests. */
    public static synchronized void resetForTest() {
        initialised = false;
        ClientMapWaypointRegistry.resetForTest();
    }
}
