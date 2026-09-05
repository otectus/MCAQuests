package dev.otectus.mcaquests.client.marker;

/**
 * An exponential filter over the direction the edge arrow points, in units of time rather than frames.
 *
 * <p>Two things it deliberately does not do. It does not smooth the arrow's <em>position</em>: a
 * marker travelling round a corner of the screen would then cut across the middle of it, which reads
 * as the target being somewhere it is not. And it does not use a per-frame blend factor: the same
 * constant at 30 and at 144 frames a second is two entirely different amounts of smoothing, so the
 * factor is derived from the elapsed time instead and the arrow settles in about a quarter of a
 * second on any machine.
 *
 * <p>Two doubles and a flag. Nothing here allocates.
 */
public final class EdgeIndicatorSmoother {

    /** The longest step that is smoothed; past this the frame gap is a stutter, not motion. */
    private static final double MAX_DT = 0.100D;
    /** Below this a filtered vector has cancelled itself out and the raw one is used instead. */
    private static final double EPS = 1.0E-8D;

    private double x;
    private double y;
    private boolean primed;

    /**
     * Blend the filtered direction toward the raw one and return the unit result.
     *
     * @param dtSeconds  time since the last frame; clamped, so a loading pause does not teleport
     * @param tauSeconds the time constant; zero or less means no smoothing at all
     * @return true when the result is usable, false when the raw direction was degenerate
     */
    public boolean update(double rawX, double rawY, double dtSeconds, double tauSeconds) {
        if (!Double.isFinite(rawX) || !Double.isFinite(rawY)) {
            return primed;
        }
        if (!primed || tauSeconds <= 0.0D) {
            return snap(rawX, rawY);
        }
        double dt = Math.max(0.0D, Math.min(dtSeconds, MAX_DT));
        double alpha = 1.0D - Math.exp(-dt / tauSeconds);
        double fx = x * (1.0D - alpha) + rawX * alpha;
        double fy = y * (1.0D - alpha) + rawY * alpha;
        if (fx * fx + fy * fy < EPS) {
            // A half-turn in one frame can put the filtered vector through zero, where it has no
            // direction to normalise; the raw one is the honest answer for that frame.
            fx = rawX;
            fy = rawY;
        }
        return store(fx, fy);
    }

    /** Jump straight to the raw direction, for a discontinuity that has no motion to smooth. */
    public boolean snap(double rawX, double rawY) {
        return store(rawX, rawY);
    }

    /** Forget everything; the next update snaps. */
    public void reset() {
        x = 0.0D;
        y = 0.0D;
        primed = false;
    }

    public boolean primed() {
        return primed;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    private boolean store(double vx, double vy) {
        double length = Math.sqrt(vx * vx + vy * vy);
        if (!Double.isFinite(length) || length < 1.0E-9D) {
            return primed;
        }
        x = vx / length;
        y = vy / length;
        primed = true;
        return true;
    }
}
