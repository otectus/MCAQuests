package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;

/**
 * The arithmetic behind the world marker, with no Minecraft types in it so it can be tested.
 *
 * <p>Same reasoning as {@code PanelGeometry} and {@code ScrollView}: the parts of rendering that can
 * be wrong in a way you would only notice by standing in the right place are the parts worth pinning
 * down in a test. A fade that goes negative, or a marker that stays solid as you walk into it, is a
 * bug nobody reports precisely and nobody can reproduce on request.
 *
 * <p>The marker is sized in <em>apparent</em> pixels rather than blocks. A fixed world size is one of
 * the two ways a marker goes wrong: at arm's length it is a billboard across the sky, and at two
 * hundred blocks it is a speck. Choosing the on-screen size and solving back through the projection
 * keeps the glyph between 18 and 24 pixels wherever the target actually is.
 */
public final class MarkerGeometry {

    /** How wide the glyph is on screen up close, in pixels. */
    public static final double MAX_APPARENT_PIXELS = 24.0D;
    /** How wide it is at the far end of the configured range. Never smaller, so it stays readable. */
    public static final double MIN_APPARENT_PIXELS = 18.0D;
    /** Where the glyph starts shrinking, in blocks. Inside this it is simply at its largest. */
    public static final double SIZE_NEAR = 12.0D;

    /**
     * The largest the marker may be in the world, in blocks.
     *
     * <p>Apparent size alone would put a six-metre billboard between the player and a villager
     * standing next to them, occluding the very thing it points at. Past this cap the glyph is drawn
     * flat on the HUD instead, at its true projected position; see {@link #usesHudFallback}.
     */
    public static final double MAX_WORLD_SIZE = 6.0D;

    /** Blocks past the arrival radius over which the marker fades up. */
    public static final double ARRIVE_BAND = 8.0D;
    /** Blocks before the draw distance over which it fades out. */
    public static final double FAR_BAND = 32.0D;

    /** How far a label is worth reading, in blocks, under {@code NEARBY}. */
    public static final double LABEL_NEARBY = 48.0D;

    private MarkerGeometry() {
    }

    /**
     * Cubic Hermite interpolation between two edges, clamped outside them.
     *
     * <p>Every fade in the marker is one of these rather than a straight ramp: a linear fade has a
     * corner at each end, and a corner in opacity is visible as a flick when the player walks across
     * it. Degenerate edges answer with a step, so a draw distance set inside a fade band produces a
     * marker that is either there or not, never one that flickers.
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        if (edge1 <= edge0) {
            return x >= edge1 ? 1.0D : 0.0D;
        }
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    /**
     * The acquire curve: fast at the start, settling at the end.
     *
     * <p>A marker that appears is answering a question the player just asked, so it should be mostly
     * there immediately; the tail is only there to stop it being a hard pop.
     */
    public static double easeOutCubic(double t) {
        double clamped = clamp01(t);
        double inverse = 1.0D - clamped;
        return 1.0D - inverse * inverse * inverse;
    }

    /**
     * How wide the glyph should be on screen at {@code distance} blocks, in pixels.
     *
     * <p>Bounded at both ends deliberately. The upper bound stops the marker becoming furniture when
     * the player is nearly on top of the target; the lower bound is the smallest the 16-pixel glyph
     * is still identifiable at.
     */
    public static double apparentPixels(double distance, int maxDistance) {
        double t = smoothstep(SIZE_NEAR, Math.max(SIZE_NEAR + 1.0D, maxDistance), distance);
        return MAX_APPARENT_PIXELS + (MIN_APPARENT_PIXELS - MAX_APPARENT_PIXELS) * t;
    }

    /**
     * How many world units one screen pixel covers at {@code cameraDepth} blocks in front of the eye.
     *
     * <p>Straight out of the perspective divide: the projection matrix's {@code m11} is
     * {@code 1/tan(fovY/2)}, so a pixel subtends {@code 2z/(m11*H)}. Taking it from the live matrix
     * rather than from the FOV option means zoom, Optifine-style FOV effects and a resized window all
     * come out right without the marker knowing they happened.
     *
     * @param cameraDepth      distance along the camera's forward axis, in blocks
     * @param m11              the projection matrix's {@code m11}
     * @param framebufferHeight the framebuffer height in physical pixels
     */
    public static double worldPerPixel(double cameraDepth, double m11, int framebufferHeight) {
        if (m11 <= 0.0D || framebufferHeight <= 0) {
            return 0.0D;
        }
        return 2.0D * Math.max(cameraDepth, 0.0D) / (m11 * framebufferHeight);
    }

    /** How wide to draw the glyph in the world, in blocks, capped at {@link #MAX_WORLD_SIZE}. */
    public static double worldSize(double distance, int maxDistance, double worldPerPixel) {
        return Math.min(apparentPixels(distance, maxDistance) * worldPerPixel, MAX_WORLD_SIZE);
    }

    /**
     * Whether the cap has made the world glyph too small to read, so the HUD should draw it instead.
     *
     * <p>Only happens at a very wide field of view or a very small window, where six blocks is fewer
     * than eighteen pixels. Drawing it flat at its projected position keeps it legible without
     * letting the billboard grow across the terrain it is standing on.
     */
    public static boolean usesHudFallback(double worldSize, double worldPerPixel) {
        return worldPerPixel > 0.0D && worldSize < MIN_APPARENT_PIXELS * worldPerPixel;
    }

    /**
     * How solid the marker is on approach: nothing inside the arrival radius, full eight blocks out.
     *
     * <p>Walking up to the bed must take the marker away rather than leave it standing in the
     * player's face while they try to see what they came for. Arriving is how you find out you
     * arrived.
     */
    public static float arrivalAlpha(double distance, int arriveRadius) {
        return (float) smoothstep(arriveRadius, arriveRadius + ARRIVE_BAND, distance);
    }

    /**
     * How solid the marker is at the far end: full until the last thirty-two blocks of range, then out.
     *
     * <p>The lower edge is held above the arrival radius so that a draw distance set very low cannot
     * produce a band that starts before the player has even left. Where the two bands would cross,
     * this one simply becomes a step and {@link #arrivalAlpha} decides.
     */
    public static float farAlpha(double distance, int arriveRadius, int maxDistance) {
        double lower = Math.max(maxDistance - FAR_BAND, arriveRadius + 1.0D);
        return (float) (1.0D - smoothstep(lower, maxDistance, distance));
    }

    /**
     * How wide the ground ring is, in blocks, for a target of {@code bbWidth}.
     *
     * <p>Proportional so a ravager does not wear a villager's ring, clamped so a bat does not wear a
     * dot and a giant does not wear a crop circle.
     */
    public static double ringRadius(double bbWidth) {
        double radius = 0.65D * bbWidth;
        return radius < 0.30D ? 0.30D : Math.min(radius, 0.90D);
    }

    /** Whether the label is drawn at {@code horizontalDistance} blocks under {@code labels}. */
    public static boolean labelVisible(McaQuestsConfig.Client.MarkerLabels labels,
                                       double horizontalDistance) {
        return switch (labels) {
            case ALWAYS -> true;
            case NEVER -> false;
            case NEARBY -> horizontalDistance <= LABEL_NEARBY;
        };
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

    private static double clamp01(double value) {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
