package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.*;

/** An explicit set of destinations, with independent outcomes and no durability-based fan-out. */
public final class MapPinRouting {
    private MapPinRouting() { }
    public static List<MapWaypointBackend> eligible(Collection<MapWaypointBackend> backends,
            WaypointSpec spec, ResourceKey<Level> dimension) {
        return backends.stream().filter(b -> b.pinAvailability(spec, dimension).available())
                .sorted(Comparator.comparing(MapWaypointBackend::id)).toList();
    }
    public static Map<String, MapMutationResult> save(Collection<MapWaypointBackend> backends,
            Set<String> selected, WaypointSpec spec, ResourceKey<Level> dimension) {
        Map<String, MapMutationResult> results = new LinkedHashMap<>();
        for (MapWaypointBackend backend : backends.stream().sorted(Comparator.comparing(MapWaypointBackend::id)).toList()) {
            if (!selected.contains(backend.id())) continue;
            try {
                results.put(backend.id(), backend.pinAvailability(spec, dimension).available()
                        ? backend.pin(spec) : MapMutationResult.UNSUPPORTED);
            } catch (RuntimeException | LinkageError error) { results.put(backend.id(), MapMutationResult.FAILED); }
        }
        return Collections.unmodifiableMap(results);
    }
}
