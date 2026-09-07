package dev.otectus.mcaquests.compat.mapatlases;

import dev.otectus.mcaquests.compat.*;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasProjectionTest {
    static { TestBootstrap.ensureBootstrapped(); }
    @Test void everyScaleAndNegativeSeamHasExactlyOneOwner() {
        for (int scale=0;scale<=4;scale++) for (int cx : new int[]{-100000, -64, 0, 100000}) {
            int span=128<<scale, half=64<<scale;
            for (double delta : new double[]{-.5,0,.5}) {
                double x=cx+half+delta;
                boolean a=AtlasProjection.owns(AtlasProjection.project(x,0,cx,0,scale));
                boolean b=AtlasProjection.owns(AtlasProjection.project(x,0,cx+span,0,scale));
                assertNotEquals(a,b,"exactly one owner at the X seam");
                a=AtlasProjection.owns(AtlasProjection.project(0,x,0,cx,scale));
                b=AtlasProjection.owns(AtlasProjection.project(0,x,0,cx+span,scale));
                assertNotEquals(a,b,"exactly one owner at the Z seam");
            }
            assertEquals(new AtlasProjection.Point(64,64),AtlasProjection.project(cx,cx,cx,cx,scale));
        }
    }
    @Test void cornerBelongsOnlyToThePositiveTile() {
        int owners=0;
        for(int x:new int[]{0,128}) for(int z:new int[]{0,128})
            if(AtlasProjection.owns(AtlasProjection.project(64,64,x,z,0))) owners++;
        assertEquals(1,owners);
    }
    @Test void subtractionUsesWideArithmetic() {
        var point=AtlasProjection.project(Integer.MAX_VALUE, Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,0);
        assertEquals(64+4294967295d,point.x()); assertEquals(64-4294967295d,point.y());
    }
    @Test void strictSliceUsesExplicitProvenanceAndInclusiveTolerance() {
        var spec=new WaypointSpec("q",new BlockPos(0,72,0),Level.NETHER,"",GuidanceKind.LOCATION,
                WaypointSpec.Ownership.AUTOMATIC,new WaypointPresentation(false,false,3,false,true,"",false));
        assertEquals("ready",AtlasProjection.sliceReason(spec,64,true,8));
        assertEquals("slice",AtlasProjection.sliceReason(spec,63,true,8));
        assertEquals("ready",AtlasProjection.sliceReason(spec,null,true,8));
        var uncertain=new WaypointSpec("q",spec.pos(),Level.NETHER,"",GuidanceKind.LOCATION,WaypointSpec.Ownership.AUTOMATIC);
        assertEquals("uncertain_height",AtlasProjection.sliceReason(uncertain,64,true,8));
        assertEquals("ready",AtlasProjection.sliceReason(uncertain,64,false,8));
    }
    @Test void rimUsesActualViewportForCardinalsDiagonalsAndRotations() {
        var viewport=new AtlasProjection.Rect(10,20,110,120);
        for(int degrees=0;degrees<360;degrees+=15) {
            double angle=Math.toRadians(degrees),dx=1000*Math.cos(angle),dy=1000*Math.sin(angle);
            var point=AtlasProjection.rim(new AtlasProjection.Point(60+dx,70+dy),viewport,5);
            assertTrue(viewport.contains(point.x(),point.y()));
            assertTrue(Math.abs(Math.abs(point.x()-60)-45)<1e-6 || Math.abs(Math.abs(point.y()-70)-45)<1e-6);
            assertEquals(0, (point.x()-60)*dy-(point.y()-70)*dx,1e-6);
        }
    }
    @Test void clippedHitRegionNeverExtendsOutsideViewport() {
        var hit=new AtlasProjection.Rect(-4,-4,8,8).intersect(new AtlasProjection.Rect(0,0,100,100));
        assertFalse(hit.contains(-1,2)); assertTrue(hit.contains(2,2)); assertFalse(hit.contains(8,2));
    }
}
