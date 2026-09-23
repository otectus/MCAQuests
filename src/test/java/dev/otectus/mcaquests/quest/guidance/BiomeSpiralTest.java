package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resumable biome search visits the points vanilla's {@code findClosestBiome3d} visits, in the same
 * order, however it is sliced (1.7.0) — so moving guidance's biome search onto the queue changes when the
 * answer arrives, never what it is.
 */
class BiomeSpiralTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final BlockPos FROM = new BlockPos(137, 70, -412);
    private static final int RADIUS = 640;
    private static final int STEP = 32;
    private static final int VERTICAL = 64;
    private static final int MIN = -64;
    private static final int MAX = 320;

    /** Vanilla's loop, written out: every point {@code findClosestBiome3d} would sample, in order. */
    private static List<BlockPos> vanillaOrder() {
        List<BlockPos> points = new ArrayList<>();
        int[] heights = Mth.outFromOrigin(FROM.getY(), MIN + 1, MAX, VERTICAL).toArray();
        for (BlockPos.MutableBlockPos offset : BlockPos.spiralAround(BlockPos.ZERO, Math.floorDiv(RADIUS, STEP),
                Direction.EAST, Direction.SOUTH)) {
            int x = FROM.getX() + offset.getX() * STEP;
            int z = FROM.getZ() + offset.getZ() * STEP;
            for (int y : heights) {
                points.add(new BlockPos(x, y, z));
            }
        }
        return points;
    }

    private static List<BlockPos> sliced(int perStep) {
        BiomeSpiral spiral = new BiomeSpiral(FROM, RADIUS, STEP, VERTICAL, MIN, MAX);
        List<BlockPos> seen = new ArrayList<>();
        while (!spiral.exhausted()) {
            Optional<BlockPos> found = spiral.advance(perStep, (x, y, z) -> {
                seen.add(new BlockPos(x, y, z));
                return false;
            });
            assertTrue(found.isEmpty());
        }
        return seen;
    }

    @Test
    @DisplayName("sliced into any step size, the spiral samples vanilla's points in vanilla's order")
    void samplesVanillaOrderWhateverTheSlice() {
        List<BlockPos> expected = vanillaOrder();
        assertEquals(expected, sliced(1));
        assertEquals(expected, sliced(7));
        assertEquals(expected, sliced(256));
        assertEquals(expected, sliced(expected.size() + 5));
    }

    @Test
    @DisplayName("the first match is the one vanilla would return, found across step boundaries")
    void findsVanillasFirstMatch() {
        List<BlockPos> order = vanillaOrder();
        // Two candidate points; vanilla answers with whichever it samples first.
        BlockPos near = order.get(1234);
        BlockPos far = order.get(4000);
        BiomeSpiral spiral = new BiomeSpiral(FROM, RADIUS, STEP, VERTICAL, MIN, MAX);
        Optional<BlockPos> found = Optional.empty();
        int steps = 0;
        while (found.isEmpty() && !spiral.exhausted()) {
            found = spiral.advance(100, (x, y, z) -> {
                BlockPos p = new BlockPos(x, y, z);
                return p.equals(near) || p.equals(far);
            });
            steps++;
        }
        assertEquals(Optional.of(near), found);
        assertEquals(13, steps, "1,235 samples at 100 a step is the thirteenth step");
    }

    @Test
    @DisplayName("an exhausted spiral answers empty")
    void exhaustedAnswersEmpty() {
        BiomeSpiral spiral = new BiomeSpiral(FROM, 64, STEP, VERTICAL, MIN, MAX);
        assertFalse(spiral.exhausted());
        assertTrue(spiral.advance(Integer.MAX_VALUE, (x, y, z) -> false).isEmpty());
        assertTrue(spiral.exhausted());
        assertTrue(spiral.advance(10, (x, y, z) -> true).isEmpty(), "nothing is left to sample");
    }
}
