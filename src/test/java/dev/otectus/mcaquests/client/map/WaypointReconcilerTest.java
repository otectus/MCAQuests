package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendCapabilities;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reconciler, against scripted backends.
 *
 * <p>Every case here is a defect the old publish-and-forget sync shipped with: a failed publish
 * recorded as a success, a removal that could never be retried, one backend's failure hiding another's,
 * a kind change that never reached the map, and a dimension change that left the waypoint where it was.
 * They are all decisions this class makes, and none of them needs a game to make.
 */
class WaypointReconcilerTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final MapBackendCapabilities HERE_ONLY =
            new MapBackendCapabilities(true, PinSupport.SESSION, true);

    private final WaypointReconciler reconciler = new WaypointReconciler();

    @Test
    @DisplayName("a new destination is applied once and then left alone")
    void appliesOnceAndDedupes() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        reconcile(backend, desired, 0L);
        SyncReport second = reconcile(backend, desired, 0L);

        assertEquals(Set.of("q1"), backend.appliedKeys());
        assertEquals(MapMutationResult.UNCHANGED, results(second, "journeymap").get("q1"),
                "an unchanged destination must not be re-published");
    }

    @Test
    @DisplayName("a moved destination is re-applied")
    void appliesAMove() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");

        reconcile(backend, List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna")), 0L);
        SyncReport report = reconcile(backend, List.of(spec("q1", 40, GuidanceKind.VILLAGER, "Anna")), 0L);

        assertEquals(MapMutationResult.APPLIED, results(report, "journeymap").get("q1"));
        assertEquals(40, backend.appliedSpecs().get("q1").pos().getX());
    }

    @Test
    @DisplayName("a kind change alone re-applies the waypoint")
    void appliesAKindOnlyChange() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");

        reconcile(backend, List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna")), 0L);
        SyncReport report = reconcile(backend, List.of(spec("q1", 10, GuidanceKind.HOME, "Anna")), 0L);

        // The old Published record held position, dimension and label only, so a destination that
        // became a different kind of place kept the old colour and the old initials for good.
        assertEquals(MapMutationResult.APPLIED, results(report, "journeymap").get("q1"));
        assertEquals(GuidanceKind.HOME, backend.appliedSpecs().get("q1").kind());
    }

    @Test
    @DisplayName("a destination that is no longer wanted is withdrawn")
    void withdrawsWhatIsGone() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");

        reconcile(backend, List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna")), 0L);
        SyncReport report = reconcile(backend, List.of(), 0L);

        assertEquals(MapMutationResult.APPLIED, results(report, "journeymap").get("q1"));
        assertTrue(backend.appliedKeys().isEmpty());
        assertTrue(backend.calls().contains("withdraw:q1"));
    }

    @Test
    @DisplayName("a backend that draws only this dimension never sees another one's coordinates")
    void filtersOtherDimensionsForCurrentDimensionOnlyBackends() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("xaero", HERE_ONLY);
        WaypointSpec here = spec("q1", 10, GuidanceKind.VILLAGER, "Anna");

        reconcile(backend, List.of(here), 0L);
        SyncReport report = reconciler.reconcile(List.of(backend), List.of(here),
                b -> true, Level.NETHER, 1L, 1L, 0L);

        assertEquals(MapMutationResult.SKIPPED_OTHER_DIMENSION, results(report, "xaero").get("q1"));
        assertTrue(backend.appliedKeys().isEmpty(),
                "an overworld waypoint must be taken off a Nether map, not left on it");
    }

    @Test
    @DisplayName("turning the feature off clears what is on the map")
    void clearsADisabledBackend() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap");
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        reconcile(backend, desired, 0L);
        SyncReport report = reconciler.reconcile(List.of(backend), desired, b -> false,
                Level.OVERWORLD, 1L, 1L, 0L);

        assertEquals(List.of(ClearCause.DISABLED), backend.clears());
        assertTrue(backend.appliedKeys().isEmpty());
        assertFalse(report.backends().get(0).enabled());
    }

    @Test
    @DisplayName("one backend failing does not stop the other")
    void isolatesBackends() {
        FakeMapWaypointBackend broken = new FakeMapWaypointBackend("journeymap")
                .scriptApply(MapMutationResult.FAILED);
        FakeMapWaypointBackend working = new FakeMapWaypointBackend("xaero");
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        SyncReport report = reconciler.reconcile(List.of(broken, working), desired, b -> true,
                Level.OVERWORLD, 1L, 1L, 0L);

        assertEquals(MapMutationResult.FAILED, results(report, "journeymap").get("q1"));
        assertEquals(MapMutationResult.APPLIED, results(report, "xaero").get("q1"));
        assertEquals(Set.of("q1"), working.appliedKeys());
    }

    @Test
    @DisplayName("a failed publish is retried rather than remembered as a success")
    void retriesAFailedApply() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap")
                .scriptApply(MapMutationResult.FAILED);
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        reconcile(backend, desired, 0L);
        assertTrue(backend.appliedKeys().isEmpty());

        SyncReport retry = reconcile(backend, desired, 1_000L);

        assertEquals(MapMutationResult.APPLIED, results(retry, "journeymap").get("q1"));
    }

    @Test
    @DisplayName("a failed removal is retried on the next pass")
    void retriesAFailedRemoval() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap")
                .scriptWithdraw(MapMutationResult.FAILED);

        reconcile(backend, List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna")), 0L);
        reconcile(backend, List.of(), 0L);
        assertEquals(Set.of("q1"), backend.appliedKeys(), "a removal that threw removed nothing");

        SyncReport retry = reconcile(backend, List.of(), 1_000L);

        assertEquals(MapMutationResult.APPLIED, results(retry, "journeymap").get("q1"));
        assertTrue(backend.appliedKeys().isEmpty());
    }

    @Test
    @DisplayName("a backend that keeps failing is retried at 1, 2, 4, 8, 16, 32 then 60 seconds")
    void backsOffToOneMinute() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap")
                .scriptApply(MapMutationResult.FAILED, MapMutationResult.FAILED,
                        MapMutationResult.FAILED, MapMutationResult.FAILED,
                        MapMutationResult.FAILED, MapMutationResult.FAILED,
                        MapMutationResult.FAILED, MapMutationResult.FAILED);
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        long now = 0L;
        for (long expected : new long[]{1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L}) {
            SyncReport report = reconcile(backend, desired, now);
            assertEquals(Optional.of(now + expected), report.backends().get(0).nextRetryAtMillis(),
                    "backoff after a failure at " + now);

            // A pass before the retry is due must not touch the map at all.
            int calls = backend.calls().size();
            reconcile(backend, desired, now + expected - 1L);
            assertEquals(calls, backend.calls().size(), "retried early at " + now);
            now += expected;
        }

        SyncReport recovered = reconcile(backend, desired, now);

        assertEquals(MapMutationResult.APPLIED, results(recovered, "journeymap").get("q1"));
        assertEquals(Optional.empty(), recovered.backends().get(0).nextRetryAtMillis(),
                "one success must put the backend back on every pass");
    }

    @Test
    @DisplayName("a world change clears every backend and forgets its backoff")
    void clearAutomaticResetsBackoff() {
        FakeMapWaypointBackend backend = new FakeMapWaypointBackend("journeymap")
                .scriptApply(MapMutationResult.FAILED);
        List<WaypointSpec> desired = List.of(spec("q1", 10, GuidanceKind.VILLAGER, "Anna"));

        reconcile(backend, desired, 0L);
        reconciler.clearAutomatic(List.of(backend), ClearCause.DIMENSION_CHANGE);
        SyncReport report = reconcile(backend, desired, 1L);

        assertEquals(List.of(ClearCause.DIMENSION_CHANGE), backend.clears());
        assertEquals(MapMutationResult.APPLIED, results(report, "journeymap").get("q1"),
                "a failure about a world that no longer exists must not delay the next one");
    }

    private SyncReport reconcile(MapWaypointBackend backend, List<WaypointSpec> desired, long nowMillis) {
        return reconciler.reconcile(List.of(backend), desired, b -> true, Level.OVERWORLD, 1L, 1L,
                nowMillis);
    }

    private static Map<String, MapMutationResult> results(SyncReport report, String backendId) {
        return report.backends().stream()
                .filter(backend -> backend.id().equals(backendId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no report for " + backendId))
                .results();
    }

    private static WaypointSpec spec(String key, int x, GuidanceKind kind, String label) {
        return new WaypointSpec(key, new BlockPos(x, 64, 0), Level.OVERWORLD, label, kind,
                WaypointSpec.Ownership.AUTOMATIC);
    }
}
