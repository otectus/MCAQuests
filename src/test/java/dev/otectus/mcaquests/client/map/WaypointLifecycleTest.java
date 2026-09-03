package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendCapabilities;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The waypoint lifecycle matrix, driven without a game.
 *
 * <p>Every row of it is a defect the previous sync shipped: a dimension change that left the waypoint
 * where it was, a world change that a dimension comparison could not see at all, a logout that left
 * the backend believing in waypoints belonging to a world that no longer existed, and a cleanup that
 * could not tell the player's own pins from the quest's. {@link SyncGate} decides when each of those
 * has happened and {@link WaypointReconciler} carries it out, so between the two of them the whole
 * matrix is testable — the client only ever supplied the level object and the clock.
 */
class WaypointLifecycleTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceKey<Level> NETHER = Level.NETHER;

    private final SyncGate gate = new SyncGate();
    private final WaypointReconciler reconciler = new WaypointReconciler();

    /** Two distinct objects standing in for two {@code ClientLevel}s. Only identity is ever read. */
    private final Object worldA = new Object();
    private final Object worldB = new Object();

    @Test
    @DisplayName("logging in reconciles once, without clearing anything")
    void loginReconciles() {
        SyncGate.Decision decision = gate.evaluate(worldA, Level.OVERWORLD, false, 0L);

        assertTrue(decision.reconcile(), "the first world must publish whatever guidance already arrived");
        assertNull(decision.clear(), "there is nothing to clear before the first world");
    }

    @Test
    @DisplayName("a quiet tick does nothing at all")
    void quietTickIsIdle() {
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);

        SyncGate.Decision decision = gate.evaluate(worldA, Level.OVERWORLD, false, 50L);

        assertFalse(decision.reconcile(), "unchanged guidance in an unchanged world is not worth a pass");
    }

    @Test
    @DisplayName("a dirty flag reconciles, once")
    void dirtyReconcilesOnce() {
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);

        assertTrue(gate.evaluate(worldA, Level.OVERWORLD, true, 50L).reconcile());
        assertFalse(gate.evaluate(worldA, Level.OVERWORLD, false, 100L).reconcile());
    }

    @Test
    @DisplayName("a due retry reconciles without a dirty flag")
    void retryReconciles() {
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);
        gate.retryAt(1_000L);

        assertFalse(gate.evaluate(worldA, Level.OVERWORLD, false, 999L).reconcile());
        assertTrue(gate.evaluate(worldA, Level.OVERWORLD, false, 1_000L).reconcile());
    }

    @Test
    @DisplayName("a dimension change clears the maps and starts a new epoch")
    void dimensionChangeClears() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("xaero", hereOnly());
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);
        assertEquals(Set.of("q1"), backend.appliedKeys());

        SyncGate.Decision decision = gate.evaluate(worldA, NETHER, false, 100L);
        reconciler.clearAutomatic(List.of(backend), decision.clear());

        assertEquals(ClearCause.DIMENSION_CHANGE, decision.clear());
        assertTrue(decision.reconcile());
        assertEquals(2L, decision.worldEpoch(), "the login pass and the dimension change are two epochs");
        assertEquals(Set.of(), backend.appliedKeys());
    }

    @Test
    @DisplayName("a different world with the same dimension key is still a different world")
    void levelIdentityChangeClears() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);

        SyncGate.Decision decision = gate.evaluate(worldB, Level.OVERWORLD, false, 100L);
        reconciler.clearAutomatic(List.of(backend), decision.clear());

        assertEquals(ClearCause.LEVEL_CHANGE, decision.clear(),
                "joining another server puts the player back in minecraft:overworld");
        assertEquals(Set.of(), backend.appliedKeys());
    }

    @Test
    @DisplayName("logging out clears the maps and forgets the world")
    void logoutClearsAndResets() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);

        reconciler.clearAutomatic(List.of(backend), ClearCause.LOGOUT);
        backend.resetEpoch();
        gate.reset();

        assertEquals(Set.of(), backend.appliedKeys());
        assertEquals(List.of(ClearCause.LOGOUT), backend.clears());
        SyncGate.Decision rejoin = gate.evaluate(worldA, Level.OVERWORLD, false, 200L);
        assertNull(rejoin.clear(),
                "after a logout the next world is a first world, not a change of world");
        assertTrue(rejoin.reconcile());
    }

    @Test
    @DisplayName("a clone clears the maps and asks for a fresh pass")
    void cloneClearsAndRepublishes() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        gate.evaluate(worldA, Level.OVERWORLD, false, 0L);
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);

        reconciler.clearAutomatic(List.of(backend), ClearCause.CLONE);
        long epoch = gate.nextEpoch();
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);

        assertEquals(2L, epoch, "the login pass and the clone are two epochs");
        assertEquals(Set.of("q1"), backend.appliedKeys(),
                "the destinations come back for the quests that are still active");
        assertTrue(backend.clears().contains(ClearCause.CLONE));
    }

    @Test
    @DisplayName("an empty desired set withdraws everything")
    void emptyDesiredSetWithdraws() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD)), Level.OVERWORLD);

        reconcile(backend, List.of(), Level.OVERWORLD);

        assertEquals(Set.of(), backend.appliedKeys(), "abandoning the last quest takes its point away");
        assertTrue(backend.calls().contains("withdraw:q1"));
    }

    @Test
    @DisplayName("clearing the automatic points leaves the player's pins alone")
    void pinsSurviveTheAutomaticClear() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        WaypointSpec pin = new WaypointSpec("q1/pin", new BlockPos(1, 2, 3), Level.OVERWORLD,
                "Anna", GuidanceKind.VILLAGER, WaypointSpec.Ownership.PIN);
        backend.pin(pin);
        reconcile(backend, List.of(spec("q1", Level.OVERWORLD), pin), Level.OVERWORLD);

        reconciler.clearAutomatic(List.of(backend), ClearCause.LOGOUT);

        assertEquals(List.of(pin), backend.pins(), "a pin is the player's, and nothing here removes one");
        assertFalse(backend.calls().contains("apply:q1/pin"),
                "a pin is never published as an automatic waypoint either");
    }

    private void reconcile(MapWaypointBackend backend, List<WaypointSpec> desired,
                           ResourceKey<Level> dimension) {
        reconciler.reconcile(List.of(backend), desired, b -> true, dimension, gate.worldEpoch(), 0L, 0L);
    }

    private static MapBackendCapabilities hereOnly() {
        return new MapBackendCapabilities(true, PinSupport.SESSION, true);
    }

    private static WaypointSpec spec(String key, ResourceKey<Level> dimension) {
        return new WaypointSpec(key, new BlockPos(10, 64, 10), dimension, "Anna",
                GuidanceKind.VILLAGER, WaypointSpec.Ownership.AUTOMATIC);
    }
}
