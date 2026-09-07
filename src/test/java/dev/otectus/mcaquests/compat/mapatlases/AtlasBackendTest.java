package dev.otectus.mcaquests.compat.mapatlases;

import dev.otectus.mcaquests.compat.*;
import dev.otectus.mcaquests.client.map.WaypointReconciler;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtlasBackendTest {
    static { TestBootstrap.ensureBootstrapped(); }
    @BeforeEach void hooks() { AtlasHookState.preflightPassed(); AtlasHookState.appliedSuccessfully(); }
    @AfterEach void resetHooks() { AtlasHookState.failed("test complete"); }
    static WaypointSpec point(String key,int x,boolean primary) {
        return new WaypointSpec(key,new BlockPos(x,64,0),Level.NETHER,"Destination",GuidanceKind.VILLAGER,
                WaypointSpec.Ownership.AUTOMATIC,new WaypointPresentation(false,false,3,primary,true,"Title",false));
    }
    @Test void atlasAloneAcceptsUnmappedOtherDimensionWithoutRetriesAndMovesOneIdentity() {
        var store=new AtlasMarkerStore(); var atlas=new MapAtlasesWaypointBackend(store,"test",null);
        var reconciler=new WaypointReconciler();
        var report=reconciler.reconcile(List.of(atlas),List.of(point("q/giver1",10,true),point("q/giver2",20,false)),
                b->true,Level.OVERWORLD,1,1,0);
        assertEquals(2,atlas.appliedKeys().size()); assertTrue(report.backends().get(0).nextRetryAtMillis().isEmpty());
        var old=store.snapshot();
        atlas.apply(point("q/giver1",300,true));
        assertEquals(2,store.keys().size()); assertEquals(10,old.all().get(0).pos().getX());
        assertEquals(300,store.snapshot().all().get(0).pos().getX());
        reconciler.reconcile(List.of(atlas),List.of(),b->true,Level.OVERWORLD,1,2,100);
        assertTrue(atlas.appliedKeys().isEmpty());
    }
    @Test void presentationChangesParticipateInReconciliationAndSnapshotIsolation() {
        var store=new AtlasMarkerStore(); var first=point("q/a",0,false);
        assertTrue(store.put(first)); var snapshot=store.snapshot();
        assertTrue(store.put(point("q/a",0,true)));
        assertFalse(snapshot.all().get(0).presentation().primary());
        assertTrue(store.snapshot().all().get(0).presentation().primary());
        assertThrows(UnsupportedOperationException.class,()->snapshot.all().clear());
        assertFalse(store.put(point("q/a",0,true)));
    }
    @Test void cleanupInvalidatesFramesAndPinsCannotEnterAutomaticStore() {
        var store=new AtlasMarkerStore(); store.put(point("q/a",0,true));
        long epoch=store.snapshot().epoch();
        var pin=new WaypointSpec("player_pin",BlockPos.ZERO,Level.OVERWORLD,"",GuidanceKind.LOCATION,WaypointSpec.Ownership.PIN);
        assertThrows(IllegalArgumentException.class,()->store.put(pin));
        var atlas=new MapAtlasesWaypointBackend(store,"test",null); atlas.resetEpoch();
        assertTrue(store.snapshot().epoch()>epoch); assertTrue(store.keys().isEmpty());
    }
    @Test void indexSeparatesRealDimensionsAndSortsPrimaryFirst() {
        var store=new AtlasMarkerStore(); store.put(point("z",0,true)); store.put(point("a",1,false));
        assertTrue(store.snapshot().near(Level.OVERWORLD,0,0,4).isEmpty());
        assertEquals(List.of("z","a"),store.snapshot().near(Level.NETHER,0,0,4).stream().map(WaypointSpec::key).toList());
    }
    @Test void unsupportedHooksRejectAutomaticStateWithoutCrashing() {
        AtlasHookState.failed("unsupported fixture");
        var store=new AtlasMarkerStore(); var atlas=new MapAtlasesWaypointBackend(store,"unknown",null);
        assertEquals(MapMutationResult.UNSUPPORTED,atlas.apply(point("q",0,true))); assertTrue(store.keys().isEmpty());
    }
}
