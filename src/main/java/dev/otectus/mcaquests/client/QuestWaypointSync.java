package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.MapWaypointBridge;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the player's minimap in step with where their quests are sending them.
 *
 * <p>One waypoint per active quest that has somewhere to point, created when it resolves, moved when
 * the quest advances to its next objective, and taken away when the quest ends. The in-world marker
 * beam stays exactly one, for the quest the server marked primary; the map is where <em>all</em> of
 * them can be seen at once without the world filling up with beacons.
 *
 * <h2>Only this dimension</h2>
 *
 * <p>A destination in another dimension is not published, for the reason the marker is not drawn
 * there: the Nether's coordinates are the overworld's divided by eight, so a waypoint at the raw
 * number would sit somewhere the player has no reason to go. The tracker still names the place, the
 * dimension and the coordinates in words, which is the honest form of that answer.
 *
 * <h2>Diffed, and re-published when the world changes</h2>
 *
 * <p>The server only sends guidance when it changes, so in the steady state this compares a handful of
 * records and does nothing. The one thing it cannot detect by comparison is the player changing world:
 * Xaero's third-party waypoint store hangs off the current world container and is replaced along with
 * it, so what was published into the old one is simply gone. Watching the client level and starting
 * over is what stops the integration reading as "it stopped working after I went to the Nether".
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestWaypointSync {

    /** What we last published for a key, so an unchanged destination costs nothing. */
    private record Published(BlockPos pos, ResourceKey<Level> dimension, String label) {
    }

    private static final Map<String, Published> PUBLISHED = new HashMap<>();

    /** The level we published against, so a world change starts over instead of drifting. */
    private static ResourceKey<Level> publishedDimension;

    private QuestWaypointSync() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        MapWaypointBridge bridge = MapWaypointBridge.Holder.get();
        if (!bridge.isAvailable()) {
            return;
        }
        if (!McaQuestsConfig.CLIENT.mapWaypoints.get()) {
            if (!PUBLISHED.isEmpty()) {
                forget(bridge);
            }
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (!dimension.equals(publishedDimension)) {
            // A new world: whatever we published lives in a container nothing renders any more.
            PUBLISHED.clear();
            publishedDimension = dimension;
        }
        sync(bridge, wanted(dimension));
    }

    /** Every destination worth a waypoint right now, keyed the way the map files them. */
    private static Map<String, Published> wanted(ResourceKey<Level> dimension) {
        List<ActiveGuidance> source = McaQuestsConfig.CLIENT.mapWaypointsFollowedOnly.get()
                ? ClientGuidanceData.primary().map(List::of).orElseGet(List::of)
                : ClientGuidanceData.all();
        Map<String, Published> wanted = new HashMap<>();
        for (ActiveGuidance guidance : source) {
            GuidanceTarget target = guidance.target();
            if (!dimension.equals(target.dimension())) {
                continue;
            }
            wanted.put(key(guidance), new Published(target.pos(), target.dimension(),
                    target.label().getString()));
        }
        return wanted;
    }

    private static void sync(MapWaypointBridge bridge, Map<String, Published> wanted) {
        Set<String> stale = new HashSet<>(PUBLISHED.keySet());
        stale.removeAll(wanted.keySet());
        for (String key : stale) {
            bridge.withdraw(key);
            PUBLISHED.remove(key);
        }
        for (Map.Entry<String, Published> entry : wanted.entrySet()) {
            if (entry.getValue().equals(PUBLISHED.get(entry.getKey()))) {
                continue;
            }
            Optional<ActiveGuidance> guidance = find(entry.getKey());
            if (guidance.isEmpty()) {
                continue;
            }
            GuidanceTarget target = guidance.get().target();
            bridge.publish(entry.getKey(), target.pos(), target.dimension(), target.label(),
                    target.kind());
            PUBLISHED.put(entry.getKey(), entry.getValue());
        }
    }

    private static Optional<ActiveGuidance> find(String key) {
        for (ActiveGuidance guidance : new ArrayList<>(ClientGuidanceData.all())) {
            if (key.equals(key(guidance))) {
                return Optional.of(guidance);
            }
        }
        return Optional.empty();
    }

    /**
     * The waypoint id for one quest.
     *
     * <p>Quest id <em>and</em> giver, like {@code QuestLogEntry} and {@code ActiveGuidance}, because
     * the same quest can be active from two different villagers and two waypoints stacked at one id
     * would leave one of them unremovable.
     */
    private static String key(ActiveGuidance guidance) {
        return guidance.questId() + "/" + guidance.villagerUuid();
    }

    /** Takes back every waypoint this mod published and forgets them. */
    private static void forget(MapWaypointBridge bridge) {
        bridge.clear();
        PUBLISHED.clear();
        publishedDimension = null;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget(MapWaypointBridge.Holder.get());
    }
}
