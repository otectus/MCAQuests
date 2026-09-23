package dev.otectus.mcaquests.quest.target;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resumable block scan visits the positions the one-call scan visited before 1.7.0, in the same order,
 * however it is sliced — so queueing guidance's block search changes when the answer arrives, never what
 * it is.
 */
class BlockRingScanTest {

    private static final BlockPos FROM = new BlockPos(10, 64, -3);
    private static final int RADIUS = 6;
    private static final int VERTICAL = 2;

    /** The pre-1.7.0 loop from {@code BlockTarget.locate}, written out. */
    private static List<BlockPos> originalOrder() {
        List<BlockPos> points = new ArrayList<>();
        for (int ring = 0; ring <= RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -VERTICAL; dy <= VERTICAL; dy++) {
                        points.add(new BlockPos(FROM.getX() + dx, FROM.getY() + dy, FROM.getZ() + dz));
                    }
                }
            }
        }
        return points;
    }

    private static List<BlockPos> sliced(int perStep) {
        BlockRingScan scan = new BlockRingScan(FROM, RADIUS, VERTICAL);
        List<BlockPos> seen = new ArrayList<>();
        while (!scan.exhausted()) {
            assertTrue(scan.advance(perStep, (x, y, z) -> {
                seen.add(new BlockPos(x, y, z));
                return false;
            }).isEmpty());
        }
        return seen;
    }

    @Test
    @DisplayName("sliced into any step size, the scan visits the original positions in the original order")
    void visitsTheOriginalOrder() {
        List<BlockPos> expected = originalOrder();
        assertEquals(expected, sliced(1));
        assertEquals(expected, sliced(9));
        assertEquals(expected, sliced(4096));
    }

    @Test
    @DisplayName("the first match is the one the original scan returned")
    void findsTheOriginalFirstMatch() {
        List<BlockPos> order = originalOrder();
        BlockPos near = order.get(137);
        BlockPos far = order.get(600);
        BlockRingScan scan = new BlockRingScan(FROM, RADIUS, VERTICAL);
        Optional<BlockPos> found = Optional.empty();
        while (found.isEmpty() && !scan.exhausted()) {
            found = scan.advance(10, (x, y, z) -> {
                BlockPos p = new BlockPos(x, y, z);
                return p.equals(near) || p.equals(far);
            });
        }
        assertEquals(Optional.of(near), found);
    }

    @Test
    @DisplayName("radius zero is the origin column only")
    void radiusZeroIsTheOriginColumn() {
        BlockRingScan scan = new BlockRingScan(FROM, 0, 1);
        List<BlockPos> seen = new ArrayList<>();
        scan.advance(100, (x, y, z) -> seen.add(new BlockPos(x, y, z)) && false);
        assertEquals(List.of(FROM.below(), FROM, FROM.above()), seen);
        assertTrue(scan.exhausted());
    }
}
