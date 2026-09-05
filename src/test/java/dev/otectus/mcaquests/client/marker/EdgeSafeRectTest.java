package dev.otectus.mcaquests.client.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one piece of geometry the edge indicator cannot be wrong about.
 *
 * <p>Every arrow the player sees is a point on this rectangle, so the property that matters is not a
 * particular coordinate but that <em>no</em> finite direction produces a non-finite one, or one off
 * the rectangle. The property test is therefore over ten thousand random directions rather than the
 * handful a person would think to write down; the handful are here too, because a property test that
 * passes says nothing about whether left is left.
 */
class EdgeSafeRectTest {

    private static final double TOL = 1.0E-6D;

    private static EdgeSafeRect rect(double width, double height, double inset) {
        EdgeSafeRect r = new EdgeSafeRect();
        r.set(inset, inset, width - inset, height - inset);
        return r;
    }

    @Nested
    @DisplayName("intersecting from the centre")
    class Intersecting {

        @Test
        @DisplayName("lands on the boundary, finite and inside, for ten thousand random directions")
        void propertyOverRandomDirections() {
            // Seeded: a failure here has to be reproducible, and an unseeded random one that appears
            // once in a thousand builds is worse than no test at all.
            Random random = new Random(20250904L);
            EdgeSafeRect r = rect(1920.0D, 1080.0D, 18.0D);
            for (int i = 0; i < 10_000; i++) {
                double dx = random.nextDouble() * 200.0D - 100.0D;
                double dy = random.nextDouble() * 200.0D - 100.0D;
                if (Math.abs(dx) < 1.0E-6D && Math.abs(dy) < 1.0E-6D) {
                    continue;
                }
                assertTrue(r.intersectFromCenter(dx, dy), "usable direction " + dx + "," + dy);
                assertTrue(Double.isFinite(r.outX()), "x for " + dx + "," + dy);
                assertTrue(Double.isFinite(r.outY()), "y for " + dx + "," + dy);
                assertTrue(r.isOnBoundary(r.outX(), r.outY(), TOL),
                        "off the boundary at " + r.outX() + "," + r.outY());
                assertTrue(r.containsWithMargin(r.outX(), r.outY(), -TOL),
                        "outside the rectangle at " + r.outX() + "," + r.outY());
            }
        }

        @Test
        @DisplayName("puts a direction along each axis at the middle of that edge")
        void axisCases() {
            EdgeSafeRect r = rect(400.0D, 300.0D, 18.0D);

            r.intersectFromCenter(1.0D, 0.0D);
            assertEquals(400.0D - 18.0D, r.outX(), TOL);
            assertEquals(150.0D, r.outY(), TOL);

            r.intersectFromCenter(-1.0D, 0.0D);
            assertEquals(18.0D, r.outX(), TOL);

            // Screen Y grows downward, so +Y is the bottom edge.
            r.intersectFromCenter(0.0D, 1.0D);
            assertEquals(300.0D - 18.0D, r.outY(), TOL);
            assertEquals(200.0D, r.outX(), TOL);

            r.intersectFromCenter(0.0D, -1.0D);
            assertEquals(18.0D, r.outY(), TOL);
        }

        @Test
        @DisplayName("reaches the exact corner of a square rectangle on a 45-degree direction")
        void cornerCases() {
            EdgeSafeRect r = rect(400.0D, 400.0D, 20.0D);
            r.intersectFromCenter(1.0D, 1.0D);
            assertEquals(380.0D, r.outX(), TOL);
            assertEquals(380.0D, r.outY(), TOL);

            r.intersectFromCenter(-1.0D, -1.0D);
            assertEquals(20.0D, r.outX(), TOL);
            assertEquals(20.0D, r.outY(), TOL);
        }

        @Test
        @DisplayName("keeps the point inside a 320x240 window and inside an ultrawide one")
        void smallAndUltrawide() {
            EdgeSafeRect small = rect(320.0D, 240.0D, 18.0D);
            small.intersectFromCenter(3.0D, 1.0D);
            assertTrue(small.containsWithMargin(small.outX(), small.outY(), -TOL));
            assertEquals(320.0D - 18.0D, small.outX(), TOL);

            EdgeSafeRect wide = rect(3440.0D, 1440.0D, 18.0D);
            // A shallow direction on an ultrawide screen leaves by the top, not the side: the vertical
            // half is far smaller than the horizontal one, which is exactly what a 16:9 assumption gets
            // wrong.
            wide.intersectFromCenter(1.0D, -1.0D);
            assertEquals(18.0D, wide.outY(), TOL);
            assertTrue(wide.containsWithMargin(wide.outX(), wide.outY(), -TOL));
        }

        @Test
        @DisplayName("refuses a zero or non-finite direction rather than dividing by it")
        void unusableDirections() {
            EdgeSafeRect r = rect(400.0D, 300.0D, 18.0D);
            assertFalse(r.intersectFromCenter(0.0D, 0.0D));
            assertEquals(200.0D, r.outX(), TOL);
            assertEquals(150.0D, r.outY(), TOL);
            assertFalse(r.intersectFromCenter(Double.NaN, 1.0D));
            assertFalse(r.intersectFromCenter(1.0D, Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName("names the vertical edge for a sideways direction and the horizontal one for a steep one")
        void dominantAxis() {
            EdgeSafeRect r = rect(400.0D, 300.0D, 18.0D);
            assertTrue(r.hitsVerticalEdge(1.0D, 0.1D));
            assertFalse(r.hitsVerticalEdge(0.1D, 1.0D));
        }
    }

    @Nested
    @DisplayName("as a rectangle")
    class Shape {

        @Test
        @DisplayName("collapses to its centre rather than inverting when the inset swallows the window")
        void degenerate() {
            EdgeSafeRect r = new EdgeSafeRect();
            r.set(100.0D, 100.0D, 40.0D, 40.0D);
            assertEquals(0.0D, r.halfWidth(), TOL);
            assertEquals(70.0D, r.centerX(), TOL);
            r.intersectFromCenter(1.0D, 1.0D);
            assertEquals(70.0D, r.outX(), TOL);
            assertEquals(70.0D, r.outY(), TOL);
        }

        @Test
        @DisplayName("treats the centre of the screen as inside and a point past the inset as outside")
        void containsWithMargin() {
            // The case the old clamp covered as "inside the frame but under the inset is off screen":
            // a point 5px from the edge is not inside a 18px margin, so the world glyph would be drawn
            // half under the hotbar and must give way to the indicator.
            EdgeSafeRect r = new EdgeSafeRect();
            r.set(0.0D, 0.0D, 400.0D, 300.0D);
            assertTrue(r.containsWithMargin(200.0D, 150.0D, 18.0D));
            assertFalse(r.containsWithMargin(200.0D, 295.0D, 18.0D));
            assertTrue(r.containsWithMargin(200.0D, 295.0D, 0.0D));
        }

        @Test
        @DisplayName("moves its edges with the GUI scale it was set at")
        void followsGuiScale() {
            // The old clamp test asserted the same thing through two GUI sizes: the rectangle is in
            // GUI-scaled pixels, so the same direction lands at a different pixel on a different scale.
            EdgeSafeRect small = rect(200.0D, 150.0D, 18.0D);
            small.intersectFromCenter(1.0D, 0.0D);
            assertEquals(200.0D - 18.0D, small.outX(), TOL);

            EdgeSafeRect large = rect(800.0D, 600.0D, 18.0D);
            large.intersectFromCenter(1.0D, 0.0D);
            assertEquals(800.0D - 18.0D, large.outX(), TOL);
        }
    }
}
