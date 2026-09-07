package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.*;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapPinRoutingTest {
    static { TestBootstrap.ensureBootstrapped(); }
    private static final WaypointSpec PIN=new WaypointSpec("pin",BlockPos.ZERO,Level.NETHER,"A",GuidanceKind.LOCATION,WaypointSpec.Ownership.PIN);
    @Test void queryingDoesNotWriteAndSelectingOneDoesNotFanOut() {
        var jm=new FakeMapWaypointBackend("journeymap"); var atlas=new FakeMapWaypointBackend("map_atlases");
        var all=List.<MapWaypointBackend>of(jm,atlas);
        assertEquals(2,MapPinRouting.eligible(all,PIN,Level.OVERWORLD).size());
        assertTrue(jm.pins().isEmpty()); assertTrue(atlas.pins().isEmpty());
        assertTrue(MapPinRouting.save(all,Set.of(),PIN,Level.OVERWORLD).isEmpty());
        var results=MapPinRouting.save(all,Set.of("map_atlases"),PIN,Level.OVERWORLD);
        assertEquals(Map.of("map_atlases",MapMutationResult.APPLIED),results);
        assertTrue(jm.pins().isEmpty()); assertEquals(1,atlas.pins().size());
    }
    @Test void crossDimensionAndUnusableMapsDoNotSuppressOtherChoices() {
        var xaero=new FakeMapWaypointBackend("xaero",new MapBackendCapabilities(true,PinSupport.SESSION,true));
        var atlas=new FakeMapWaypointBackend("map_atlases").usable(false);
        var jm=new FakeMapWaypointBackend("journeymap");
        var all=List.<MapWaypointBackend>of(xaero,atlas,jm);
        assertEquals(List.of(jm),MapPinRouting.eligible(all,PIN,Level.OVERWORLD));
        var results=MapPinRouting.save(all,Set.of("journeymap","map_atlases","xaero"),PIN,Level.OVERWORLD);
        assertEquals(MapMutationResult.APPLIED,results.get("journeymap"));
        assertEquals(MapMutationResult.UNSUPPORTED,results.get("map_atlases"));
        assertEquals(MapMutationResult.UNSUPPORTED,results.get("xaero"));
    }
}
