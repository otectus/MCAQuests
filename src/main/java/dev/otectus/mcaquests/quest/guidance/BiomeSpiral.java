package dev.otectus.mcaquests.quest.guidance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.Iterator;
import java.util.Optional;

/**
 * Vanilla's {@code BiomeSource.findClosestBiome3d} walk, resumable (1.7.0).
 *
 * <p>Vanilla samples outward from the origin in one call: a square spiral of columns
 * {@code horizontalStep} apart, and in each column the heights {@code Mth.outFromOrigin} yields, nearest
 * first. For a biome that exists somewhere but not nearby that is every point out to the radius — the
 * runtime fixture measured 140–170 ms on a real world for one guidance search. This visits exactly the
 * same points in exactly the same order, so it finds exactly the same position, but stops after a given
 * number of samples and carries on from there next time, which lets the server-wide search queue spread
 * the work over as many ticks as its budget allows.
 */
final class BiomeSpiral {

    /** Whether the biome at a block position is one the search wants. */
    @FunctionalInterface
    interface Probe {
        boolean test(int x, int y, int z);
    }

    private final int originX;
    private final int originZ;
    private final int horizontalStep;
    private final int[] heights;
    private final Iterator<BlockPos.MutableBlockPos> columns;
    private int columnX;
    private int columnZ;
    /** The next height to sample in the current column; {@code heights.length} when a new one is due. */
    private int heightIndex;

    BiomeSpiral(BlockPos from, int blockRadius, int horizontalStep, int verticalStep, int minBuildHeight,
                int maxBuildHeight) {
        this.originX = from.getX();
        this.originZ = from.getZ();
        this.horizontalStep = horizontalStep;
        this.heights = Mth.outFromOrigin(from.getY(), minBuildHeight + 1, maxBuildHeight, verticalStep).toArray();
        this.columns = BlockPos.spiralAround(BlockPos.ZERO, Math.floorDiv(blockRadius, horizontalStep),
                Direction.EAST, Direction.SOUTH).iterator();
        this.heightIndex = heights.length;
    }

    /**
     * Samples at most {@code maxSamples} points. The first match, or empty — either because this step
     * found nothing (see {@link #exhausted}) or because the spiral has run out.
     */
    Optional<BlockPos> advance(int maxSamples, Probe probe) {
        int samples = 0;
        while (samples < maxSamples) {
            if (heightIndex >= heights.length) {
                if (!columns.hasNext()) {
                    return Optional.empty();
                }
                BlockPos.MutableBlockPos offset = columns.next();
                columnX = originX + offset.getX() * horizontalStep;
                columnZ = originZ + offset.getZ() * horizontalStep;
                heightIndex = 0;
            }
            int y = heights[heightIndex++];
            samples++;
            if (probe.test(columnX, y, columnZ)) {
                return Optional.of(new BlockPos(columnX, y, columnZ));
            }
        }
        return Optional.empty();
    }

    /** True once every point has been sampled. */
    boolean exhausted() {
        return heightIndex >= heights.length && !columns.hasNext();
    }
}
