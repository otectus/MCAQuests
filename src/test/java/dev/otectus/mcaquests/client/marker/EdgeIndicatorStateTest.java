package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every way the edge indicator can be wrong, driven a frame at a time.
 *
 * <p>The cases are the spec's mandatory list, and they are the list because each of them is a thing a
 * player does — turning round, walking behind a wall, resizing the window, changing quest — that a
 * screenshot of a working marker never shows. The camera here is a real JOML view and perspective,
 * built the same way the game builds them, so the projection under test is the projection that ships.
 *
 * <p>Screen Y grows downward throughout: a target above the camera belongs on the <em>top</em> edge.
 */
class EdgeIndicatorStateTest {

    private static final double INSET = 18.0D;
    private static final double ICON_HALF = 9.0D;
    /** The edge the arrow's centre actually sits on: the inset plus the icon's own half-size. */
    private static final double EFFECTIVE = INSET + ICON_HALF;
    private static final double TOL = 1.0E-3D;

    private static final Object OVERWORLD = "minecraft:overworld";
    private static final Object NETHER = "minecraft:the_nether";

    private static EdgeIndicatorState.EdgeTuning tuning() {
        return tuning(McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_ONLY, false);
    }

    private static EdgeIndicatorState.EdgeTuning tuning(
            McaQuestsConfig.Client.EdgeIndicatorMode mode, boolean reducedMotion) {
        return new EdgeIndicatorState.EdgeTuning(INSET, ICON_HALF, ICON_HALF,
                80.0D, 4.0D, 2.0D, 2, reducedMotion, mode);
    }

    /**
     * A camera and a screen, mutable, driven frame by frame.
     *
     * <p>Everything the state needs and nothing it does not: no {@code Minecraft}, no window, no
     * level. The basis is built the way the game's is — forward, world up, and the cross products
     * between them — so {@code getLeftVector} pointing left rather than right is a mistake this can
     * catch.
     */
    private static final class Scene {

        private final EdgeIndicatorState state = new EdgeIndicatorState();

        private double camX;
        private double camY;
        private double camZ;
        private double lookX;
        private double lookY = 0.0D;
        private double lookZ = -1.0D;
        private int guiWidth = 1920;
        private int guiHeight = 1080;
        private double guiScale = 2.0D;
        private double fovDegrees = 70.0D;
        private long nanos = 1_000_000_000L;
        private int identity = 1;
        private Object dimension = OVERWORLD;

        private Matrix4f modelView() {
            return new Matrix4f().setLookAlong((float) lookX, (float) lookY, (float) lookZ,
                    0.0F, 1.0F, 0.0F);
        }

        private Matrix4f projection() {
            return new Matrix4f().perspective((float) Math.toRadians(fovDegrees),
                    (float) guiWidth / guiHeight, 0.05F, 1000.0F);
        }

        /** The camera's own right, up and left, from the forward vector and world up. */
        private double[] basis() {
            double rx = lookY * 0.0D - lookZ * 1.0D;
            double ry = lookZ * 0.0D - lookX * 0.0D;
            double rz = lookX * 1.0D - lookY * 0.0D;
            double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
            rx /= rl;
            ry /= rl;
            rz /= rl;
            double ux = ry * lookZ - rz * lookY;
            double uy = rz * lookX - rx * lookZ;
            double uz = rx * lookY - ry * lookX;
            // left is the negation of right; the game's Camera.getLeftVector points left.
            return new double[]{-rx, -ry, -rz, ux, uy, uz};
        }

        private void frame(double tx, double ty, double tz, long dtNanos) {
            nanos += dtNanos;
            double[] b = basis();
            state.update(identity, dimension, OVERWORLD, tx, ty, tz, camX, camY, camZ,
                    lookX, lookY, lookZ, b[0], b[1], b[2], b[3], b[4], b[5],
                    modelView(), projection(), guiWidth, guiHeight, guiScale, nanos, tuning());
        }

        private void frames(int count, double tx, double ty, double tz) {
            for (int i = 0; i < count; i++) {
                frame(tx, ty, tz, 16_000_000L);
            }
        }

        /** The world X that lands on a given horizontal NDC at depth {@code z} in front. */
        private double worldXForNdc(double ndc, double z) {
            double f = 1.0D / Math.tan(Math.toRadians(fovDegrees) / 2.0D);
            double aspect = (double) guiWidth / guiHeight;
            return ndc * aspect * z / f;
        }

        private double centreX() {
            return guiWidth / 2.0D;
        }

        private double centreY() {
            return guiHeight / 2.0D;
        }
    }

    @Nested
    @DisplayName("deciding what to draw")
    class Deciding {

        @Test
        @DisplayName("draws the world marker for a target straight ahead")
        void centred() {
            Scene scene = new Scene();
            scene.frames(3, 0.0D, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.WORLD, scene.state.mode());
            assertSame(EdgeIndicatorState.EdgeSide.NONE, scene.state.edgeSide());
            // The old clamp test's "centre is on screen": the published point is the projected one.
            assertEquals(scene.centreX(), scene.state.edgeX(), 0.5D);
            assertEquals(scene.centreY(), scene.state.edgeY(), 0.5D);
        }

        @Test
        @DisplayName("puts a target past each edge on that edge, inside the icon's own inset")
        void eachEdge() {
            Scene right = new Scene();
            right.frames(3, 60.0D, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, right.state.mode());
            assertSame(EdgeIndicatorState.EdgeSide.RIGHT, right.state.edgeSide());
            assertEquals(1920.0D - EFFECTIVE, right.state.edgeX(), TOL);
            assertEquals(540.0D, right.state.edgeY(), TOL);
            // The angle the chevron is rotated by: zero to the right, growing clockwise, because
            // screen Y grows downward.
            assertEquals(0.0D, right.state.angleRadians(), TOL);

            Scene left = new Scene();
            left.frames(3, -60.0D, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeSide.LEFT, left.state.edgeSide());
            assertEquals(EFFECTIVE, left.state.edgeX(), TOL);

            Scene above = new Scene();
            above.frames(3, 0.0D, 60.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeSide.TOP, above.state.edgeSide());
            assertEquals(EFFECTIVE, above.state.edgeY(), TOL);
            assertEquals(960.0D, above.state.edgeX(), TOL);
            assertEquals(-Math.PI / 2.0D, above.state.angleRadians(), TOL);

            Scene below = new Scene();
            below.frames(3, 0.0D, -60.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeSide.BOTTOM, below.state.edgeSide());
            assertEquals(1080.0D - EFFECTIVE, below.state.edgeY(), TOL);
        }

        @Test
        @DisplayName("sends each diagonal quadrant to its own corner region")
        void diagonalQuadrants() {
            assertQuadrant(60.0D, 60.0D, true, false);
            assertQuadrant(-60.0D, 60.0D, false, false);
            assertQuadrant(60.0D, -60.0D, true, true);
            assertQuadrant(-60.0D, -60.0D, false, true);
        }

        private void assertQuadrant(double x, double y, boolean expectRight, boolean expectDown) {
            Scene scene = new Scene();
            scene.frames(3, x, y, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, scene.state.mode());
            assertEquals(expectRight, scene.state.edgeX() > scene.centreX(),
                    "horizontal half for " + x + "," + y);
            assertEquals(expectDown, scene.state.edgeY() > scene.centreY(),
                    "vertical half for " + x + "," + y);
            assertTrue(scene.state.edgeX() >= EFFECTIVE - TOL
                    && scene.state.edgeX() <= 1920.0D - EFFECTIVE + TOL);
            assertTrue(scene.state.edgeY() >= EFFECTIVE - TOL
                    && scene.state.edgeY() <= 1080.0D - EFFECTIVE + TOL);
        }

        @Test
        @DisplayName("answers a target directly behind with bottom centre, and never moves off it")
        void directlyBehind() {
            Scene scene = new Scene();
            for (int i = 0; i < 60; i++) {
                scene.frame(0.0D, 0.0D, 10.0D, 16_000_000L);
                assertSame(EdgeIndicatorState.EdgeMode.EDGE, scene.state.mode());
                assertEquals(scene.centreX(), scene.state.edgeX(), TOL);
                assertEquals(1080.0D - EFFECTIVE, scene.state.edgeY(), TOL);
                assertSame(EdgeIndicatorState.EdgeSide.BOTTOM, scene.state.edgeSide());
            }
        }

        @Test
        @DisplayName("sends a target behind and to the right to the right edge, which is the way to turn")
        void behindLeftAndRight() {
            // A reversal of the 1.5.3 behaviour, on purpose. The old code mirrored the projected
            // direction because the divide by a negative w had already mirrored it, and called the
            // left edge "the way the player turns". The camera-space bearing needs no mirror: a target
            // behind your right shoulder is to your right, and the arrow now says so.
            Scene right = new Scene();
            right.frames(3, 5.0D, 0.0D, 10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, right.state.mode());
            assertSame(EdgeIndicatorState.EdgeSide.RIGHT, right.state.edgeSide());

            Scene left = new Scene();
            left.frames(3, -5.0D, 0.0D, 10.0D);
            assertSame(EdgeIndicatorState.EdgeSide.LEFT, left.state.edgeSide());
        }

        @Test
        @DisplayName("never shows the arrow at all when the mode is DISABLED")
        void disabledNeverEdges() {
            EdgeIndicatorState state = new EdgeIndicatorState();
            Matrix4f view = new Matrix4f().setLookAlong(0.0F, 0.0F, -1.0F, 0.0F, 1.0F, 0.0F);
            Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70.0D),
                    16.0F / 9.0F, 0.05F, 1000.0F);
            for (int i = 0; i < 5; i++) {
                state.update(1, OVERWORLD, OVERWORLD, 60.0D, 0.0D, -10.0D, 0.0D, 0.0D, 0.0D,
                        0.0D, 0.0D, -1.0D, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D,
                        view, projection, 1920, 1080, 2.0D, 1_000_000_000L + i * 16_000_000L,
                        tuning(McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED, false));
            }
            assertSame(EdgeIndicatorState.EdgeMode.WORLD, state.mode());
        }
    }

    @Nested
    @DisplayName("at the edges of the arithmetic")
    class Degenerate {

        @Test
        @DisplayName("neither flips nor produces NaN for a target sitting on the near plane")
        void nearPlaneBand() {
            Scene scene = new Scene();
            scene.frames(2, 0.1D, 0.0D, -0.06D);
            EdgeIndicatorState.EdgeMode settled = scene.state.mode();
            for (int i = 0; i < 30; i++) {
                // A hair in front of the near-plane epsilon, then a hair behind it.
                scene.frame(0.1D, 0.0D, i % 2 == 0 ? -0.04D : -0.06D, 16_000_000L);
                assertTrue(Double.isFinite(scene.state.edgeX()));
                assertTrue(Double.isFinite(scene.state.edgeY()));
                assertTrue(Double.isFinite(scene.state.angleRadians()));
                assertSame(settled, scene.state.mode(), "flipped on frame " + i);
            }
        }

        @Test
        @DisplayName("stays finite and settled for a target on the camera itself, where w is zero")
        void degenerateProjection() {
            Scene scene = new Scene();
            scene.frames(2, 0.0D, 0.0D, 0.0D);
            EdgeIndicatorState.EdgeMode settled = scene.state.mode();
            assertNotEquals(EdgeIndicatorState.EdgeMode.WORLD, settled);
            for (int i = 0; i < 10; i++) {
                scene.frame(0.0D, 0.0D, 0.0D, 16_000_000L);
                assertTrue(Double.isFinite(scene.state.edgeX()));
                assertTrue(Double.isFinite(scene.state.edgeY()));
                assertTrue(Double.isFinite(scene.state.angleRadians()));
                assertSame(settled, scene.state.mode());
            }
        }

        @Test
        @DisplayName("hides rather than casting a NaN or an infinity to a screen pixel")
        void nonFiniteInputHides() {
            Scene scene = new Scene();
            scene.frames(3, 0.0D, 0.0D, -10.0D);
            scene.frame(Double.NaN, 0.0D, -10.0D, 16_000_000L);
            assertSame(EdgeIndicatorState.EdgeMode.HIDDEN, scene.state.mode());

            scene.frame(Double.POSITIVE_INFINITY, 0.0D, -10.0D, 16_000_000L);
            assertSame(EdgeIndicatorState.EdgeMode.HIDDEN, scene.state.mode());
        }

        @Test
        @DisplayName("hides the moment the target is in another dimension")
        void dimensionChangeHides() {
            Scene scene = new Scene();
            scene.frames(3, 60.0D, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, scene.state.mode());
            scene.dimension = NETHER;
            scene.frame(60.0D, 0.0D, -10.0D, 16_000_000L);
            assertSame(EdgeIndicatorState.EdgeMode.HIDDEN, scene.state.mode());
            assertSame(EdgeIndicatorState.EdgeSide.NONE, scene.state.edgeSide());
        }
    }

    @Nested
    @DisplayName("across screens and settings")
    class Screens {

        @Test
        @DisplayName("keeps the whole icon inside a 320x240 window")
        void smallGui() {
            Scene scene = new Scene();
            scene.guiWidth = 320;
            scene.guiHeight = 240;
            scene.frames(3, 60.0D, 30.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, scene.state.mode());
            assertTrue(scene.state.edgeX() >= EFFECTIVE - TOL
                    && scene.state.edgeX() <= 320.0D - EFFECTIVE + TOL, "x " + scene.state.edgeX());
            assertTrue(scene.state.edgeY() >= EFFECTIVE - TOL
                    && scene.state.edgeY() <= 240.0D - EFFECTIVE + TOL, "y " + scene.state.edgeY());
        }

        @Test
        @DisplayName("intersects the right edge, not the top, on an ultrawide screen")
        void ultrawide() {
            Scene scene = new Scene();
            scene.guiWidth = 3440;
            scene.guiHeight = 1440;
            scene.frames(3, 60.0D, 2.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeSide.RIGHT, scene.state.edgeSide());
            assertEquals(3440.0D - EFFECTIVE, scene.state.edgeX(), TOL);
            assertTrue(scene.state.edgeY() > EFFECTIVE && scene.state.edgeY() < 1440.0D - EFFECTIVE);
        }

        @Test
        @DisplayName("recomputes against the new geometry when the GUI scale changes")
        void guiScaleChange() {
            Scene scene = new Scene();
            scene.frames(4, 60.0D, 0.0D, -10.0D);
            assertEquals(1920.0D - EFFECTIVE, scene.state.edgeX(), TOL);

            // A scale change halves the GUI in scaled pixels; the arrow belongs on the new edge on the
            // very next frame, not swept toward it from the old one.
            scene.guiScale = 4.0D;
            scene.guiWidth = 960;
            scene.guiHeight = 540;
            scene.frame(60.0D, 0.0D, -10.0D, 16_000_000L);
            assertEquals(960.0D - EFFECTIVE, scene.state.edgeX(), TOL);
            assertEquals(270.0D, scene.state.edgeY(), TOL);
        }

        @Test
        @DisplayName("projects against the new field of view when it changes")
        void fovChange() {
            Scene scene = new Scene();
            // On screen at 70 degrees, comfortably inside the frame.
            double x = scene.worldXForNdc(0.6D, 10.0D);
            scene.frames(4, x, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.WORLD, scene.state.mode());

            // The same target at 20 degrees is well outside it.
            scene.fovDegrees = 20.0D;
            scene.frames(4, x, 0.0D, -10.0D);
            assertSame(EdgeIndicatorState.EdgeMode.EDGE, scene.state.mode());
            assertSame(EdgeIndicatorState.EdgeSide.RIGHT, scene.state.edgeSide());
        }
    }

    @Nested
    @DisplayName("over time")
    class Temporal {

        @Test
        @DisplayName("does not flicker for a target jittering a pixel either side of the boundary")
        void boundaryJitter() {
            Scene scene = new Scene();
            double inside = scene.worldXForNdc(1.0D - 2.0D / 1920.0D, 10.0D);
            double outside = scene.worldXForNdc(1.0D + 2.0D / 1920.0D, 10.0D);
            scene.frames(3, inside, 0.0D, -10.0D);
            EdgeIndicatorState.EdgeMode start = scene.state.mode();
            int changes = 0;
            EdgeIndicatorState.EdgeMode previous = start;
            for (int i = 0; i < 100; i++) {
                scene.frame(i % 2 == 0 ? outside : inside, 0.0D, -10.0D, 16_000_000L);
                if (scene.state.mode() != previous) {
                    changes++;
                    previous = scene.state.mode();
                }
            }
            assertTrue(changes <= 1, "the marker changed mode " + changes + " times on one pixel");
        }

        @Test
        @DisplayName("follows the same trajectory at 30 and at 144 frames a second")
        void frameRateIndependence() {
            double[] slow = trajectory(30);
            double[] fast = trajectory(144);
            // Two percent of a 1920-pixel screen. Not zero, and it cannot be: the two machines sample
            // a moving target at different instants, so some of this gap is the trajectory and not the
            // filter. What matters is that it is a fraction of a percent of the screen rather than the
            // half-screen a per-frame blend factor would give.
            assertEquals(slow[0], fast[0], 40.0D);
            assertEquals(slow[1], fast[1], 40.0D);
        }

        /** Half a second of a target behind the camera swinging from high to low, at a given frame rate. */
        private double[] trajectory(int fps) {
            Scene scene = new Scene();
            long step = 1_000_000_000L / fps;
            int frames = fps / 2;
            scene.frames(2, 5.0D, 20.0D, 10.0D);
            for (int i = 0; i < frames; i++) {
                double t = (i + 1.0D) / frames;
                scene.frame(5.0D, 20.0D - 40.0D * t, 10.0D, step);
            }
            return new double[]{scene.state.edgeX(), scene.state.edgeY()};
        }

        @Test
        @DisplayName("snaps to a new target instead of sliding across the screen to it")
        void targetChangeSnaps() {
            Scene scene = new Scene();
            scene.frames(6, 60.0D, 0.0D, -10.0D);
            assertEquals(1920.0D - EFFECTIVE, scene.state.edgeX(), TOL);

            scene.identity = 2;
            for (int i = 0; i < 5; i++) {
                scene.frame(-60.0D, 0.0D, -10.0D, 16_000_000L);
                // Every frame is on the rectangle, and none of them is in the middle of the screen:
                // smoothing the position rather than the direction is what used to send the arrow
                // straight through the player's crosshair on a retarget.
                assertTrue(scene.state.edgeX() <= EFFECTIVE + TOL
                                || scene.state.edgeX() >= 1920.0D - EFFECTIVE - TOL,
                        "travelled through the interior at " + scene.state.edgeX());
            }
            assertSame(EdgeIndicatorState.EdgeSide.LEFT, scene.state.edgeSide());
            assertEquals(EFFECTIVE, scene.state.edgeX(), TOL);
        }

        @Test
        @DisplayName("snaps after a frame gap long enough to be a stutter")
        void longFrameGapSnaps() {
            Scene scene = new Scene();
            scene.frames(4, 60.0D, 0.0D, -10.0D);
            // Two seconds of nothing, then the target is on the other side.
            scene.frame(-60.0D, 0.0D, -10.0D, 2_000_000_000L);
            assertEquals(EFFECTIVE, scene.state.edgeX(), TOL);
        }
    }
}
