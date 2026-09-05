package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Whether the tracked target gets a world marker, an arrow at the edge, or nothing — and where.
 *
 * <p>The only object in the marker path that remembers anything between frames, which is deliberate:
 * projection is arithmetic and belongs in {@link MarkerProjection}, geometry is arithmetic and
 * belongs in {@link EdgeSafeRect}, and everything that can go wrong over time is here where it can be
 * driven frame by frame in a test.
 *
 * <p>Three things it fixes about the 1.5.3 path, all of them cases a player hits and a screenshot
 * does not show:
 *
 * <ul>
 *   <li><b>A target on the frustum boundary flickered.</b> One pixel of mouse noise moved it in and
 *       out of the frame, and the marker alternated between a world glyph and an edge arrow every
 *       frame. There are now two thresholds and a frame count between them.</li>
 *   <li><b>Behind the camera was mirror arithmetic on a negative {@code w}.</b> Sign noise near the
 *       near plane chose an edge. A target behind the camera now uses its camera-space bearing, which
 *       is defined everywhere except exactly behind, and exactly behind is answered by a constant.</li>
 *   <li><b>Nothing was smoothed, so the arrow jittered.</b> The <em>direction</em> is filtered and
 *       re-intersected with the rectangle every frame; filtering the position instead would send a
 *       marker rounding a corner straight across the middle of the screen.</li>
 * </ul>
 *
 * <p>Nothing here allocates after construction. No {@code Vec2}, no {@code Optional}, no boxing: one
 * scratch {@link Vector4f}, two reused rectangles, and primitives.
 */
public final class EdgeIndicatorState {

    /** What the marker is doing this frame. */
    public enum EdgeMode {
        /** Nothing is drawn: no target, wrong dimension, or arithmetic that did not come out. */
        HIDDEN,
        /** The target is on screen; the world renderer draws its billboard. */
        WORLD,
        /** The target is off screen or behind; the HUD draws an arrow at {@link #edgeX()}. */
        EDGE
    }

    /** Which edge of the safe rectangle the arrow ended up on, for placing its label inward. */
    public enum EdgeSide {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        /** Not on an edge at all: the world marker, or nothing. */
        NONE
    }

    /**
     * The settings a frame is decided with, derived from {@link MarkerSettings} once per config load
     * rather than once per frame.
     *
     * @param inset       configured inset in GUI pixels, before the icon's own half-size
     * @param iconHalfW   half the indicator's width, so the whole icon clears the inset
     * @param iconHalfH   half its height
     * @param smoothingMs the direction filter's time constant; zero means none
     * @param enterPx     how far outside the visible rectangle a target goes before the arrow appears
     * @param exitPx      how far back inside it comes before the world marker returns
     */
    public record EdgeTuning(double inset, double iconHalfW, double iconHalfH,
                             double smoothingMs, double enterPx, double exitPx,
                             int transitionFrames, boolean reducedMotion,
                             McaQuestsConfig.Client.EdgeIndicatorMode mode) {
    }

    /** Depth along the camera's forward axis below which matrix projection is not trusted, in blocks. */
    private static final double NEAR_EPSILON = 0.05D;
    /** How far past that the two-frame rule still applies, rather than entering EDGE at once. */
    private static final double NEAR_BAND = 0.25D;
    /** Below this squared length a bearing has no direction and the fallback is used. */
    private static final double DEGENERATE = 1.0E-8D;
    /** A frame this far from the last is a stutter; the filter snaps rather than sweeping. */
    private static final double FRAME_GAP_SECONDS = 0.250D;
    /** A camera that moved further than this in one frame teleported, in blocks. */
    private static final double CAMERA_JUMP = 8.0D;
    /** A target that moved further than this in one frame is not on the same walk, in blocks. */
    private static final double ANCHOR_JUMP = 16.0D;

    private final EdgeSafeRect edgeRect = new EdgeSafeRect();
    private final EdgeSafeRect visibleRect = new EdgeSafeRect();
    private final EdgeIndicatorSmoother smoother = new EdgeIndicatorSmoother();
    private final Vector4f scratch = new Vector4f();

    private EdgeMode mode = EdgeMode.HIDDEN;
    private EdgeSide edgeSide = EdgeSide.NONE;
    private double edgeX;
    private double edgeY;
    private double angleRadians;

    // What the last frame was about, for spotting the discontinuities that must not be smoothed over.
    private boolean seen;
    private int lastIdentity;
    private Object lastDimension;
    private double lastCameraX;
    private double lastCameraY;
    private double lastCameraZ;
    private double lastAnchorX;
    private double lastAnchorY;
    private double lastAnchorZ;
    private int lastGuiWidth;
    private int lastGuiHeight;
    private double lastGuiScale;
    private long lastNanos;

    private double lastDtSeconds;
    private int enterFrames;
    private int exitFrames;
    private boolean snapNext = true;

    // The last direction that meant something, for the frame a target passes exactly behind the
    // camera: there is no answer in the arithmetic there, and the previous answer is the least
    // surprising one to keep.
    private boolean hasLastDirection;
    private double lastDirectionX;
    private double lastDirectionY;

    // The raw, unfiltered direction, kept so the debug overlay can draw it beside the filtered one.
    private double rawDirectionX;
    private double rawDirectionY;

    /** Forget everything: a new world, a new target, or the marker being switched off. */
    public void clear() {
        mode = EdgeMode.HIDDEN;
        edgeSide = EdgeSide.NONE;
        edgeX = 0.0D;
        edgeY = 0.0D;
        angleRadians = 0.0D;
        seen = false;
        lastDimension = null;
        smoother.reset();
        enterFrames = 0;
        exitFrames = 0;
        snapNext = true;
        hasLastDirection = false;
    }

    /**
     * Decide this frame.
     *
     * <p>Everything it needs arrives as a primitive or as an object the caller already holds, so the
     * whole decision can be driven from a unit test with no {@code Minecraft} anywhere near it.
     *
     * @param targetIdentity  what makes this a different target rather than the same one moved
     * @param targetDimension the dimension the target is in, compared with the camera's by equality
     * @param nowNanos        wall clock, for a frame time that survives the game being paused
     * @param tuning          the config, already resolved; see {@link EdgeTuning}
     */
    public void update(int targetIdentity, Object targetDimension, Object cameraDimension,
                       double anchorX, double anchorY, double anchorZ,
                       double cameraX, double cameraY, double cameraZ,
                       double lookX, double lookY, double lookZ,
                       double leftX, double leftY, double leftZ,
                       double upX, double upY, double upZ,
                       Matrix4f modelView, Matrix4f projection,
                       int guiWidth, int guiHeight, double guiScale,
                       long nowNanos, EdgeTuning tuning) {
        if (targetDimension == null || cameraDimension == null
                || !targetDimension.equals(cameraDimension)) {
            // A marker for another dimension would be drawn at a coordinate that means nothing here,
            // and no amount of hysteresis makes that worth one more frame.
            clear();
            return;
        }
        if (!finite(anchorX, anchorY, anchorZ) || !finite(cameraX, cameraY, cameraZ)
                || !finite(lookX, lookY, lookZ)) {
            hide();
            return;
        }

        double dt = seen ? (nowNanos - lastNanos) / 1.0E9D : 0.0D;
        lastDtSeconds = dt;
        if (discontinuous(targetIdentity, cameraDimension, anchorX, anchorY, anchorZ,
                cameraX, cameraY, cameraZ, guiWidth, guiHeight, guiScale, dt)) {
            resetTemporalFilter();
        }
        remember(targetIdentity, cameraDimension, anchorX, anchorY, anchorZ,
                cameraX, cameraY, cameraZ, guiWidth, guiHeight, guiScale, nowNanos);

        double relX = anchorX - cameraX;
        double relY = anchorY - cameraY;
        double relZ = anchorZ - cameraZ;
        double depth = relX * lookX + relY * lookY + relZ * lookZ;
        if (!Double.isFinite(depth)) {
            hide();
            return;
        }

        double rawX;
        double rawY;
        double screenX = guiWidth * 0.5D;
        double screenY = guiHeight * 0.5D;
        boolean front = depth > NEAR_EPSILON;
        boolean outsideByEnter = false;
        boolean insideByExit = false;
        if (front) {
            if (!MarkerProjection.projectInto(relX, relY, relZ, modelView, projection, scratch)) {
                hide();
                return;
            }
            double ndcX = scratch.x();
            double ndcY = scratch.y();
            screenX = (ndcX * 0.5D + 0.5D) * guiWidth;
            screenY = (0.5D - ndcY * 0.5D) * guiHeight;
            if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
                hide();
                return;
            }
            visibleRect.set(0.0D, 0.0D, guiWidth, guiHeight);
            // Two thresholds, never one: the gap between them is the whole anti-flicker mechanism. A
            // negative margin grows the rectangle, so "outside by enterPx" is one containment test.
            outsideByEnter = !visibleRect.containsWithMargin(screenX, screenY, -tuning.enterPx());
            insideByExit = visibleRect.containsWithMargin(screenX, screenY, tuning.exitPx());
            // Screen Y grows downward while clip Y grows upward.
            rawX = ndcX;
            rawY = -ndcY;
        } else {
            // getLeftVector points left, so right is its negation; clip Y is up and screen Y is down.
            rawX = -(relX * leftX + relY * leftY + relZ * leftZ);
            rawY = -(relX * upX + relY * upY + relZ * upZ);
        }

        double lengthSquared = rawX * rawX + rawY * rawY;
        if (!Double.isFinite(lengthSquared) || lengthSquared < DEGENERATE) {
            if (hasLastDirection) {
                rawX = lastDirectionX;
                rawY = lastDirectionY;
            } else {
                // Nothing about a target exactly behind the player says left or right, so the answer
                // is chosen rather than computed: bottom centre, meaning "turn around", the same
                // every frame rather than flickering between two edges on the noise of a mouse.
                rawX = 0.0D;
                rawY = 1.0D;
            }
        } else {
            double length = Math.sqrt(lengthSquared);
            rawX /= length;
            rawY /= length;
            hasLastDirection = true;
            lastDirectionX = rawX;
            lastDirectionY = rawY;
        }
        rawDirectionX = rawX;
        rawDirectionY = rawY;

        EdgeMode next = advanceMode(front, depth, outsideByEnter, insideByExit,
                tuning.transitionFrames());
        if (next == EdgeMode.EDGE
                && tuning.mode() == McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED) {
            next = EdgeMode.WORLD;
        }
        mode = next;

        if (mode != EdgeMode.EDGE) {
            edgeSide = EdgeSide.NONE;
            edgeX = screenX;
            edgeY = screenY;
            angleRadians = Math.atan2(rawY, rawX);
            return;
        }
        placeOnEdge(rawX, rawY, guiWidth, guiHeight, dt, tuning);
    }

    /**
     * Turn a decided world frame into an edge frame, for a target on screen but invisible behind
     * terrain.
     *
     * <p>Only {@code OFFSCREEN_OR_OCCLUDED} calls this, and only after
     * {@link MarkerOcclusionSampler} has said so. A promotion of an already-decided frame rather than
     * a fourth branch inside the state machine, so the default path stays exactly what it was.
     */
    public void promoteToEdge(int guiWidth, int guiHeight, double dtSeconds, EdgeTuning tuning) {
        if (mode != EdgeMode.WORLD) {
            return;
        }
        mode = EdgeMode.EDGE;
        placeOnEdge(rawDirectionX, rawDirectionY, guiWidth, guiHeight, dtSeconds, tuning);
    }

    private void placeOnEdge(double rawX, double rawY, int guiWidth, int guiHeight,
                             double dtSeconds, EdgeTuning tuning) {
        double dirX = rawX;
        double dirY = rawY;
        if (!tuning.reducedMotion() && tuning.smoothingMs() > 0.0D) {
            if (snapNext) {
                smoother.snap(rawX, rawY);
            } else {
                smoother.update(rawX, rawY, dtSeconds, tuning.smoothingMs() / 1000.0D);
            }
            if (smoother.primed()) {
                dirX = smoother.x();
                dirY = smoother.y();
            }
        }
        snapNext = false;

        // The safe rectangle accounts for the icon, not merely a point: an 18px inset with a 9px
        // half-diamond leaves half the diamond outside the inset the player asked for.
        double insetX = tuning.inset() + tuning.iconHalfW();
        double insetY = tuning.inset() + tuning.iconHalfH();
        edgeRect.set(insetX, insetY, guiWidth - insetX, guiHeight - insetY);
        if (!edgeRect.intersectFromCenter(dirX, dirY)) {
            hide();
            return;
        }
        double x = edgeRect.outX();
        double y = edgeRect.outY();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            hide();
            return;
        }
        edgeX = x;
        edgeY = y;
        angleRadians = Math.atan2(dirY, dirX);
        edgeSide = edgeRect.hitsVerticalEdge(dirX, dirY)
                ? (dirX >= 0.0D ? EdgeSide.RIGHT : EdgeSide.LEFT)
                : (dirY >= 0.0D ? EdgeSide.BOTTOM : EdgeSide.TOP);
    }

    /**
     * The hysteresis machine.
     *
     * <p>A target behind the camera enters EDGE at once, since there is nothing ambiguous about it,
     * except within a band around the near plane where depth noise alone could flip it and the
     * two-frame rule applies as it does on screen.
     */
    private EdgeMode advanceMode(boolean front, double depth, boolean outsideByEnter,
                                 boolean insideByExit, int transitionFrames) {
        int frames = Math.max(transitionFrames, 1);
        if (!front) {
            exitFrames = 0;
            if (depth <= -NEAR_BAND) {
                enterFrames = frames;
                return EdgeMode.EDGE;
            }
            enterFrames++;
            if (enterFrames >= frames) {
                return EdgeMode.EDGE;
            }
            return mode == EdgeMode.EDGE ? EdgeMode.EDGE : EdgeMode.WORLD;
        }
        if (mode == EdgeMode.EDGE) {
            enterFrames = 0;
            exitFrames = insideByExit ? exitFrames + 1 : 0;
            return exitFrames >= frames ? EdgeMode.WORLD : EdgeMode.EDGE;
        }
        exitFrames = 0;
        enterFrames = outsideByEnter ? enterFrames + 1 : 0;
        return enterFrames >= frames ? EdgeMode.EDGE : EdgeMode.WORLD;
    }

    private boolean discontinuous(int targetIdentity, Object dimension,
                                  double anchorX, double anchorY, double anchorZ,
                                  double cameraX, double cameraY, double cameraZ,
                                  int guiWidth, int guiHeight, double guiScale, double dt) {
        if (!seen) {
            return true;
        }
        if (targetIdentity != lastIdentity || !dimension.equals(lastDimension)) {
            return true;
        }
        if (guiWidth != lastGuiWidth || guiHeight != lastGuiHeight || guiScale != lastGuiScale) {
            return true;
        }
        if (dt > FRAME_GAP_SECONDS || dt < 0.0D) {
            return true;
        }
        if (squaredDistance(cameraX, cameraY, cameraZ, lastCameraX, lastCameraY, lastCameraZ)
                > CAMERA_JUMP * CAMERA_JUMP) {
            return true;
        }
        return squaredDistance(anchorX, anchorY, anchorZ, lastAnchorX, lastAnchorY, lastAnchorZ)
                > ANCHOR_JUMP * ANCHOR_JUMP;
    }

    private static double squaredDistance(double ax, double ay, double az,
                                          double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean finite(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }

    private void remember(int targetIdentity, Object dimension,
                          double anchorX, double anchorY, double anchorZ,
                          double cameraX, double cameraY, double cameraZ,
                          int guiWidth, int guiHeight, double guiScale, long nowNanos) {
        seen = true;
        lastIdentity = targetIdentity;
        lastDimension = dimension;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorZ = anchorZ;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        lastCameraZ = cameraZ;
        lastGuiWidth = guiWidth;
        lastGuiHeight = guiHeight;
        lastGuiScale = guiScale;
        lastNanos = nowNanos;
    }

    /** Snap rather than sweep on the next frame, and start the frame counts again. */
    private void resetTemporalFilter() {
        smoother.reset();
        snapNext = true;
        enterFrames = 0;
        exitFrames = 0;
    }

    /**
     * Draw nothing this frame, without forgetting where the target was.
     *
     * <p>Not a {@link #clear()}: one frame of non-finite matrices is a hiccup, and the direction is
     * worth keeping for the frame after it, so the arrow comes back where it left rather than
     * sweeping in from wherever the filter happened to be.
     */
    private void hide() {
        mode = EdgeMode.HIDDEN;
        edgeSide = EdgeSide.NONE;
    }

    /** How long the last frame took, for anything that has to advance the same filter afterwards. */
    public double lastDtSeconds() {
        return lastDtSeconds;
    }

    public EdgeMode mode() {
        return mode;
    }

    public EdgeSide edgeSide() {
        return edgeSide;
    }

    public double edgeX() {
        return edgeX;
    }

    public double edgeY() {
        return edgeY;
    }

    public double angleRadians() {
        return angleRadians;
    }

    /** The unfiltered direction this frame, for the debug overlay to draw beside the filtered one. */
    public double rawDirectionX() {
        return rawDirectionX;
    }

    public double rawDirectionY() {
        return rawDirectionY;
    }

    /** The filtered direction the arrow is actually placed with. */
    public double filteredDirectionX() {
        return smoother.primed() ? smoother.x() : rawDirectionX;
    }

    public double filteredDirectionY() {
        return smoother.primed() ? smoother.y() : rawDirectionY;
    }
}
