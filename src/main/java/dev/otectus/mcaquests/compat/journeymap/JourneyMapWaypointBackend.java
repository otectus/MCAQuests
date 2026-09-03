package dev.otectus.mcaquests.compat.journeymap;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.marker.MarkerColours;
import dev.otectus.mcaquests.compat.BindingState;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendCapabilities;
import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.ProbeStep;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quest destinations on JourneyMap, through its published client plugin API.
 *
 * <h2>What JourneyMap draws, and what it does not</h2>
 *
 * <p>An automatic waypoint is map-only: {@code showOnMap} on, {@code showInWorld} and
 * {@code showBeacon} off. JourneyMap would otherwise put its own icon and its own light column at the
 * destination, on top of the one this mod's renderer is already drawing there — two markers, slightly
 * apart, in two different styles. The in-world job belongs to {@code client/marker}; the map job
 * belongs here, and neither does the other's.
 *
 * <p>A <em>pin</em> is the player's, so it keeps JourneyMap's normal appearance: they asked for a
 * waypoint, not for one this mod had opinions about, and it is theirs to delete.
 *
 * <h2>Dimensions</h2>
 *
 * <p>A JourneyMap waypoint carries its own dimension set and its own primary dimension, so unlike
 * Xaero this backend can hold points for anywhere — {@link MapBackendCapabilities#currentDimensionOnly()}
 * is false and the reconciler never withholds a spec. A spec whose dimension has changed is
 * <em>removed and recreated</em> rather than edited: the dimension set is what decides which map the
 * waypoint appears on, and JourneyMap indexes by it.
 *
 * <h2>Nothing is applied until it has been read back</h2>
 *
 * <p>{@code addWaypoint} returns {@code void}, and JourneyMap can decline a waypoint — a store that is
 * not up yet, an id it dislikes — without throwing. So every mutation ends with
 * {@code getWaypoint(modId, id)}, and only a waypoint JourneyMap hands back is recorded as applied.
 * That is the defect this whole layer was rebuilt around: the old code wrote down its intention, and
 * because its own record then said the waypoint was there, it never tried again.
 */
public final class JourneyMapWaypointBackend implements MapWaypointBackend {

    /** JourneyMap's mod id: the owner every waypoint call is made under. */
    private static final String MOD_ID = "journeymap";

    private static final MapBackendCapabilities CAPABILITIES =
            new MapBackendCapabilities(true, PinSupport.PERSISTENT, false);

    /** One automatic waypoint we have proof of: the object JourneyMap holds, and what we asked for. */
    private record Applied(Waypoint waypoint, WaypointSpec spec) {
    }

    private final IClientAPI api;
    private final WaypointSource waypoints;

    private final Map<String, Applied> applied = new LinkedHashMap<>();
    /**
     * Keys whose removal call failed.
     *
     * <p>They stay in {@link #applied} as well, so {@link #appliedKeys()} still reports them and the
     * reconciler asks again next pass; this set is what lets {@link #status()} and {@link #probe()}
     * say that a cleanup is outstanding rather than leaving it to look like an ordinary waypoint.
     */
    private final Set<String> pendingRemoval = new LinkedHashSet<>();
    /** Pin ids, for diagnostics only. Nothing here ever removes one; the player owns them. */
    private final Set<String> pinned = new LinkedHashSet<>();

    @Nullable
    private MapBackendStatus.Failure lastFailure;

    /** The production constructor: the API object JourneyMap hands to the plugin's callback. */
    JourneyMapWaypointBackend(IClientAPI api) {
        this(api, WaypointFactory::createWaypoint);
    }

    /**
     * Test seam.
     *
     * <p>Only waypoint <em>construction</em> is injected. Everything else goes through {@code api}, so
     * a test's stub {@link IClientAPI} exercises the real add / read-back / remove logic, which is
     * where the interesting behaviour is. {@link WaypointFactory} cannot serve a test at all: its
     * static store is installed by JourneyMap's own bootstrap and is absent outside a running game.
     */
    JourneyMapWaypointBackend(IClientAPI api, WaypointSource waypoints) {
        this.api = api;
        this.waypoints = waypoints;
    }

    /** {@link WaypointFactory#createWaypoint(String, BlockPos, String, String, boolean)}. */
    @FunctionalInterface
    interface WaypointSource {
        Waypoint create(String modId, BlockPos pos, String name, String dimensionId,
                        boolean persistent);
    }

    @Override
    public String id() {
        return McaQuestsJourneyMapPlugin.BACKEND_ID;
    }

    @Override
    public Optional<String> modVersion() {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString());
        } catch (Throwable ignored) {
            // No mod list at all: a unit test, or a game that has not got that far yet.
            return Optional.empty();
        }
    }

    @Override
    public MapBackendCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean isUsable() {
        // This object exists only because JourneyMap constructed the plugin and called initialize.
        // There is no partial binding to be in: every API member is pinned by compilation.
        return true;
    }

    @Override
    public Set<String> appliedKeys() {
        return Set.copyOf(applied.keySet());
    }

    @Override
    public MapMutationResult apply(WaypointSpec spec) {
        Applied existing = applied.get(spec.key());
        if (existing != null && existing.spec().equals(spec) && !pendingRemoval.contains(spec.key())) {
            return MapMutationResult.UNCHANGED;
        }
        if (existing != null && !recyclable(existing.spec(), spec)) {
            // The dimension or the ownership changed, and both are baked into the object JourneyMap
            // indexed. Editing them in place leaves a waypoint filed under the world it used to be in.
            if (!remove(existing.waypoint())) {
                return MapMutationResult.FAILED;
            }
            applied.remove(spec.key());
            existing = null;
        }
        Waypoint waypoint = existing == null ? create(spec, false) : retheme(existing.waypoint(), spec);
        if (waypoint == null) {
            return MapMutationResult.FAILED;
        }
        // add() is also the update call: JourneyMap keys on the waypoint's own id, so re-adding a
        // mutated object moves and re-colours it rather than producing a second marker.
        if (!add(waypoint) || !readBack(waypoint)) {
            applied.remove(spec.key());
            return MapMutationResult.FAILED;
        }
        applied.put(spec.key(), new Applied(waypoint, spec));
        pendingRemoval.remove(spec.key());
        return MapMutationResult.APPLIED;
    }

    @Override
    public MapMutationResult withdraw(String key) {
        Applied existing = applied.get(key);
        if (existing == null) {
            return MapMutationResult.UNCHANGED;
        }
        if (!remove(existing.waypoint())) {
            // Kept in the applied map on purpose, so the reconciler withdraws it again next pass.
            pendingRemoval.add(key);
            return MapMutationResult.FAILED;
        }
        applied.remove(key);
        pendingRemoval.remove(key);
        return MapMutationResult.APPLIED;
    }

    @Override
    public void clearAutomatic(ClearCause cause) {
        // One removal per waypoint we put there, and never removeAllWaypoints(modId): that is the
        // call that took the player's own saved pins with it.
        for (Applied entry : List.copyOf(applied.values())) {
            remove(entry.waypoint());
        }
        applied.clear();
        pendingRemoval.clear();
        McaQuests.LOGGER.debug("[MCA: Quests] JourneyMap quest waypoints cleared ({})", cause);
    }

    @Override
    public MapMutationResult pin(WaypointSpec spec) {
        Waypoint waypoint = create(spec, true);
        if (waypoint == null) {
            return MapMutationResult.FAILED;
        }
        if (!add(waypoint) || !readBack(waypoint)) {
            return MapMutationResult.FAILED;
        }
        String id = id(waypoint);
        if (id != null) {
            pinned.add(id);
        }
        return MapMutationResult.APPLIED;
    }

    @Override
    public MapBackendStatus status() {
        return new MapBackendStatus(id(), BindingState.BOUND, modVersion(), CAPABILITIES, List.of(),
                applied.size(), Optional.ofNullable(lastFailure));
    }

    @Override
    public List<ProbeStep> probe() {
        List<ProbeStep> steps = new ArrayList<>();
        steps.add(ProbeStep.passed("binding", McaQuestsJourneyMapPlugin.API_VERSION));
        steps.add(pendingRemoval.isEmpty()
                ? ProbeStep.passed("applied", Integer.toString(applied.size()))
                : ProbeStep.failed("applied", "pending removal: " + String.join(", ", pendingRemoval)));

        // The write half. JourneyMap can decline a waypoint without throwing, and from inside the game
        // that silence is indistinguishable from having no map mod installed — so the only honest
        // answer is to add one, read it back and take it away again.
        WaypointSpec spec = new WaypointSpec(McaQuests.MOD_ID + "/probe", BlockPos.ZERO,
                Level.OVERWORLD, "MCA: Quests", GuidanceKind.LOCATION,
                WaypointSpec.Ownership.AUTOMATIC);
        Waypoint waypoint = create(spec, false);
        if (waypoint == null) {
            steps.add(ProbeStep.failed("create", failureDetail()));
            return steps;
        }
        steps.add(ProbeStep.passed("create"));
        if (!add(waypoint)) {
            steps.add(ProbeStep.failed("add", failureDetail()));
            return steps;
        }
        steps.add(ProbeStep.passed("add"));
        steps.add(readBack(waypoint)
                ? ProbeStep.passed("read") : ProbeStep.failed("read", failureDetail()));
        steps.add(remove(waypoint)
                ? ProbeStep.passed("remove") : ProbeStep.failed("remove", failureDetail()));
        return steps;
    }

    @Override
    public void resetEpoch() {
        applied.clear();
        pendingRemoval.clear();
        pinned.clear();
    }

    /**
     * Whether an existing waypoint can be edited into {@code wanted} instead of being replaced.
     *
     * <p>Position, name and colour are ordinary fields. Dimension and ownership are not: the first
     * decides which map the waypoint is filed under, the second decides whether JourneyMap saves it.
     */
    private static boolean recyclable(WaypointSpec current, WaypointSpec wanted) {
        return current.dimension().equals(wanted.dimension())
                && current.ownership() == wanted.ownership();
    }

    /**
     * The overloads that take a dimension {@code String}, never the ones that take a
     * {@link ResourceKey}.
     *
     * <p>Both exist, and the {@code ResourceKey} forms are the ones JourneyMap's own guide shows. They
     * are also a trap: they are thin wrappers that call {@code ResourceKey#location()}, and the API
     * jar ships production bytecode, so that call is compiled to an SRG name. Under official mappings
     * — a unit test, a dev run — it resolves to nothing and every waypoint fails with
     * {@code NoSuchMethodError}. Converting the key on this side costs one call and works everywhere.
     */
    private static String dimensionId(WaypointSpec spec) {
        return spec.dimension().location().toString();
    }

    @Nullable
    private Waypoint create(WaypointSpec spec, boolean persistent) {
        try {
            String dimension = dimensionId(spec);
            Waypoint waypoint = waypoints.create(McaQuests.MOD_ID, spec.pos(), spec.label(),
                    dimension, persistent);
            if (waypoint == null) {
                return null;
            }
            waypoint.setDimensions(Set.of(dimension));
            waypoint.setPrimaryDimension(dimension);
            waypoint.setColor(MarkerColours.of(spec.kind()));
            if (!persistent) {
                // Map only: the in-world icon and the beacon at this block are the renderer's, not
                // JourneyMap's. A pin keeps JourneyMap's own appearance, because it is the player's.
                waypoint.setShowOnMap(true);
                waypoint.setShowInWorld(false);
                waypoint.setShowBeacon(false);
            }
            return waypoint;
        } catch (Throwable t) {
            record("create", t);
            return null;
        }
    }

    /** Edits an existing waypoint to match {@code spec}. Same object, so JourneyMap updates in place. */
    @Nullable
    private Waypoint retheme(Waypoint waypoint, WaypointSpec spec) {
        try {
            waypoint.setBlockPos(spec.pos());
            waypoint.setName(spec.label());
            waypoint.setColor(MarkerColours.of(spec.kind()));
            return waypoint;
        } catch (Throwable t) {
            record("retheme", t);
            return null;
        }
    }

    private boolean add(Waypoint waypoint) {
        try {
            api.addWaypoint(MOD_ID, waypoint);
            return true;
        } catch (Throwable t) {
            record("add", t);
            return false;
        }
    }

    private boolean remove(Waypoint waypoint) {
        try {
            api.removeWaypoint(MOD_ID, waypoint);
            return true;
        } catch (Throwable t) {
            record("remove", t);
            return false;
        }
    }

    /** Whether JourneyMap will hand the waypoint back. The only proof that anything happened. */
    private boolean readBack(Waypoint waypoint) {
        String id = id(waypoint);
        if (id == null) {
            return false;
        }
        try {
            return api.getWaypoint(MOD_ID, id) != null;
        } catch (Throwable t) {
            record("read", t);
            return false;
        }
    }

    @Nullable
    private String id(Waypoint waypoint) {
        try {
            return waypoint.getId();
        } catch (Throwable t) {
            record("id", t);
            return null;
        }
    }

    private String failureDetail() {
        return lastFailure == null ? "unknown" : lastFailure.fingerprint();
    }

    /**
     * Records a failure by its <em>cause</em> rather than its occurrence.
     *
     * <p>The operation, the exception type and the frame it came from: two failures with the same
     * fingerprint are the same bug, which is what lets the reconciler warn once instead of twenty
     * times a second. Nothing above DEBUG is logged here for the same reason.
     */
    private void record(String operation, Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        String origin = frames.length > 0 ? frames[0].toString() : "no stack";
        lastFailure = new MapBackendStatus.Failure(
                operation + '/' + t.getClass().getSimpleName() + '@' + origin,
                MapMutationResult.FAILED, Optional.ofNullable(t.getMessage()));
        McaQuests.LOGGER.debug("[MCA: Quests] JourneyMap {} failed; ignoring", operation, t);
    }
}
