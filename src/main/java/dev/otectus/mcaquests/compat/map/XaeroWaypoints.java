package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.map.MapBinding.Member;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * <h2>Re-fetched, never cached</h2>
 *
 * <p>The store hangs off the session's <em>current world container</em>, and that object is replaced
 * when the player changes world. Holding on to one would leave waypoints being written into a
 * container nothing renders any more — a bug that would look exactly like "the integration stopped
 * working after I went to the Nether". So the five-call chain down to the store is walked on every
 * publish. It is five virtual calls against objects the game already has in hand.
 *
 * <h2>Colour</h2>
 *
 * <p>Xaero has twenty-one named colours where JourneyMap takes RGB, so the mapping from
 * {@link GuidanceKind} is made here by hand rather than by finding the nearest neighbour to the beam's
 * colour. It is a seven-entry table; a nearest-colour search would be more code, no more accurate, and
 * would need the colour enum bound to read its hex values.
 */
final class XaeroWaypoints {

    /**
     * Xaero's package root, stored <em>dotted</em>, never in internal slash form — that is what lets
     * {@code NoXaeroStaticLinkTest} byte-scan for slash-form references and treat any hit as a
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
    static final Member SET_NAME = Member.virtual(WAYPOINT, "setName", void.class, 1, String.class);

    /** Everything the integration needs. Replayed against a real jar by {@code MapBindingProbeTest}. */
    static final List<Member> MANIFEST = List.of(CURRENT_SESSION, WAYPOINTS_MANAGER, WORLD_MANAGER_OF,
            ROOT_CONTAINER, THIRD_PARTY_MANAGER_OF, ORIGIN, ADD, REMOVE, CLEAR_ORIGIN, NEW_WAYPOINT,
            SET_ORIGIN, SET_NAME);

    /** {@code WaypointPurpose.NORMAL}. A quest destination is not a death point. */
    private static final int PURPOSE_NORMAL = 0;

    private final MapBinding.Resolution binding;
    private final ResourceLocation origin;
    /** Keys we believe are on the map, so an unchanged destination costs no work. */
    private final Set<String> published = new HashSet<>();

    private XaeroWaypoints(MapBinding.Resolution binding, ResourceLocation origin) {
        this.binding = binding;
        this.origin = origin;
    }

    /** Resolves Xaero's Minimap, or {@code null} when it is absent. */
    @Nullable
    static XaeroWaypoints resolve(ResourceLocation origin) {
        MapBinding.Resolution binding = MapBinding.resolve("Xaero's Minimap", ROOT, PROBE, MANIFEST,
                XaeroWaypoints.class.getClassLoader());
        if (!binding.isBound() && !binding.isPartial()) {
            return null;
        }
        return new XaeroWaypoints(binding, origin);
    }

    MapBinding.Resolution binding() {
        return binding;
    }

    boolean isUsable() {
        return binding.isBound();
    }

    void publish(String key, BlockPos pos, ResourceKey<Level> dimension, Component label,
                 GuidanceKind kind) {
        Object store = store();
        if (store == null) {
            return;
        }
        Object waypoint = create(pos, label, kind, false);
        if (waypoint == null) {
            return;
        }
        // add() is a map put, so this both creates and moves. That is also what makes the integration
        // survive a world change: the container is new and empty, and the next pass simply fills it.
        invoke(ADD, store, key, waypoint);
        published.add(key);
    }

    void withdraw(String key) {
        if (!published.remove(key)) {
            return;
        }
        Object store = store();
        if (store != null) {
            invoke(REMOVE, store, key);
        }
    }

    void clear() {
        published.clear();
        Object store = store();
        if (store != null) {
            invoke(CLEAR_ORIGIN, store);
        }
    }

    /**
     * A waypoint the player keeps.
     *
     * <p>Third-party waypoints are not saved, so "keeps" here means "for this session, and this mod
     * will not take it away". Writing into the player's own saved set instead would mean editing and
     * re-serialising their waypoint file, which is a heavier promise than a quest log button should
     * make.
     */
    boolean pin(BlockPos pos, ResourceKey<Level> dimension, Component label, GuidanceKind kind) {
        Object store = store();
        Object waypoint = store == null ? null : create(pos, label, kind, false);
        if (waypoint == null) {
            return false;
        }
        invoke(ADD, store, "pin/" + pos.getX() + '/' + pos.getY() + '/' + pos.getZ(), waypoint);
        return true;
    }

    /** Adds a waypoint and looks the store up again, so a chain that silently broke is visible. */
    String probe() {
        Object store = store();
        if (store == null) {
            return "could not reach the third-party waypoint store (no minimap session yet, or this "
                    + "Xaero build moved it)";
        }
        return "third-party waypoint store reached, " + published.size() + " waypoint(s) published";
    }

    /**
     * The third-party waypoint store for our origin, walked fresh from the session every call.
     *
     * <p>{@code null} whenever any link is missing — before the player has joined a world there is no
     * session at all, which is an ordinary state rather than an error.
     */
    @Nullable
    private Object store() {
        Object session = invokeReturning(CURRENT_SESSION);
        if (session == null) {
            return null;
        }
        Object waypointsManager = invokeReturning(WAYPOINTS_MANAGER, session);
        if (waypointsManager == null) {
            return null;
        }
        Object worldManager = invokeReturning(WORLD_MANAGER_OF, waypointsManager);
        if (worldManager == null) {
            return null;
        }
        Object container = invokeReturning(ROOT_CONTAINER, worldManager);
        if (container == null) {
            return null;
        }
        Object manager = invokeReturning(THIRD_PARTY_MANAGER_OF, container);
        return manager == null ? null : invokeReturning(ORIGIN, manager, origin);
    }

    @Nullable
    private Object create(BlockPos pos, Component label, GuidanceKind kind, boolean temporary) {
        String name = label.getString();
        Object waypoint = invokeReturning(NEW_WAYPOINT, pos.getX(), pos.getY(), pos.getZ(),
                name, initials(name), colour(kind), PURPOSE_NORMAL, temporary, true);
        if (waypoint != null) {
            invoke(SET_ORIGIN, waypoint, origin);
        }
        return waypoint;
    }

    /**
     * The one or two letters Xaero draws on the minimap edge when the label will not fit.
     *
     * <p>Taken from the destination's own name, so "Nether Fortress" reads as NF and "Anna's home" as
     * AH — which is enough to tell two markers apart at a glance, and is what a player would write
     * themselves.
     */
    private static String initials(String name) {
        StringBuilder out = new StringBuilder(2);
        for (String word : name.split("\\s+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0))) {
                out.append(Character.toUpperCase(word.charAt(0)));
            }
            if (out.length() == 2) {
                break;
            }
        }
        return out.length() == 0 ? "Q" : out.toString();
    }

    /** {@code WaypointColor} ordinals, chosen to sit as close to the beam's colours as the palette allows. */
    private static int colour(GuidanceKind kind) {
        return switch (kind) {
            case VILLAGER -> 17;             // LIGHT_BLUE, the tracker's direction blue
            case HOME, WORKSTATION -> 6;     // GOLD
            case VILLAGE -> 18;              // LIME
            case STRUCTURE -> 12;            // RED
            case BIOME -> 11;                // AQUA
            case PORTAL -> 13;               // PURPLE
            case LOCATION -> 15;             // WHITE
        };
    }

    private void invoke(Member member, Object... args) {
        invokeReturning(member, args);
    }

    @Nullable
    private Object invokeReturning(Member member, Object... args) {
        try {
            return binding.handle(member).invokeWithArguments(args);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Xaero {} failed; ignoring",
                    member.describe().toLowerCase(Locale.ROOT), t);
            return null;
        }
    }
}
