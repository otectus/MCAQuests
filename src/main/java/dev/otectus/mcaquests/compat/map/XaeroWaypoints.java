package dev.otectus.mcaquests.compat.map;

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
import dev.otectus.mcaquests.compat.map.MapBinding.Member;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quest destinations on Xaero's Minimap, bound by name.
 *
 * <h2>There is no API to bind to</h2>
 *
 * <p>Xaero's Minimap ships no {@code api} package, no annotations and no service loader. What it does
 * ship, since the {@code xaero.hud} rewrite, is a purpose-built third-party waypoint store — the same
 * one its own Waystones support uses — where a mod registers waypoints under a {@link ResourceLocation}
 * origin and Xaero renders them without ever writing them to the player's saved waypoint files. That
 * is exactly the ownership this feature wants: the waypoints belong to the quest, and a player who
 * uninstalls this mod is not left tidying up somebody else's markers.
 *
 * <h2>Two origins</h2>
 *
 * <p>{@code mcaquests:quests} holds the automatic points and only those, so
 * {@link #clearAutomatic(ClearCause)} can empty it wholesale — which is the cheapest correct cleanup
 * Xaero offers — without touching anything the player asked for. {@code mcaquests:pins} holds what
 * they asked for. The third-party store is not saved, so a pin here lasts the session and
 * {@link PinSupport#SESSION} says so rather than the interface promising something Xaero cannot keep.
 *
 * <h2>Re-fetched, never cached</h2>
 *
 * <p>The store hangs off the session's <em>current world container</em>, and that object is replaced
 * when the player changes world. Holding on to one would leave waypoints being written into a
 * container nothing renders any more — a bug that would look exactly like "the integration stopped
 * working after I went to the Nether". So the chain down to the store is walked on every mutation, and
 * the object that comes back is compared by identity: a different store is a new world, and everything
 * this class believed about what is on the map is discarded so the reconciler can fill it again.
 *
 * <h2>Dimensions are somebody else's problem</h2>
 *
 * <p>A Xaero waypoint has no dimension field, so publishing an overworld coordinate while the player
 * is in the Nether would put a marker somewhere they have no reason to go. This backend cannot check
 * that itself — it may not import a client class to ask where the player is — so it declares
 * {@link MapBackendCapabilities#currentDimensionOnly()} and the reconciler withholds the rest.
 */
public final class XaeroWaypoints implements MapWaypointBackend {

    /**
     * Xaero's package root, stored <em>dotted</em>, never in internal slash form — that is what lets
     * {@code NoMinimapStaticLinkTest} byte-scan for slash-form references and treat any hit as a
     * regression, with no exemption for this file.
     */
    private static final String ROOT = "xaero.";

    /** The class whose presence identifies an installed Xaero's Minimap. */
    private static final String PROBE = "common.XaeroMinimapSession";

    private static final String SESSION = "common.XaeroMinimapSession";
    private static final String MINIMAP_SESSION = "hud.minimap.module.MinimapSession";
    private static final String WORLD_MANAGER = "hud.minimap.world.MinimapWorldManager";
    private static final String WORLD_CONTAINER = "hud.minimap.world.container.MinimapWorldContainer";
    private static final String THIRD_PARTY_MANAGER =
            "hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager";
    private static final String THIRD_PARTY = "hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints";
    private static final String WAYPOINT = "common.minimap.waypoints.Waypoint";

    static final Member CURRENT_SESSION =
            Member.statik(SESSION, "getCurrentSession", Object.class, 0);
    static final Member WAYPOINTS_MANAGER =
            Member.virtual(SESSION, "getWaypointsManager", Object.class, 0);
    static final Member WORLD_MANAGER_OF =
            Member.virtual(MINIMAP_SESSION, "getWorldManager", Object.class, 0);
    static final Member ROOT_CONTAINER =
            Member.virtual(WORLD_MANAGER, "getCurrentRootContainer", Object.class, 0);
    static final Member THIRD_PARTY_MANAGER_OF =
            Member.virtual(WORLD_CONTAINER, "getThirdPartyWaypointManager", Object.class, 0);
    static final Member ORIGIN =
            Member.virtual(THIRD_PARTY_MANAGER, "get", Object.class, 1, ResourceLocation.class);
    static final Member ADD = Member.virtual(THIRD_PARTY, "add", void.class, 2, String.class, null);
    static final Member REMOVE = Member.virtual(THIRD_PARTY, "remove", void.class, 1, String.class);
    static final Member CLEAR_ORIGIN = Member.virtual(THIRD_PARTY, "clear", void.class, 0);
    /**
     * {@code (x, y, z, name, initials, colourIndex, purposeOrdinal, temporary, yIncluded)}.
     *
     * <p>The all-primitive overload, pinned at index five. Its enum-typed twin has the same nine
     * parameters and differs only in taking {@code WaypointColor} and {@code WaypointPurpose} — which
     * this mod cannot name without linking itself to Xaero, and which is the whole reason the binding
     * grew positional parameter hints.
     */
    static final Member NEW_WAYPOINT = Member.constructor(WAYPOINT, 9,
            int.class, int.class, int.class, String.class, String.class,
            int.class, int.class, boolean.class, boolean.class);
    static final Member SET_ORIGIN = Member.virtual(WAYPOINT, "setThirdPartyOrigin", void.class, 1,
            ResourceLocation.class);

    /** Everything the integration cannot work without. Replayed against a real jar by the probe test. */
    static final List<Member> ESSENTIAL = List.of(CURRENT_SESSION, WAYPOINTS_MANAGER, WORLD_MANAGER_OF,
            ROOT_CONTAINER, THIRD_PARTY_MANAGER_OF, ORIGIN, ADD, REMOVE, CLEAR_ORIGIN, NEW_WAYPOINT,
            SET_ORIGIN);

    static final Member COLOUR_ENUM = Member.cls("hud.minimap.waypoint.WaypointColor");
    static final Member PURPOSE_ENUM = Member.cls("hud.minimap.waypoint.WaypointPurpose");

    /**
     * The two enums the primitive constructor takes indexes into.
     *
     * <p>Optional because the constructor takes {@code int}s and will accept any of them: without
     * these every quest waypoint is the first colour in Xaero's palette, which is worse-looking and
     * entirely functional. Reading the ordinals off the live enums by <em>name</em> is what replaced a
     * hard-coded table that happened to match one Xaero build and would have silently recoloured every
     * waypoint on the day the palette gained an entry.
     */
    static final List<Member> OPTIONAL = List.of(COLOUR_ENUM, PURPOSE_ENUM);

    /** {@code WaypointPurpose.NORMAL}: a quest destination is not a death point. */
    private static final String PURPOSE_NORMAL = "NORMAL";

    /** Index 0 is what an unresolved palette falls back to — a wrong colour, never a missing waypoint. */
    private static final int FALLBACK_ORDINAL = 0;

    private static final String MOD_ID = "xaerominimap";
    private static final ResourceLocation QUESTS = ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quests");
    private static final ResourceLocation PINS = ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "pins");

    private static final MapBackendCapabilities CAPABILITIES =
            new MapBackendCapabilities(true, PinSupport.SESSION, true);

    /** One automatic waypoint we have proof of: the object Xaero holds, and what we asked it to be. */
    private record Applied(Object waypoint, WaypointSpec spec) {
    }

    private final BindingState binding;
    private final List<String> missingMembers;
    private final Calls calls;

    private final Map<String, Applied> applied = new LinkedHashMap<>();
    private final Set<String> pinned = new HashSet<>();
    /** The store the {@link #applied} map is about; a different object means a different world. */
    private WeakReference<Object> automaticStore = new WeakReference<>(null);

    XaeroWaypoints(BindingState binding, List<String> missingMembers, Calls calls) {
        this.binding = binding;
        this.missingMembers = List.copyOf(missingMembers);
        this.calls = calls;
    }

    /**
     * Resolves Xaero's Minimap, or {@code null} when it is absent.
     *
     * <p>Called reflectively by {@code client.map.MapWaypointCompat}, which is why it is public and
     * takes nothing: no first-party class outside this package may name this one.
     */
    @Nullable
    public static XaeroWaypoints resolve() {
        MapBinding.Resolution resolution = MapBinding.resolve("Xaero's Minimap", ROOT, PROBE,
                ESSENTIAL, OPTIONAL, XaeroWaypoints.class.getClassLoader());
        if (!resolution.isBound() && !resolution.isPartial()) {
            return null;
        }
        BindingState state = resolution.isBound() ? BindingState.BOUND : BindingState.PARTIAL;
        return new XaeroWaypoints(state, resolution.missing(), new Reflective(resolution));
    }

    @Override
    public String id() {
        return "xaero";
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
        return binding == BindingState.BOUND;
    }

    @Override
    public Set<String> appliedKeys() {
        return Set.copyOf(applied.keySet());
    }

    @Override
    public MapMutationResult apply(WaypointSpec spec) {
        Object store = automaticStore();
        if (store == null) {
            return MapMutationResult.RETRY_LATER;
        }
        Applied existing = applied.get(spec.key());
        if (existing != null && existing.spec().equals(spec)) {
            return MapMutationResult.UNCHANGED;
        }
        Object waypoint = calls.waypoint(QUESTS, spec);
        if (waypoint == null) {
            return MapMutationResult.FAILED;
        }
        // add() is a map put, so this both creates and moves. Nothing is recorded until it returns:
        // believing a waypoint exists is what stopped the old code from ever trying again.
        if (!calls.add(store, spec.key(), waypoint)) {
            return MapMutationResult.FAILED;
        }
        applied.put(spec.key(), new Applied(waypoint, spec));
        return MapMutationResult.APPLIED;
    }

    @Override
    public MapMutationResult withdraw(String key) {
        Object store = automaticStore();
        if (!applied.containsKey(key)) {
            return MapMutationResult.UNCHANGED;
        }
        if (store == null) {
            return MapMutationResult.RETRY_LATER;
        }
        if (!calls.remove(store, key)) {
            // Kept in the applied map on purpose, so the reconciler withdraws it again next pass.
            return MapMutationResult.FAILED;
        }
        applied.remove(key);
        return MapMutationResult.APPLIED;
    }

    @Override
    public void clearAutomatic(ClearCause cause) {
        Object store = calls.store(QUESTS);
        if (store != null) {
            calls.clear(store);
        }
        applied.clear();
        automaticStore = new WeakReference<>(null);
        McaQuests.LOGGER.debug("[MCA: Quests] Xaero quest waypoints cleared ({})", cause);
    }

    @Override
    public MapMutationResult pin(WaypointSpec spec) {
        Object store = calls.store(PINS);
        if (store == null) {
            return MapMutationResult.RETRY_LATER;
        }
        Object waypoint = calls.waypoint(PINS, spec);
        if (waypoint == null) {
            return MapMutationResult.FAILED;
        }
        String key = pinKey(spec);
        if (!calls.add(store, key, waypoint)) {
            return MapMutationResult.FAILED;
        }
        pinned.add(key);
        return MapMutationResult.APPLIED;
    }

    /**
     * The store key for a pin, dimension first.
     *
     * <p>Derived here rather than taken from {@link WaypointSpec#key()} because the store is shared
     * across dimensions and the old key was coordinates alone: the same block in the overworld and the
     * Nether collided, and one of the two pins was silently the other.
     */
    static String pinKey(WaypointSpec spec) {
        BlockPos pos = spec.pos();
        return "pin/" + spec.dimension().location() + '/' + pos.getX() + '/' + pos.getY() + '/'
                + pos.getZ();
    }

    @Override
    public MapBackendStatus status() {
        BindingState state = binding;
        if (state == BindingState.BOUND && calls.store(QUESTS) == null) {
            // Before the player has joined a world there is no session at all, which is an ordinary
            // state rather than an error, and reads to an operator as "wait a moment" rather than
            // "your Xaero build is unsupported".
            state = BindingState.NOT_READY;
        }
        return new MapBackendStatus(id(), state, modVersion(), CAPABILITIES, missingMembers,
                applied.size(), calls.lastFailure());
    }

    @Override
    public List<ProbeStep> probe() {
        List<ProbeStep> steps = new ArrayList<>();
        steps.add(binding == BindingState.BOUND
                ? ProbeStep.passed("binding")
                : ProbeStep.failed("binding", String.join(", ", missingMembers)));
        Object store = calls.store(QUESTS);
        if (store == null) {
            steps.add(ProbeStep.failed("store", "no minimap session"));
            return steps;
        }
        steps.add(ProbeStep.passed("store", Integer.toString(applied.size())));

        // The write half. Both mods can decline a waypoint without throwing and neither says so, and
        // from inside the game that silence is indistinguishable from having no minimap installed —
        // so the only honest answer is to add one and take it away again.
        String key = McaQuests.MOD_ID + "/probe";
        WaypointSpec spec = new WaypointSpec(key, BlockPos.ZERO, Level.OVERWORLD, "MCA: Quests",
                GuidanceKind.LOCATION, WaypointSpec.Ownership.AUTOMATIC);
        Object waypoint = calls.waypoint(QUESTS, spec);
        if (waypoint == null) {
            steps.add(ProbeStep.failed("create", failureDetail()));
            return steps;
        }
        steps.add(ProbeStep.passed("create"));
        steps.add(calls.add(store, key, waypoint)
                ? ProbeStep.passed("add") : ProbeStep.failed("add", failureDetail()));
        steps.add(calls.remove(store, key)
                ? ProbeStep.passed("remove") : ProbeStep.failed("remove", failureDetail()));
        return steps;
    }

    @Override
    public void resetEpoch() {
        applied.clear();
        pinned.clear();
        automaticStore = new WeakReference<>(null);
    }

    private String failureDetail() {
        return calls.lastFailure().map(MapBackendStatus.Failure::fingerprint).orElse("unknown");
    }

    /**
     * The automatic origin's store, with the identity check that makes a world change survivable.
     *
     * <p>A store object we have not seen before belongs to a container Xaero built for a different
     * world; everything published into the old one went with it. Dropping the applied map is what
     * turns that from "the integration stopped working" into one reconciliation pass.
     */
    @Nullable
    private Object automaticStore() {
        Object store = calls.store(QUESTS);
        if (store == null) {
            return null;
        }
        if (automaticStore.get() != store) {
            applied.clear();
            automaticStore = new WeakReference<>(store);
        }
        return store;
    }

    /**
     * Every reflective call this backend makes, behind one seam.
     *
     * <p>Package-private so a test can supply a double: the alternative is a manifest resolved against
     * a real Xaero jar, which is a file nobody can put on a Maven and therefore not a thing the normal
     * suite may need. {@code MapBindingProbeTest} covers the other half — that the names in the
     * manifest are the names Xaero actually has.
     *
     * <p>Every method reports success as "did not throw", because the store's methods return
     * {@code void} and a null out of a void handle says nothing at all.
     */
    interface Calls {

        /** The third-party waypoint store for {@code origin}, or null when there is no session yet. */
        @Nullable
        Object store(ResourceLocation origin);

        /** A Xaero waypoint carrying this spec, already stamped with {@code origin}. */
        @Nullable
        Object waypoint(ResourceLocation origin, WaypointSpec spec);

        boolean add(Object store, String key, Object waypoint);

        boolean remove(Object store, String key);

        boolean clear(Object store);

        /** The last call that threw, fingerprinted by member and exception type. */
        Optional<MapBackendStatus.Failure> lastFailure();
    }

    /** {@link Calls} over the resolved manifest. The only part of this class that touches Xaero. */
    private static final class Reflective implements Calls {

        private final MapBinding.Resolution binding;
        private final int purpose;
        private final Map<GuidanceKind, Integer> colours = new EnumMap<>(GuidanceKind.class);

        @Nullable
        private MapBackendStatus.Failure lastFailure;

        private Reflective(MapBinding.Resolution binding) {
            this.binding = binding;
            this.purpose = ordinalOf(binding, PURPOSE_ENUM, PURPOSE_NORMAL);
            for (GuidanceKind kind : GuidanceKind.values()) {
                colours.put(kind, ordinalOf(binding, COLOUR_ENUM, MarkerColours.xaeroColourName(kind)));
            }
        }

        /** A named constant's own ordinal, read off the live enum. Falls back to the first entry. */
        private static int ordinalOf(MapBinding.Resolution binding, Member enumClass, String constant) {
            Object value = binding.enumConstant(enumClass, constant);
            return value instanceof Enum<?> e ? e.ordinal() : FALLBACK_ORDINAL;
        }

        @Override
        @Nullable
        public Object store(ResourceLocation origin) {
            Object session = call(CURRENT_SESSION);
            if (session == null) {
                return null;
            }
            Object waypointsManager = call(WAYPOINTS_MANAGER, session);
            if (waypointsManager == null) {
                return null;
            }
            Object worldManager = call(WORLD_MANAGER_OF, waypointsManager);
            if (worldManager == null) {
                return null;
            }
            Object container = call(ROOT_CONTAINER, worldManager);
            if (container == null) {
                return null;
            }
            Object manager = call(THIRD_PARTY_MANAGER_OF, container);
            return manager == null ? null : call(ORIGIN, manager, origin);
        }

        @Override
        @Nullable
        public Object waypoint(ResourceLocation origin, WaypointSpec spec) {
            BlockPos pos = spec.pos();
            Object waypoint = call(NEW_WAYPOINT, pos.getX(), pos.getY(), pos.getZ(), spec.label(),
                    MarkerColours.initials(spec.kind()),
                    colours.getOrDefault(spec.kind(), FALLBACK_ORDINAL), purpose, false, true);
            if (waypoint == null) {
                return null;
            }
            return invoke(SET_ORIGIN, waypoint, origin) ? waypoint : null;
        }

        @Override
        public boolean add(Object store, String key, Object waypoint) {
            return invoke(ADD, store, key, waypoint);
        }

        @Override
        public boolean remove(Object store, String key) {
            return invoke(REMOVE, store, key);
        }

        @Override
        public boolean clear(Object store) {
            return invoke(CLEAR_ORIGIN, store);
        }

        @Override
        public Optional<MapBackendStatus.Failure> lastFailure() {
            return Optional.ofNullable(lastFailure);
        }

        /** A call whose answer is the value it returned; null covers both "threw" and "returned null". */
        @Nullable
        private Object call(Member member, Object... args) {
            try {
                return binding.handle(member).invokeWithArguments(args);
            } catch (Throwable t) {
                record(member, t);
                return null;
            }
        }

        /** A call whose answer is only whether it completed, which is all a void method can say. */
        private boolean invoke(Member member, Object... args) {
            try {
                binding.handle(member).invokeWithArguments(args);
                return true;
            } catch (Throwable t) {
                record(member, t);
                return false;
            }
        }

        private void record(Member member, Throwable t) {
            lastFailure = new MapBackendStatus.Failure(
                    member.describe() + '/' + t.getClass().getSimpleName(),
                    MapMutationResult.FAILED, Optional.ofNullable(t.getMessage()));
            McaQuests.LOGGER.debug("[MCA: Quests] Xaero {} failed; ignoring",
                    member.describe().toLowerCase(Locale.ROOT), t);
        }
    }
}
