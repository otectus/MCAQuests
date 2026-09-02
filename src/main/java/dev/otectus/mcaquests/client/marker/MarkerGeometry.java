package dev.otectus.mcaquests.client.marker;

/**
 * The arithmetic behind the world marker, with no Minecraft types in it so it can be tested.
 *
 * <p>Same reasoning as {@code PanelGeometry} and {@code ScrollView}: the parts of rendering that can
 * be wrong in a way you would only notice by standing in the right place are the parts worth pinning
 * down in a test. A fade that goes negative, or a marker that stays solid as you walk into it, is a
 * bug nobody reports precisely and nobody can reproduce on request.
 */
public final class MarkerGeometry {

    /** Blocks over which the marker fades up, just outside the arrival radius. */
    public static final double FADE_IN_BAND = 12.0D;
    /** Blocks over which the marker fades out as it approaches the configured draw distance. */
    public static final double FADE_OUT_BAND = 48.0D;

    /** How tall the beam stands, in blocks. Tall enough to clear a hill, short enough not to be sky. */
    public static final double BEAM_HEIGHT = 24.0D;
    /** How wide the beam is, in blocks. A quarter block: a thread of light, not a pillar. */
    public static final double BEAM_WIDTH = 0.25D;

    private MarkerGeometry() {
    }

    /**
     * How solid the marker should be at {@code distance} blocks.
     *
     * <p>Zero inside {@code arriveRadius}, because a marker you are standing on is in your face and
     * has already done its job — arriving is how you find out you arrived. Zero again past
     * {@code maxDistance}, so a beam does not stand on the horizon for a village two thousand blocks
     * away. Between the two it ramps rather than snapping, so neither edge is a flicker as you walk.
     *
     * <p>Copes with a {@code maxDistance} set lower than the bands are wide, by taking whichever ramp
     * is dimmer: the marker then simply never reaches full strength, which is what a player who set it
     * that low asked for.
     */
    public static float alpha(double distance, int arriveRadius, int maxDistance) {
        if (distance <= arriveRadius || distance >= maxDistance) {
            return 0.0F;
        }
        double in = FADE_IN_BAND <= 0.0D ? 1.0D : (distance - arriveRadius) / FADE_IN_BAND;
        double out = FADE_OUT_BAND <= 0.0D ? 1.0D : (maxDistance - distance) / FADE_OUT_BAND;
        return (float) clamp01(Math.min(in, out));
    }

    /**
     * Horizontal distance, ignoring height.
     *
     * <p>The tracker has always measured this way and the marker follows it: a target at the bottom of
     * a ravine is not further to walk than one on the ground beside you, and reporting it as further
     * makes the number useless for deciding whether to set off.
     */
    public static double horizontalDistance(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * How far up the label floats above the marker's base, so it clears the beam rather than sitting
     * inside it, and rises a little with distance so it stays legible instead of shrinking into it.
     */
    public static double labelHeight(double distance) {
        return BEAM_HEIGHT + 1.0D + clamp01(distance / 128.0D) * 2.0D;
    }

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
