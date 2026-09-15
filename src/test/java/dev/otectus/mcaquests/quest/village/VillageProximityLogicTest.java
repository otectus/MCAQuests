package dev.otectus.mcaquests.quest.village;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure geometry behind the escort village gate. Everything here is arithmetic over bounding boxes,
 * so it needs no level, no server and no registry — the three world-reading sources in
 * {@link VillageProximity#check} are exercised in-game, not here.
 */
class VillageProximityLogicTest {

    static {
        // BoundingBox's codec field reaches vanilla state that asserts the game is bootstrapped; see
        // the helper's Javadoc for why the real Bootstrap.bootStrap() cannot run in these tests.
        TestBootstrap.ensureBootstrapped();
    }

    /** A 100x100 village footprint sitting at ground level. */
    private static final BoundingBox VILLAGE =
            BoundingBox.fromCorners(new Vec3i(0, 60, 0), new Vec3i(100, 70, 100));

    @Test
    void insideTheFootprintIsZeroWhateverTheHeight() {
        assertEquals(0.0D, VillageProximity.horizontalDistance(new BlockPos(50, 64, 50), VILLAGE));
        // Y is ignored on purpose: a villager on a mountain above the village is still in the village.
        assertEquals(0.0D, VillageProximity.horizontalDistance(new BlockPos(50, 250, 50), VILLAGE));
        assertEquals(0.0D, VillageProximity.horizontalDistance(new BlockPos(0, -40, 100), VILLAGE));
    }

    @Test
    void outsideMeasuresToTheNearestEdgeOrCorner() {
        // straight out along +X from the maxX edge
        assertEquals(20.0D, VillageProximity.horizontalDistance(new BlockPos(120, 64, 50), VILLAGE));
        // straight out along -Z from the minZ edge
        assertEquals(30.0D, VillageProximity.horizontalDistance(new BlockPos(50, 64, -30), VILLAGE));
        // diagonally past a corner, so both axes contribute
        assertEquals(Math.sqrt(30 * 30 + 40 * 40),
                VillageProximity.horizontalDistance(new BlockPos(130, 64, 140), VILLAGE));
    }

    @Test
    void nearestTakesTheSmallestOfSeveralAndIsEmptyForNone() {
        BoundingBox far = BoundingBox.fromCorners(new Vec3i(900, 60, 900), new Vec3i(1000, 70, 1000));
        BoundingBox nearer = BoundingBox.fromCorners(new Vec3i(300, 60, 0), new Vec3i(400, 70, 100));
        BlockPos pos = new BlockPos(200, 64, 50);
        OptionalDouble nearest = VillageProximity.nearestHorizontalDistance(pos, List.of(far, VILLAGE, nearer));
        assertTrue(nearest.isPresent());
        assertEquals(100.0D, nearest.getAsDouble());
        // An empty list is "found nothing", which the gate reads as FAR — not as "not known yet".
        assertTrue(VillageProximity.nearestHorizontalDistance(pos, List.of()).isEmpty());
    }

    @Test
    void scanRadiusCoversTheWholeCachedRegion() {
        // One scan from a region's centre answers for every position in that region, so it must reach
        // the gate distance plus the region's half-diagonal.
        double halfDiagonal = Math.sqrt(2) * (1 << VillageProximity.CACHE_REGION_BITS) / 2.0D;
        for (double distance : new double[] {1, 24, 100, 500, 2048}) {
            assertTrue(VillageProximity.scanRadiusFor(distance) >= distance + halfDiagonal,
                    "scan radius too small for " + distance);
        }
    }
}
