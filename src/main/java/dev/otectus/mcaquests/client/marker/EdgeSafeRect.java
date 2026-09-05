package dev.otectus.mcaquests.client.marker;

/**
 * The rectangle an off-screen indicator is allowed to sit on, and where a direction meets it.
 *
 * <p>The old code did this inside {@code MarkerProjection.clamp}, mixed in with the on-screen
 * decision, which meant the geometry could not be tested on its own and the same arithmetic could
 * not be reused for the visible rectangle. It is a rectangle and a ray from its centre; that is all
 * it is, and it is worth having as one testable thing.
 *
 * <p>Mutable and reused. This is asked a question every frame at whatever frame rate the player has,
 * and the answer is two doubles: a record per frame would be a hundred and forty small objects a
 * second for the collector to think about, to say something that fits in two fields.
 *
 * <p><b>Postcondition of {@link #intersectFromCenter}</b>: for any finite, non-zero direction the
 * output is finite and lies on the boundary of the rectangle. A zero component maps to an infinite
 * ray parameter on that axis, so the other axis decides — never a division that yields NaN.
 */
public final class EdgeSafeRect {

    /** Below this a direction component is zero, and its axis cannot be the one that is hit. */
    private static final double EPS = 1.0E-9D;

    private double minX;
    private double minY;
    private double maxX;
    private double maxY;

    private double outX;
    private double outY;

    /**
     * Reshape the rectangle in place.
     *
     * <p>A degenerate rectangle (a window smaller than twice its own inset) collapses to its centre
     * rather than inverting: an indicator drawn on the centre of a tiny screen is wrong-looking but
     * on screen, and an inverted rectangle would put it outside the window entirely.
     */
    public void set(double minX, double minY, double maxX, double maxY) {
        if (maxX < minX) {
            double centre = (minX + maxX) * 0.5D;
            minX = centre;
            maxX = centre;
        }
        if (maxY < minY) {
            double centre = (minY + maxY) * 0.5D;
            minY = centre;
            maxY = centre;
        }
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public double centerX() {
        return (minX + maxX) * 0.5D;
    }

    public double centerY() {
        return (minY + maxY) * 0.5D;
    }

    public double halfWidth() {
        return (maxX - minX) * 0.5D;
    }

    public double halfHeight() {
        return (maxY - minY) * 0.5D;
    }

    /**
     * Cast a ray from the centre along {@code (dirX, dirY)} and stop where it leaves the rectangle.
     *
     * <p>The answer is left in {@link #outX()} / {@link #outY()} rather than returned, so that
     * nothing is allocated.
     *
     * @return false when the direction is not usable — non-finite, or zero in both axes — in which
     *         case the output is the centre and the caller must not draw anything from it
     */
    public boolean intersectFromCenter(double dirX, double dirY) {
        double centerX = centerX();
        double centerY = centerY();
        if (!Double.isFinite(dirX) || !Double.isFinite(dirY)
                || (Math.abs(dirX) < EPS && Math.abs(dirY) < EPS)) {
            outX = centerX;
            outY = centerY;
            return false;
        }
        double tx = Math.abs(dirX) < EPS
                ? Double.POSITIVE_INFINITY
                : halfWidth() / Math.abs(dirX);
        double ty = Math.abs(dirY) < EPS
                ? Double.POSITIVE_INFINITY
                : halfHeight() / Math.abs(dirY);
        double t = Math.min(tx, ty);
        outX = centerX + dirX * t;
        outY = centerY + dirY * t;
        return true;
    }

    /** True when the intersection landed on a vertical (left or right) edge rather than a horizontal one. */
    public boolean hitsVerticalEdge(double dirX, double dirY) {
        // tx <= ty, rearranged so neither side divides: halfW/|dx| <= halfH/|dy|.
        return halfWidth() * Math.abs(dirY) <= halfHeight() * Math.abs(dirX);
    }

    /** Where the last {@link #intersectFromCenter} landed, in the same units the rectangle was set in. */
    public double outX() {
        return outX;
    }

    public double outY() {
        return outY;
    }

    /** True when the point is inside the rectangle shrunk by {@code margin} on every side. */
    public boolean containsWithMargin(double x, double y, double margin) {
        return x >= minX + margin && x <= maxX - margin
                && y >= minY + margin && y <= maxY - margin;
    }

    /** True when the point sits on the rectangle's boundary, within {@code eps}. */
    public boolean isOnBoundary(double x, double y, double eps) {
        boolean inside = x >= minX - eps && x <= maxX + eps && y >= minY - eps && y <= maxY + eps;
        if (!inside) {
            return false;
        }
        return Math.abs(x - minX) <= eps || Math.abs(x - maxX) <= eps
                || Math.abs(y - minY) <= eps || Math.abs(y - maxY) <= eps;
    }
}
