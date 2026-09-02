package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.map.MapBinding.Member;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quest destinations on JourneyMap, bound by name against its jar-in-jar API.
 *
 * <h2>Why there is no {@code @JourneyMapPlugin} class</h2>
 *
 * <p>JourneyMap's documented entry point is a class carrying its annotation and implementing
 * {@code IClientPlugin}, which cannot be done reflectively: an annotation has to be in the class file
 * and an interface has to be in the {@code implements} clause, and both would hard-link this mod to
 * one JourneyMap API version living in a jar that is not on this build's classpath.
 *
 * <p>It turns out not to be needed. {@code journeymap.api.client.impl.ClientAPI} is an enum with a
 * single {@code INSTANCE} constant implementing {@code IClientAPI}, and its {@code addWaypoint(modId,
 * waypoint)} resolves the mod id through a private {@code getPlugin} that <b>creates and caches a
 * wrapper for an id it has never seen</b> rather than rejecting it. So an unregistered mod id is a
 * first-class caller. That is a real behaviour of a real build rather than a documented promise, which
 * is why {@code /mcaquests debug waypoints} round-trips a probe waypoint instead of assuming it worked.
 *
 * <h2>The two traps in the factory</h2>
 *
 * <p>{@code WaypointFactory} has four static {@code createWaypoint} overloads. One four-argument form
 * takes the <em>dimension</em> where the five-argument form takes the <em>name</em>, and the two
 * five-argument forms differ only at index three ({@code String} against {@code ResourceKey}). Binding
 * by arity alone would be a coin flip between a working waypoint and one named after the Nether, so
 * the manifest pins the parameter types.
 *
 * <p>Its static store is installed by JourneyMap's own client bootstrap, so calling it before that has
 * run throws. Nothing here runs before the player is in a world, and every call is guarded anyway.
 */
final class JourneyMapWaypoints {

    /**
     * JourneyMap's package root, stored <em>dotted</em>, never in internal slash form — that is what
     * lets {@code NoJourneyMapStaticLinkTest} byte-scan for slash-form references and treat any hit as
     * a regression, with no exemption for this file.
     */
    private static final String ROOT = "journeymap.";

    /** The interface whose presence identifies an installed, API-bearing JourneyMap. */
    private static final String PROBE = "api.v2.client.IClientAPI";

    private static final String API = "api.v2.client.IClientAPI";
    private static final String IMPL = "api.client.impl.ClientAPI";
    private static final String FACTORY = "api.v2.common.waypoint.WaypointFactory";
    private static final String WAYPOINT = "api.v2.common.waypoint.Waypoint";

    static final Member CLIENT_API_IMPL = Member.cls(IMPL);
    static final Member ADD_WAYPOINT =
            Member.virtual(API, "addWaypoint", void.class, 2, String.class, null);
    static final Member REMOVE_WAYPOINT =
            Member.virtual(API, "removeWaypoint", void.class, 2, String.class, null);
    static final Member REMOVE_ALL_WAYPOINTS =
            Member.virtual(API, "removeAllWaypoints", void.class, 1, String.class);
    static final Member GET_WAYPOINT =
            Member.virtual(API, "getWaypoint", Object.class, 2, String.class, String.class);
    /** {@code (modId, pos, name, dimensionId, persistent)} — see the class javadoc on the overloads. */
    static final Member CREATE_WAYPOINT =
            Member.statik(FACTORY, "createWaypoint", Object.class, 5,
                    String.class, BlockPos.class, String.class, String.class, boolean.class);
    static final Member WAYPOINT_ID = Member.virtual(WAYPOINT, "getId", String.class, 0);
    static final Member SET_COLOR = Member.virtual(WAYPOINT, "setColor", void.class, 1, int.class);
    static final Member SET_BLOCK_POS =
            Member.virtual(WAYPOINT, "setBlockPos", void.class, 1, BlockPos.class);
    static final Member SET_NAME = Member.virtual(WAYPOINT, "setName", void.class, 1, String.class);

    /** Everything the integration needs. Replayed against a real jar by {@code MapBindingProbeTest}. */
    static final List<Member> MANIFEST = List.of(CLIENT_API_IMPL, ADD_WAYPOINT, REMOVE_WAYPOINT,
            REMOVE_ALL_WAYPOINTS, GET_WAYPOINT, CREATE_WAYPOINT, WAYPOINT_ID, SET_COLOR,
            SET_BLOCK_POS, SET_NAME);

    private final MapBinding.Resolution binding;
    @Nullable
    private final Object api;
    private final String modId;
    /** Our key -> the waypoint object we handed JourneyMap, so it can be moved and taken back. */
    private final Map<String, Object> published = new HashMap<>();

    private JourneyMapWaypoints(MapBinding.Resolution binding, @Nullable Object api, String modId) {
        this.binding = binding;
        this.api = api;
        this.modId = modId;
    }

    /** Resolves JourneyMap, or {@code null} when it is absent or nothing usable bound. */
    @Nullable
    static JourneyMapWaypoints resolve(String modId) {
        MapBinding.Resolution binding = MapBinding.resolve("JourneyMap", ROOT, PROBE, MANIFEST,
                JourneyMapWaypoints.class.getClassLoader());
        if (!binding.isBound() && !binding.isPartial()) {
            return null;
        }
        Object api = binding.enumConstant(CLIENT_API_IMPL, "INSTANCE");
        if (api == null) {
            McaQuests.LOGGER.warn("[MCA: Quests] JourneyMap is installed but its client API singleton "
                    + "did not resolve; quest waypoints will not appear on it.");
            return null;
        }
        return new JourneyMapWaypoints(binding, api, modId);
    }

    MapBinding.Resolution binding() {
        return binding;
    }

    boolean isUsable() {
        return api != null && binding.isBound();
    }

    /** Creates the waypoint, or moves and renames the one already standing there. */
    void publish(String key, BlockPos pos, ResourceKey<Level> dimension, Component label,
                 GuidanceKind kind) {
        Object existing = published.get(key);
        if (existing != null) {
            invoke(SET_BLOCK_POS, existing, pos);
            invoke(SET_NAME, existing, label.getString());
            return;
        }
        // Not persistent: this waypoint belongs to the quest, not to the player. A quest destination
        // saved into somebody's waypoint list would outlive the quest and have to be tidied up by hand.
        Object waypoint = create(pos, dimension, label, kind, false);
        if (waypoint == null) {
            return;
        }
        published.put(key, waypoint);
        invoke(ADD_WAYPOINT, api, modId, waypoint);
    }

    void withdraw(String key) {
        Object waypoint = published.remove(key);
        if (waypoint != null) {
            invoke(REMOVE_WAYPOINT, api, modId, waypoint);
        }
    }

    void clear() {
        published.clear();
        invoke(REMOVE_ALL_WAYPOINTS, api, modId);
    }

    /** A waypoint the player keeps. Persistent, and never withdrawn by us. */
    boolean pin(BlockPos pos, ResourceKey<Level> dimension, Component label, GuidanceKind kind) {
        Object waypoint = create(pos, dimension, label, kind, true);
        if (waypoint == null) {
            return false;
        }
        invoke(ADD_WAYPOINT, api, modId, waypoint);
        return true;
    }

    /**
     * Adds a waypoint and reads it back, so a rejected add is visible.
     *
     * <p>{@code addWaypoint} returns nothing and JourneyMap is free to decline one. Without this,
     * "waypoints do not appear" would be indistinguishable from inside the game from "the feature is
     * off" and from "this JourneyMap build changed its API" — which is precisely how the world marker
     * shipped looking broken when it was merely silent.
     */
    String probe() {
        Object waypoint = create(BlockPos.ZERO, Level.OVERWORLD,
                Component.literal("MCA: Quests probe"), GuidanceKind.LOCATION, false);
        if (waypoint == null) {
            return "could not build a waypoint (the factory did not bind, or JourneyMap has not "
                    + "finished starting up)";
        }
        String id = (String) invokeReturning(WAYPOINT_ID, waypoint);
        if (id == null) {
            return "built a waypoint but it reported no id";
        }
        invoke(ADD_WAYPOINT, api, modId, waypoint);
        Object read = invokeReturning(GET_WAYPOINT, api, modId, id);
        invoke(REMOVE_WAYPOINT, api, modId, waypoint);
        return read == null
                ? "JourneyMap accepted a waypoint and then did not know about it (the add was refused)"
                : "round-trip OK";
    }

    @Nullable
    private Object create(BlockPos pos, ResourceKey<Level> dimension, Component label,
                          GuidanceKind kind, boolean persistent) {
        Object waypoint = invokeReturning(CREATE_WAYPOINT, modId, pos, label.getString(),
                dimension.location().toString(), persistent);
        if (waypoint != null) {
            invoke(SET_COLOR, waypoint, dev.otectus.mcaquests.client.marker.MarkerColours.of(kind));
        }
        return waypoint;
    }

    private void invoke(Member member, Object... args) {
        invokeReturning(member, args);
    }

    @Nullable
    private Object invokeReturning(Member member, Object... args) {
        try {
            MethodHandle handle = binding.handle(member);
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] JourneyMap {} failed; ignoring", member.describe(), t);
            return null;
        }
    }
}
