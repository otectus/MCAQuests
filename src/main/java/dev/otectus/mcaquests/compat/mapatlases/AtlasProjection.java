package dev.otectus.mcaquests.compat.mapatlases;

import dev.otectus.mcaquests.compat.WaypointSpec;

/** Continuous projection and viewport policy, independent of graphics and optional mods. */
public final class AtlasProjection {
    private AtlasProjection() { }
    public record Point(double x, double y) { }
    public record Rect(double left, double top, double right, double bottom) {
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
        public Rect intersect(Rect other) {
            return new Rect(Math.max(left, other.left), Math.max(top, other.top),
                    Math.min(right, other.right), Math.min(bottom, other.bottom));
        }
    }
    public static Point project(double x, double z, int centerX, int centerZ, int scale) {
        if (scale < 0 || scale > 4) throw new IllegalArgumentException("Unsupported map scale");
        double factor = 1 << scale;
        return new Point(64 + (x - (double) centerX) / factor,
                64 + (z - (double) centerZ) / factor);
    }
    public static boolean owns(Point point) {
        return point.x >= 0 && point.x < 128 && point.y >= 0 && point.y < 128;
    }
    public static String sliceReason(WaypointSpec spec, Integer height, boolean strict, int tolerance) {
        if (height == null || !strict) return "ready";
        if (!spec.presentation().reliableHeight() || spec.presentation().approximate()
                || spec.presentation().lastKnown()) return "uncertain_height";
        return Math.abs((long) spec.pos().getY() - height) <= tolerance ? "ready" : "slice";
    }
    /** Square/rectangular rim intersection in already rotated viewport coordinates. */
    public static Point rim(Point target, Rect viewport, double inset) {
        double cx = (viewport.left + viewport.right) / 2, cy = (viewport.top + viewport.bottom) / 2;
        double dx = target.x - cx, dy = target.y - cy;
        double hw = Math.max(0, (viewport.right - viewport.left) / 2 - inset);
        double hh = Math.max(0, (viewport.bottom - viewport.top) / 2 - inset);
        if (dx == 0 && dy == 0) return new Point(cx, cy);
        double t = Math.min(dx == 0 ? Double.POSITIVE_INFINITY : hw / Math.abs(dx),
                dy == 0 ? Double.POSITIVE_INFINITY : hh / Math.abs(dy));
        return new Point(cx + dx * t, cy + dy * t);
    }
}
