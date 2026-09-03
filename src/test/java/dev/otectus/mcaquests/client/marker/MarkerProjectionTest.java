package dev.otectus.mcaquests.client.marker;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The projection behind the edge indicator, pinned down before anything renders with it.
 *
 * <p>Written first, and deliberately. JOML's {@code Vector4f.mul(Matrix4fc)} multiplies the matrix by
 * the vector rather than the other way round, and getting that backwards produces an indicator that
 * is plausibly wrong — it points somewhere, just not at the target — which is the hardest kind of
 * render bug to notice and the easiest to check here.
 *
 * <p>The camera looks down <b>-Z</b>, as it does in Minecraft, so a target in front of the player has
 * a negative Z in camera space.
 */
class MarkerProjectionTest {

    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 300;
    private static final double INSET = 18.0D;

    /** A plain 70-degree perspective, the game's default field of view. */
    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(70.0D),
                (float) GUI_WIDTH / GUI_HEIGHT, 0.05F, 1000.0F);
    }

    private static MarkerProjection.Projection project(double x, double y, double z) {
        return MarkerProjection.project(x, y, z, new Matrix4f(), perspective());
    }

    @Nested
    @DisplayName("projecting")
    class Projecting {

        @Test
        @DisplayName("puts a target straight ahead at the centre of the screen, in front of the camera")
        void centreFront() {
            MarkerProjection.Projection p = project(0.0D, 0.0D, -10.0D);
            assertFalse(p.behind(), "a target down -Z is in front of the camera");
            assertEquals(0.0D, p.ndcX(), 1.0E-6D);
            assertEquals(0.0D, p.ndcY(), 1.0E-6D);
        }

        @Test
        @DisplayName("puts a target to the right at positive X and one to the left at negative X")
        void leftAndRight() {
            assertTrue(project(5.0D, 0.0D, -10.0D).ndcX() > 0.0D);
            assertTrue(project(-5.0D, 0.0D, -10.0D).ndcX() < 0.0D);
        }

        @Test
        @DisplayName("puts a target above the camera at positive Y, which is up in clip space")
        void upAndDown() {
            assertTrue(project(0.0D, 5.0D, -10.0D).ndcY() > 0.0D);
            assertTrue(project(0.0D, -5.0D, -10.0D).ndcY() < 0.0D);
        }

        @Test
        @DisplayName("reports a target behind the camera as behind")
        void behind() {
            assertTrue(project(0.0D, 0.0D, 10.0D).behind());
            assertTrue(project(5.0D, 0.0D, 10.0D).behind());
        }
    }

    @Nested
    @DisplayName("clamping")
    class Clamping {

        @Test
        @DisplayName("leaves a target in the middle of the screen alone, and says it is on screen")
        void centreIsOnScreen() {
            MarkerProjection.EdgePoint point = MarkerProjection.clamp(
                    project(0.0D, 0.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertTrue(point.onScreen());
            assertEquals(GUI_WIDTH / 2.0D, point.x(), 1.0E-4D);
            assertEquals(GUI_HEIGHT / 2.0D, point.y(), 1.0E-4D);
        }

        @Test
        @DisplayName("puts a target off each edge on that edge, inside the safe inset")
        void eachEdge() {
            // Far off to one side at a short depth, so the projected point is well outside the frame.
            MarkerProjection.EdgePoint right = MarkerProjection.clamp(
                    project(60.0D, 0.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertFalse(right.onScreen());
            assertEquals(GUI_WIDTH - INSET, right.x(), 1.0E-4D);
            assertEquals(GUI_HEIGHT / 2.0D, right.y(), 1.0E-4D);

            MarkerProjection.EdgePoint left = MarkerProjection.clamp(
                    project(-60.0D, 0.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertEquals(INSET, left.x(), 1.0E-4D);

            // Screen Y grows downward, so a target above the camera clamps to the top edge.
            MarkerProjection.EdgePoint above = MarkerProjection.clamp(
                    project(0.0D, 60.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertEquals(INSET, above.y(), 1.0E-4D);
            assertEquals(GUI_WIDTH / 2.0D, above.x(), 1.0E-4D);

            MarkerProjection.EdgePoint below = MarkerProjection.clamp(
                    project(0.0D, -60.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertEquals(GUI_HEIGHT - INSET, below.y(), 1.0E-4D);
        }

        @Test
        @DisplayName("puts a diagonal target in a corner, and never outside the safe rectangle")
        void diagonalCorner() {
            MarkerProjection.EdgePoint point = MarkerProjection.clamp(
                    project(60.0D, 60.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertFalse(point.onScreen());
            assertTrue(point.x() >= INSET - 1.0E-4D && point.x() <= GUI_WIDTH - INSET + 1.0E-4D,
                    "x " + point.x());
            assertTrue(point.y() >= INSET - 1.0E-4D && point.y() <= GUI_HEIGHT - INSET + 1.0E-4D,
                    "y " + point.y());
            // Up and to the right: the upper-right corner, not the lower-left one.
            assertTrue(point.x() > GUI_WIDTH / 2.0D);
            assertTrue(point.y() < GUI_HEIGHT / 2.0D);
        }

        @Test
        @DisplayName("sends a target behind and to the right to the left edge, so turning follows it")
        void behindInverts() {
            MarkerProjection.EdgePoint point = MarkerProjection.clamp(
                    project(5.0D, 0.0D, 10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertFalse(point.onScreen());
            assertEquals(INSET, point.x(), 1.0E-4D);
        }

        @Test
        @DisplayName("sends a target directly behind the camera to the bottom centre")
        void directlyBehindGoesToBottomCentre() {
            // Nothing about a target exactly behind the player says left or right, so the answer has
            // to be chosen rather than computed -- otherwise the arrow flickers between the two edges
            // on the noise of a mouse.
            MarkerProjection.EdgePoint point = MarkerProjection.clamp(
                    project(0.0D, 0.0D, 10.0D), GUI_WIDTH, GUI_HEIGHT, INSET);
            assertFalse(point.onScreen());
            assertEquals(GUI_WIDTH / 2.0D, point.x(), 1.0E-4D);
            assertEquals(GUI_HEIGHT - INSET, point.y(), 1.0E-4D);
        }

        @Test
        @DisplayName("treats a target inside the frame but under the inset as off screen")
        void insetCountsAsOffScreen() {
            // Otherwise the glyph is drawn half under the hotbar or the minimap and the indicator that
            // would have replaced it never appears.
            MarkerProjection.Projection p = new MarkerProjection.Projection(false, 0.0D, -0.95D);
            MarkerProjection.EdgePoint point = MarkerProjection.clamp(p, GUI_WIDTH, GUI_HEIGHT, INSET);
            assertFalse(point.onScreen());
            assertEquals(GUI_HEIGHT - INSET, point.y(), 1.0E-4D);
        }

        @Test
        @DisplayName("works in GUI-scaled pixels, so a different scale moves the edge with it")
        void followsGuiScale() {
            MarkerProjection.Projection p = project(60.0D, 0.0D, -10.0D);
            assertEquals(200.0D - INSET,
                    MarkerProjection.clamp(p, 200, 150, INSET).x(), 1.0E-4D);
            assertEquals(800.0D - INSET,
                    MarkerProjection.clamp(p, 800, 600, INSET).x(), 1.0E-4D);
        }

        @Test
        @DisplayName("gives an angle that points at the target from the centre of the screen")
        void anglePointsAtTheTarget() {
            // Zero to the right, growing clockwise, because screen Y grows downward.
            assertEquals(0.0D, MarkerProjection.clamp(
                    project(60.0D, 0.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET).angleRadians(),
                    1.0E-4D);
            assertEquals(-Math.PI / 2.0D, MarkerProjection.clamp(
                    project(0.0D, 60.0D, -10.0D), GUI_WIDTH, GUI_HEIGHT, INSET).angleRadians(),
                    1.0E-4D);
        }
    }
}
