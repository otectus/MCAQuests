package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import net.minecraftforge.fml.ModList;

/**
 * Entry point for the optional JourneyMap / Xaero's Minimap waypoint integration.
 *
 * <p>The seam, in the shape {@link TownsteadCompat} already uses: the implementation class is named by
 * a <b>dotted</b> string and reached through {@code Class.forName}, so no bytecode in this
 * always-loaded class refers to {@code compat.map} and the static-link byte scan needs no exemption
 * for it. Nothing here is reached unless a mapping mod is actually installed.
 *
 * <p><b>Client only.</b> Called from the client setup event, never from common setup: a waypoint lives
 * on one player's map, both supported mods are client mods, and a dedicated server has no business
 * loading either binding. On a server the bridge stays the no-op it starts as.
 */
public final class MapWaypointCompat {

    private static final String IMPLEMENTATION =
            "dev.otectus.mcaquests.compat.map.ReflectiveMapWaypointBridge";

    /** Mapping mods worth looking for. Absence of all of them is the common case, and is silent. */
    private static final String[] MOD_IDS = {"journeymap", "xaerominimap"};

    private static boolean initialised;

    private MapWaypointCompat() {
    }

    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        if (!anyInstalled()) {
            McaQuests.LOGGER.debug("[MCA: Quests] No supported minimap installed; quest waypoints off.");
            return;
        }
        try {
            Class<?> implementation = Class.forName(IMPLEMENTATION);
            MapWaypointBridge bridge =
                    (MapWaypointBridge) implementation.getDeclaredConstructor().newInstance();
            MapWaypointBridge.Holder.set(bridge);
            bridge.describe().forEach(line -> McaQuests.LOGGER.info("[MCA: Quests] Minimap — {}", line));
        } catch (Throwable t) {
            // A minimap build this one does not recognise disables the waypoints and nothing else.
            McaQuests.LOGGER.error("[MCA: Quests] Minimap waypoint integration failed to start; "
                    + "quest destinations will still show on the tracker and the world marker.", t);
        }
    }

    private static boolean anyInstalled() {
        for (String modId : MOD_IDS) {
            if (ModList.get().isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    /** Restores the no-op bridge and allows a fresh {@link #init()}. For tests. */
    public static synchronized void resetForTest() {
        initialised = false;
        MapWaypointBridge.Holder.reset();
    }
}
