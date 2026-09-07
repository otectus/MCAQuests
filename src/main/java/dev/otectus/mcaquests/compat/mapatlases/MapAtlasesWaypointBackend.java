package dev.otectus.mcaquests.compat.mapatlases;

import dev.otectus.mcaquests.compat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.*;

/** Server-loadable backend. All client and native operations live behind its delegate. */
public final class MapAtlasesWaypointBackend implements MapWaypointBackend {
    public interface Actions {
        boolean pinsSupported();
        boolean navigationSupported();
        MapActionAvailability availability(WaypointSpec spec, boolean pin);
        MapMutationResult pin(WaypointSpec spec);
        MapMutationResult navigate(WaypointSpec spec);
        void reset();
        void tick();
        List<Component> details();
    }
    private final AtlasMarkerStore store;
    private final String version;
    private final Actions actions;
    public MapAtlasesWaypointBackend(AtlasMarkerStore store, String version, Actions actions) {
        this.store = store; this.version = version; this.actions = actions;
    }
    public static MapWaypointBackend resolve() throws ReflectiveOperationException {
        return (MapWaypointBackend) Class.forName(
                "dev.otectus.mcaquests.compat.mapatlases.client.AtlasRuntime").getMethod("create").invoke(null);
    }
    @Override public String id() { return "map_atlases"; }
    @Override public Optional<String> modVersion() { return Optional.ofNullable(version); }
    @Override public MapBackendCapabilities capabilities() {
        return new MapBackendCapabilities(true,
                actions != null && actions.pinsSupported() ? PinSupport.PERSISTENT : PinSupport.NONE, false);
    }
    @Override public boolean isUsable() { return AtlasHookState.applied(); }
    @Override public Set<String> appliedKeys() { return store.keys(); }
    @Override public MapMutationResult apply(WaypointSpec spec) {
        if (!isUsable() || spec.ownership() != WaypointSpec.Ownership.AUTOMATIC)
            return MapMutationResult.UNSUPPORTED;
        return store.put(spec) ? MapMutationResult.APPLIED : MapMutationResult.UNCHANGED;
    }
    @Override public MapMutationResult withdraw(String key) {
        return store.remove(key) ? MapMutationResult.APPLIED : MapMutationResult.UNCHANGED;
    }
    @Override public void clearAutomatic(ClearCause cause) { resetEpoch(); }
    @Override public void resetEpoch() { store.clear(); if (actions != null) actions.reset(); }
    @Override public boolean supportsNavigation() { return actions != null && actions.navigationSupported(); }
    @Override public MapActionAvailability navigationAvailability(WaypointSpec spec) {
        return actions == null ? MapActionAvailability.unavailable("unsupported_navigation") : actions.availability(spec, false);
    }
    @Override public MapActionAvailability pinAvailability(WaypointSpec spec, ResourceKey<Level> dimension) {
        return actions == null ? MapActionAvailability.unavailable("unsupported_pins") : actions.availability(spec, true);
    }
    @Override public MapMutationResult pin(WaypointSpec spec) {
        return actions == null ? MapMutationResult.UNSUPPORTED : actions.pin(spec);
    }
    @Override public MapMutationResult navigate(WaypointSpec spec) {
        return actions == null ? MapMutationResult.UNSUPPORTED : actions.navigate(spec);
    }
    @Override public void clientTick() { if (actions != null) actions.tick(); }
    @Override public List<Component> details() { return actions == null ? List.of() : actions.details(); }
    @Override public MapBackendStatus status() {
        return new MapBackendStatus(id(), isUsable() ? BindingState.BOUND : BindingState.PARTIAL,
                modVersion(), capabilities(), isUsable() ? List.of() : List.of(AtlasHookState.failure()),
                store.keys().size(), Optional.empty());
    }
    @Override public List<ProbeStep> probe() {
        // Isolated probe state cannot overwrite a live quest or save a native pin.
        AtlasMarkerStore probe = new AtlasMarkerStore();
        WaypointSpec point = new WaypointSpec("mcaquests:atlas_probe", net.minecraft.core.BlockPos.ZERO,
                Level.OVERWORLD, "", dev.otectus.mcaquests.quest.guidance.GuidanceKind.LOCATION,
                WaypointSpec.Ownership.AUTOMATIC);
        boolean stored = probe.put(point), removed = probe.remove(point.key());
        return List.of(new ProbeStep("atlas_manifest", AtlasHookState.preflight(), Optional.of(AtlasHookState.failure())),
                new ProbeStep("atlas_hooks", AtlasHookState.applied(), Optional.empty()),
                new ProbeStep("atlas_temporary_state", stored && removed && probe.keys().isEmpty(), Optional.empty()),
                new ProbeStep("atlas_viewport", AtlasHookState.viewportObserved(), Optional.empty()),
                new ProbeStep("atlas_render_observed", AtlasHookState.renderObserved(), Optional.empty()));
    }
}
