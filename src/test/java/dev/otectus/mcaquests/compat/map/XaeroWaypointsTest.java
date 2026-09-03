package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.compat.BindingState;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Xaero backend's bookkeeping, against a store double.
 *
 * <p>What is testable here is everything above the reflection: when applied state is committed, when
 * it is discarded, and which origin a call lands in. The reflective half — that
 * {@code xaero.hud.minimap.waypoint.WaypointColor} exists and that {@code LIGHT_BLUE} is one of its
 * constants — cannot be proved without a real Xaero jar, so it is proved by
 * {@code MapBindingProbeTest} instead, and the {@link XaeroWaypoints.Calls} seam is where the two
 * halves meet.
 */
class XaeroWaypointsTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUESTS = ResourceLocation.fromNamespaceAndPath("mcaquests", "quests");
    private static final ResourceLocation PINS = ResourceLocation.fromNamespaceAndPath("mcaquests", "pins");

    @Test
    @DisplayName("Xaero's pins are session-only and its waypoints are this-dimension-only")
    void declaresWhatXaeroCanActuallyDo() {
        XaeroWaypoints backend = backend(new FakeCalls());

        // Both matter to the player: the quest log offers a session waypoint rather than claiming a
        // permanent one, and the reconciler withholds every other dimension's coordinates.
        assertEquals(PinSupport.SESSION, backend.capabilities().pins());
        assertTrue(backend.capabilities().currentDimensionOnly());
        assertTrue(backend.capabilities().automaticWaypoints());
    }

    @Test
    @DisplayName("no session yet is a retry, not a failure")
    void retriesWithoutASession() {
        FakeCalls calls = new FakeCalls();
        calls.store = null;
        XaeroWaypoints backend = backend(calls);

        assertEquals(MapMutationResult.RETRY_LATER, backend.apply(spec("q1", GuidanceKind.VILLAGER)));
        assertEquals(BindingState.NOT_READY, backend.status().binding());
    }

    @Test
    @DisplayName("an applied waypoint is recorded once and not re-added")
    void appliesOnce() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);
        WaypointSpec spec = spec("q1", GuidanceKind.VILLAGER);

        assertEquals(MapMutationResult.APPLIED, backend.apply(spec));
        assertEquals(MapMutationResult.UNCHANGED, backend.apply(spec));
        assertEquals(Set.of("q1"), backend.appliedKeys());
        assertEquals(List.of("add:" + QUESTS + ":q1"), calls.log);
    }

    @Test
    @DisplayName("an add that throws commits nothing")
    void doesNotCommitAfterAFailedAdd() {
        FakeCalls calls = new FakeCalls();
        calls.addSucceeds = false;
        XaeroWaypoints backend = backend(calls);

        // The defect this replaces: the old code added the key to its own set either side of a call
        // whose success it could not know, so a waypoint that never arrived never arrived again.
        assertEquals(MapMutationResult.FAILED, backend.apply(spec("q1", GuidanceKind.VILLAGER)));
        assertTrue(backend.appliedKeys().isEmpty());
    }

    @Test
    @DisplayName("a remove that throws keeps the waypoint, so the removal is retried")
    void doesNotForgetAfterAFailedRemove() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));

        calls.removeSucceeds = false;
        assertEquals(MapMutationResult.FAILED, backend.withdraw("q1"));
        assertEquals(Set.of("q1"), backend.appliedKeys());

        calls.removeSucceeds = true;
        assertEquals(MapMutationResult.APPLIED, backend.withdraw("q1"));
        assertTrue(backend.appliedKeys().isEmpty());
    }

    @Test
    @DisplayName("withdrawing something we never published is free")
    void withdrawingAnUnknownKeyIsUnchanged() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);

        assertEquals(MapMutationResult.UNCHANGED, backend.withdraw("q1"));
        assertEquals(List.of(), calls.log);
    }

    @Test
    @DisplayName("a new store means a new world, so applied state is discarded")
    void dropsAppliedStateWhenTheStoreIsReplaced() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);
        WaypointSpec spec = spec("q1", GuidanceKind.VILLAGER);
        backend.apply(spec);

        // Xaero replaces the whole world container on a world change; what was published went with it.
        calls.store = new Object();
        assertEquals(MapMutationResult.APPLIED, backend.apply(spec),
                "the same spec must be re-published into a store that has never seen it");
        assertEquals(2, calls.log.size());
    }

    @Test
    @DisplayName("clearing empties the quest origin and only the quest origin")
    void clearAutomaticEmptiesTheQuestOrigin() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));
        backend.pin(spec("q1", GuidanceKind.VILLAGER));

        backend.clearAutomatic(ClearCause.DIMENSION_CHANGE);

        assertTrue(backend.appliedKeys().isEmpty());
        assertEquals(List.of("clear:" + QUESTS), calls.log.subList(2, calls.log.size()));
    }

    @Test
    @DisplayName("a pin lands in the pin origin under a key that names its dimension")
    void pinsAreKeyedByDimension() {
        FakeCalls calls = new FakeCalls();
        XaeroWaypoints backend = backend(calls);

        assertEquals(MapMutationResult.APPLIED, backend.pin(spec("q1", GuidanceKind.VILLAGER)));

        // Coordinates alone collided across dimensions: the same block in the Nether was the same pin.
        assertEquals(List.of("add:" + PINS + ":pin/minecraft:overworld/10/64/0"), calls.log);
    }

    @Test
    @DisplayName("a failure is reported with a fingerprint rather than a sentence")
    void reportsTheLastFailure() {
        FakeCalls calls = new FakeCalls();
        calls.addSucceeds = false;
        XaeroWaypoints backend = backend(calls);
        backend.apply(spec("q1", GuidanceKind.VILLAGER));

        Optional<MapBackendStatus.Failure> failure = backend.status().lastFailure();

        assertTrue(failure.isPresent());
        assertEquals(MapMutationResult.FAILED, failure.get().result());
    }

    private static XaeroWaypoints backend(XaeroWaypoints.Calls calls) {
        return new XaeroWaypoints(BindingState.BOUND, List.of(), calls);
    }

    private static WaypointSpec spec(String key, GuidanceKind kind) {
        return new WaypointSpec(key, new BlockPos(10, 64, 0), Level.OVERWORLD, "Anna", kind,
                WaypointSpec.Ownership.AUTOMATIC);
    }

    /** A third-party waypoint store that records what it was asked to do, and can be told to fail. */
    private static final class FakeCalls implements XaeroWaypoints.Calls {

        /** Stands in for Xaero's {@code Waypoint}; carries the origin so the log can name it. */
        private record FakePoint(ResourceLocation origin, String key) {
        }

        private final List<String> log = new ArrayList<>();

        @Nullable
        private Object store = new Object();
        private boolean addSucceeds = true;
        private boolean removeSucceeds = true;

        @Nullable
        private MapBackendStatus.Failure lastFailure;

        @Override
        @Nullable
        public Object store(ResourceLocation origin) {
            return store;
        }

        @Override
        public Object waypoint(ResourceLocation origin, WaypointSpec spec) {
            return new FakePoint(origin, spec.key());
        }

        @Override
        public boolean add(Object store, String key, Object waypoint) {
            log.add("add:" + ((FakePoint) waypoint).origin() + ":" + key);
            return addSucceeds || fail("add");
        }

        @Override
        public boolean remove(Object store, String key) {
            log.add("remove:" + key);
            return removeSucceeds || fail("remove");
        }

        @Override
        public boolean clear(Object store) {
            log.add("clear:" + QUESTS);
            return true;
        }

        @Override
        public Optional<MapBackendStatus.Failure> lastFailure() {
            return Optional.ofNullable(lastFailure);
        }

        private boolean fail(String member) {
            lastFailure = new MapBackendStatus.Failure(member + "/RuntimeException",
                    MapMutationResult.FAILED, Optional.of("scripted"));
            return false;
        }
    }
}
