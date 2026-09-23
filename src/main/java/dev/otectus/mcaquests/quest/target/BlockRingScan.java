package dev.otectus.mcaquests.quest.target;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * {@link BlockTarget}'s nearest-block search, resumable (1.7.0).
 *
 * <p>Square rings outward from the origin; each ring's shell is visited row by row ({@code dx} ascending,
 * then {@code dz} ascending), and each column from {@code -vertical} to {@code +vertical}. The first
 * position the probe accepts is the answer. Visiting stops after a given number of probes and carries on
 * from there next time, so the server-wide search queue can spread a 48-block scan — up to a quarter of a
 * million block reads, 10–20 ms measured on a real world — over as many ticks as its budget allows.
 * {@link BlockTarget#locate} runs the same scan to completion, so both answer identically.
 */
public final class BlockRingScan {

    /** Whether the block at a position is one the search wants. */
    @FunctionalInterface
    public interface Probe {
        boolean test(int x, int y, int z);
    }

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int radius;
    private final int vertical;
    private int ring;
    private int dx;
    /** 0 or 1 for the two edge cells of an inner row; 0..2*ring for a full row. */
    private int dzIndex;
    private int dy;
    private boolean done;

    public BlockRingScan(BlockPos from, int radius, int vertical) {
        this.originX = from.getX();
        this.originY = from.getY();
        this.originZ = from.getZ();
        this.radius = Math.max(0, radius);
        this.vertical = Math.max(0, vertical);
        this.ring = 0;
        this.dx = 0;
        this.dzIndex = 0;
        this.dy = -this.vertical;
    }

    /**
     * Probes at most {@code maxProbes} positions. The first match, or empty — because this slice found
     * nothing (see {@link #exhausted}) or because the scan has run out.
     */
    public Optional<BlockPos> advance(int maxProbes, Probe probe) {
        int probes = 0;
        while (!done && probes < maxProbes) {
            int dz = currentDz();
            int x = originX + dx;
            int y = originY + dy;
            int z = originZ + dz;
            probes++;
            boolean hit = probe.test(x, y, z);
            next();
            if (hit) {
                return Optional.of(new BlockPos(x, y, z));
            }
        }
        return Optional.empty();
    }

    public boolean exhausted() {
        return done;
    }

    /** A full row when {@code |dx| == ring}; otherwise only the two edge cells, {@code -ring} then {@code ring}. */
    private boolean fullRow() {
        return Math.abs(dx) == ring;
    }

    private int currentDz() {
        if (ring == 0) {
            return 0;
        }
        return fullRow() ? -ring + dzIndex : (dzIndex == 0 ? -ring : ring);
    }

    private int rowLength() {
        return ring == 0 ? 1 : (fullRow() ? 2 * ring + 1 : 2);
    }

    private void next() {
        if (dy < vertical) {
            dy++;
            return;
        }
        dy = -vertical;
        if (dzIndex + 1 < rowLength()) {
            dzIndex++;
            return;
        }
        dzIndex = 0;
        if (ring > 0 && dx < ring) {
            dx++;
            return;
        }
        if (ring >= radius) {
            done = true;
            return;
        }
        ring++;
        dx = -ring;
    }
}
