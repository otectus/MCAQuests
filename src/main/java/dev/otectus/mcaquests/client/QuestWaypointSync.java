package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.map.ClientMapWaypointRegistry;
import dev.otectus.mcaquests.client.map.MapSyncDirtyFlag;
import dev.otectus.mcaquests.client.map.SyncGate;
import dev.otectus.mcaquests.client.map.SyncReport;
import dev.otectus.mcaquests.client.map.WaypointReconciler;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the player's minimap in step with where their quests are sending them.
 *
 * <p>One waypoint per active quest that has somewhere to point, created when it resolves, moved when
 * the quest advances to its next objective, and taken away when the quest ends. The in-world marker
 * stays exactly one, for the quest the server marked primary; the map is where <em>all</em> of them can
 * be seen at once without the world filling up with beacons.
 *
 * <h2>What this class decides, and what it does not</h2>
 *
 * <p>It decides what <em>should</em> be on the map. When that is worth asking at all is
 * {@link SyncGate}'s answer; applying it, retrying it and reporting on it belongs to
 * {@link WaypointReconciler}, which is pure and testable; the backends own what they have actually
 * managed to publish. In particular, a destination in another dimension is no longer filtered out
 * here: JourneyMap can show one in its true dimension and Xaero cannot, so the question is answered
 * per backend by {@code MapBackendCapabilities.currentDimensionOnly()}.
 *
 * <h2>The quiet tick allocates nothing</h2>
 *
 * <p>Guidance changes when a packet arrives, when the player changes world, or when they change a
 * setting — never on its own, and certainly not twenty times a second. So the tick reads a flag and a
 * clock and returns; the desired set is built only on the ticks where one of those three things
 * actually happened, or where a failing backend has asked to be tried again.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestWaypointSync {

    private static final WaypointReconciler RECONCILER = new WaypointReconciler();
    private static final SyncGate GATE = new SyncGate();

    private static SyncReport lastReport = SyncReport.empty();
    /** Wall clock of the last pass that no backend failed, or 0 when there has not been one. */
    private static long lastSyncMillis;

    private QuestWaypointSync() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientMapWaypointRegistry.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        // Consumed on every tick rather than only on the ticks that reconcile: a change that arrives
        // in the same tick as a world change must not be left set for the next world.
        boolean dirty = MapSyncDirtyFlag.consume();
        long now = System.currentTimeMillis();
        ResourceKey<Level> dimension = level.dimension();
        SyncGate.Decision decision = GATE.evaluate(level, dimension, dirty, now);
        if (decision.clear() != null) {
            RECONCILER.clearAutomatic(ClientMapWaypointRegistry.backends(), decision.clear());
        }
        if (!decision.reconcile()) {
            return;
        }
        lastReport = RECONCILER.reconcile(ClientMapWaypointRegistry.backends(), desired(),
                QuestWaypointSync::enabled, dimension, decision.worldEpoch(),
                ClientGuidanceData.revision(), now);
        long retryAt = 0L;
        boolean failed = false;
        for (SyncReport.BackendReport report : lastReport.backends()) {
            if (report.nextRetryAtMillis().isPresent()) {
                failed = true;
                long next = report.nextRetryAtMillis().get();
                retryAt = retryAt == 0L ? next : Math.min(retryAt, next);
            }
        }
        GATE.retryAt(retryAt);
        if (!failed) {
            lastSyncMillis = now;
        }
    }

    /**
     * Whether one backend may be reconciled at all.
     *
     * <p>{@code mapWaypoints} is the master switch and the per-backend keys refine it, so a player with
     * both mods can put quest destinations on the one they actually read. A backend turned off here is
     * cleared by the reconciler rather than merely skipped, which is what makes the toggle take effect
     * the moment the config is saved.
     */
    private static boolean enabled(MapWaypointBackend backend) {
        if (!McaQuestsConfig.CLIENT.mapWaypoints.get()) {
            return false;
        }
        return switch (backend.id()) {
            case "journeymap" -> McaQuestsConfig.CLIENT.journeyMapWaypoints.get();
            case "xaero" -> McaQuestsConfig.CLIENT.xaeroWaypoints.get();
            default -> true;
        };
    }

    /** Every destination worth a waypoint right now, in the shape the map layer compares. */
    private static List<WaypointSpec> desired() {
        List<ActiveGuidance> source = McaQuestsConfig.CLIENT.mapWaypointsFollowedOnly.get()
                ? ClientGuidanceData.primary().map(List::of).orElseGet(List::of)
                : ClientGuidanceData.all();
        List<WaypointSpec> desired = new ArrayList<>(source.size());
        for (ActiveGuidance guidance : source) {
            GuidanceTarget target = guidance.target();
            desired.add(new WaypointSpec(key(guidance), target.pos(), target.dimension(),
                    target.label().getString(), target.kind(), WaypointSpec.Ownership.AUTOMATIC));
        }
        return desired;
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

    /** The last pass, for the client waypoint diagnostic. */
    public static SyncReport lastReport() {
        return lastReport;
    }

    /** When the last pass finished with nothing failing, or 0 when none has. */
    public static long lastSyncMillis() {
        return lastSyncMillis;
    }

    /** Asks for a reconcile on the next tick — for the config listener and the client command. */
    public static void markDirty() {
        MapSyncDirtyFlag.set();
    }

    /**
     * A world is about to begin.
     *
     * <p>Nothing is cleared here: there is no world yet to clear anything from, and the backends were
     * already told the previous one had ended. The epoch moves so a report cannot be mistaken for one
     * about the world before it.
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        GATE.nextEpoch();
        MapSyncDirtyFlag.set();
    }

    /**
     * The player object was replaced — a respawn, or a dimension change carried out by the server.
     *
     * <p>The waypoints go and come back rather than being left in place, because the snapshot that
     * follows a clone is recomputed from scratch on the server and this is the one moment where the
     * client's idea of the desired set is guaranteed stale.
     */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        RECONCILER.clearAutomatic(ClientMapWaypointRegistry.backends(), ClearCause.CLONE);
        GATE.nextEpoch();
        MapSyncDirtyFlag.set();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RECONCILER.clearAutomatic(ClientMapWaypointRegistry.backends(), ClearCause.LOGOUT);
        ClientMapWaypointRegistry.backends().forEach(MapWaypointBackend::resetEpoch);
        GATE.reset();
        lastReport = SyncReport.empty();
        lastSyncMillis = 0L;
        MapSyncDirtyFlag.set();
    }
}
