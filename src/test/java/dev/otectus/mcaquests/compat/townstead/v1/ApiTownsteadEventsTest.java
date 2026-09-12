package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent;
import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerCollapsedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerDiedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerLifeStageChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerRecoveredEvent;
import com.aetherianartificer.townstead.api.v1.event.VillagerTierChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The subscription lifecycle: registered once, released completely on failure, and never left half
 * done. Townstead keeps its listener list for the JVM, so this is the whole contract.
 */
class ApiTownsteadEventsTest {

    @AfterEach
    void tearDown() {
        ApiTownsteadEvents.stop();
    }

    @Test
    @DisplayName("subscribes to every event the scans have a signal for, once")
    void subscribesOnce() {
        FakeTownsteadApi api = new FakeTownsteadApi();
        ApiTownsteadEvents.start(api);
        ApiTownsteadEvents.start(api);
        Set<Class<?>> types = api.events.subscriptions.stream().map(r -> (Class<?>) r.type).collect(Collectors.toSet());
        assertEquals(Set.of(VillagerCollapsedEvent.class, VillagerRecoveredEvent.class, VillagerDiedEvent.class,
                VillagerTierChangedEvent.class, VillagerLifeStageChangedEvent.class, VillageSpiritChangedEvent.class,
                BuildingEstablishedEvent.class, BuildingUpgradedEvent.class, CalendarRolloverEvent.class), types);
        assertEquals(9, api.events.subscriptions.size(), "a second start must not subscribe again");
        assertEquals(9, ApiTownsteadEvents.activeSubscriptions());
    }

    @Test
    @DisplayName("releases everything registered so far when a subscription fails, and rethrows")
    void partialFailureLeavesNothingBehind() {
        FakeTownsteadApi api = new FakeTownsteadApi();
        api.events.failOnSubscribe = 4;
        assertThrows(IllegalStateException.class, () -> ApiTownsteadEvents.start(api));
        assertEquals(4, api.events.subscriptions.size());
        assertEquals(0, api.events.active(), "the four that were made must be closed again");
        assertEquals(0, ApiTownsteadEvents.activeSubscriptions());
    }

    @Test
    @DisplayName("stop closes every subscription and is idempotent; start may then run again")
    void stopAndRestart() {
        FakeTownsteadApi api = new FakeTownsteadApi();
        ApiTownsteadEvents.start(api);
        ApiTownsteadEvents.stop();
        ApiTownsteadEvents.stop();
        assertEquals(0, api.events.active());
        ApiTownsteadEvents.start(api);
        assertEquals(9, ApiTownsteadEvents.activeSubscriptions());
    }

    @Test
    @DisplayName("a handler that hits API drift is contained and named; the listener keeps working")
    void driftInAHandlerIsContained() {
        ApiTownsteadBridge.resetDriftForTest();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<VillagerCollapsedEvent> listener = ApiTownsteadEvents.guarded(
                VillagerCollapsedEvent.class, event -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new NoSuchMethodError("VillagerCollapsedEvent.uuid");
                    }
                });
        VillagerCollapsedEvent event = new VillagerCollapsedEvent(null, java.util.UUID.randomUUID(), 0);
        listener.accept(event); // the drift is swallowed
        listener.accept(event); // and the listener is still called afterwards
        assertEquals(2, calls.get());
        assertEquals(java.util.List.of("event VillagerCollapsedEvent"), new ApiTownsteadBridge(new FakeTownsteadApi()).unresolvedMembers());
        ApiTownsteadBridge.resetDriftForTest();
    }

    @Test
    @DisplayName("the bridge wires events on bind and releases them on unbind, but not for an unsupported API generation")
    void bridgeHooks() {
        FakeTownsteadApi api = new FakeTownsteadApi();
        ApiTownsteadBridge bridge = new ApiTownsteadBridge(api);
        bridge.onBound();
        assertEquals(9, ApiTownsteadEvents.activeSubscriptions());
        bridge.onUnbound();
        assertEquals(0, ApiTownsteadEvents.activeSubscriptions());

        FakeTownsteadApi future = new FakeTownsteadApi();
        future.apiVersion = 2;
        new ApiTownsteadBridge(future).onBound();
        assertEquals(0, future.events.subscriptions.size(), "an unknown API generation is not listened to");
    }
}
