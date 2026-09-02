package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Puts quest destinations onto whatever mapping mods the player has installed.
 *
 * <p>The seam, in the shape {@link TownsteadBridge} already uses: this interface names nothing but
 * vanilla types, the implementations live in {@code compat.map} and are reached by a dotted
 * {@code Class.forName} so no bytecode here mentions them, and {@link Holder} always has a working
 * instance so no call site needs a guard.
 *
 * <p><b>Client-side only.</b> A waypoint is a thing on one player's map; nothing about it belongs on a
 * server, and both supported mods are client mods. The bridge is never set on a dedicated server.
 *
 * <h2>Why the mod does this at all</h2>
 *
 * <p>The in-world marker is a beam with a maximum draw distance measured in hundreds of blocks. A
 * fortress eighteen hundred blocks away is a real answer to "where next" and a beam cannot show it,
 * so for the destinations that most need help the mod's own marker is exactly the one that cannot
 * appear. A minimap can, and in a 1.20.1 MCA pack the player almost certainly has one.
 */
public interface MapWaypointBridge {

    /** Whether any mapping mod bound. False on a dedicated server, and when none is installed. */
    boolean isAvailable();

    /**
     * One line per backend: what bound, and what did not. Reads nothing and writes nothing, so it is
     * safe to log at startup — which is where it is called from.
     */
    List<String> describe();

    /**
     * As {@link #describe()}, but each backend also proves it works: JourneyMap's adds a waypoint,
     * reads it back and removes it.
     *
     * <p>Separate from {@code describe} because it <b>writes</b>, and because it can only give a true
     * answer once the game is running. Both mods can decline a waypoint without throwing and neither
     * says so, which from inside the game is indistinguishable from having no minimap installed at
     * all — that silence is how the world marker shipped looking broken the first time. This is what
     * {@code /mcaquests debug waypoints} runs, on demand, never at startup: at client-setup time
     * JourneyMap has not yet installed the static store its waypoint factory needs, so a probe there
     * would report a failure that is only the clock.
     */
    List<String> probe();

    /**
     * Creates the waypoint {@code key} names, or moves it if it already exists.
     *
     * @param key    stable for the life of one quest's destination, so a quest whose objective
     *               advances moves its waypoint instead of collecting a second one
     * @param kind   what the destination is; each backend turns it into its own colour, because
     *               Xaero has a palette of twenty-one named colours and JourneyMap takes RGB
     */
    void publish(String key, BlockPos pos, ResourceKey<Level> dimension, Component label,
                 GuidanceKind kind);

    /** Removes the waypoint {@code key} names. Silent when there is none. */
    void withdraw(String key);

    /** Removes every waypoint this mod published. */
    void clear();

    /**
     * Drops a waypoint the <em>player</em> owns, which outlives the quest and which they delete
     * themselves.
     *
     * <p>Distinct from {@link #publish} on purpose. The automatic ones belong to the quest: they
     * appear when it can say where to go and vanish when it is done, which is right for a marker and
     * wrong for a place the player has decided is worth remembering. This is the quest log's
     * "add waypoint" button, and nothing takes one of these away.
     *
     * @return whether anything was added, so the caller can tell the player
     */
    boolean pin(BlockPos pos, ResourceKey<Level> dimension, Component label, GuidanceKind kind);

    /** Holds the live bridge; the no-op stands in until a mapping mod binds, and on servers. */
    final class Holder {

        private static volatile MapWaypointBridge instance = NoopMapWaypointBridge.INSTANCE;

        private Holder() {
        }

        public static MapWaypointBridge get() {
            return instance;
        }

        public static void set(MapWaypointBridge bridge) {
            instance = bridge == null ? NoopMapWaypointBridge.INSTANCE : bridge;
        }

        /** Restores the no-op. For tests, and for a client disconnecting from a world. */
        public static void reset() {
            instance = NoopMapWaypointBridge.INSTANCE;
        }
    }
}
