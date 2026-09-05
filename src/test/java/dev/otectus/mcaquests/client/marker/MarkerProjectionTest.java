package dev.otectus.mcaquests.client.marker;

import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.joml.Vector4f;

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
 *
 * <p>The edge-clamping this class used to do moved to {@link EdgeSafeRect} and
 * {@link EdgeIndicatorState} in 1.5.4, and so did its cases: the geometry is in
 * {@code EdgeSafeRectTest}, the decisions made from it in {@code EdgeIndicatorStateTest}.
 */
class MarkerProjectionTest {

    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 300;

    /** A plain 70-degree perspective, the game's default field of view. */
    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(70.0D),
                (float) GUI_WIDTH / GUI_HEIGHT, 0.05F, 1000.0F);
    }

    private static MarkerProjection.Projection project(double x, double y, double z) {
        return MarkerProjection.project(x, y, z, new Matrix4f(), perspective());
    }

    @Nested
    @DisplayName("projecting into a scratch vector")
    class ProjectingInto {

        @Test
        @DisplayName("agrees with the allocating projection for a target in front")
        void agreesWithProject() {
            Vector4f scratch = new Vector4f();
            assertTrue(MarkerProjection.projectInto(5.0D, 2.0D, -10.0D,
                    new Matrix4f(), perspective(), scratch));
            MarkerProjection.Projection p = project(5.0D, 2.0D, -10.0D);
            assertEquals(p.ndcX(), scratch.x(), 1.0E-5D);
            assertEquals(p.ndcY(), scratch.y(), 1.0E-5D);
        }

        @Test
        @DisplayName("refuses a target behind the camera rather than reporting mirrored coordinates")
        void refusesBehind() {
            // The caller has a camera-space bearing for that case, which is better than sign noise on
            // a negative w choosing an edge.
            assertFalse(MarkerProjection.projectInto(5.0D, 0.0D, 10.0D,
                    new Matrix4f(), perspective(), new Vector4f()));
        }

        @Test
        @DisplayName("refuses a non-finite input instead of passing NaN on to a screen coordinate")
        void refusesNonFinite() {
            assertFalse(MarkerProjection.projectInto(Double.NaN, 0.0D, -10.0D,
                    new Matrix4f(), perspective(), new Vector4f()));
        }
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

}
