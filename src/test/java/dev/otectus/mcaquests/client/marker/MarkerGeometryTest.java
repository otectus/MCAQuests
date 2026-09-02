package dev.otectus.mcaquests.client.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world marker's arithmetic, which is the part of it that can be wrong in a way you would only
 * notice by standing in the right place.
 *
 * <p>Same bar as {@code PanelGeometryTest} and {@code ScrollViewTest}: no Minecraft types, so the
 * fade curve and the distance maths are checked without a running game, and a marker that stays solid
 * as you walk into it fails here rather than in a bug report that says "it looks wrong sometimes".
 */
class MarkerGeometryTest {

    private static final int ARRIVE = 8;
    private static final int MAX = 256;

    @Nested
    @DisplayName("the fade")
    class Fade {

        @Test
        @DisplayName("is nothing inside the arrival radius, so arriving removes the marker")
        void invisibleOnArrival() {
            // The whole point: walking up to the bed must take the beam away rather than leave it
            // standing in the player's face while they try to see what they came for.
            assertEquals(0.0F, MarkerGeometry.alpha(0.0D, ARRIVE, MAX));
            assertEquals(0.0F, MarkerGeometry.alpha(ARRIVE, ARRIVE, MAX));
        }

        @Test
        @DisplayName("is nothing past the configured draw distance")
        void invisibleBeyondMax() {
            assertEquals(0.0F, MarkerGeometry.alpha(MAX, ARRIVE, MAX));
            assertEquals(0.0F, MarkerGeometry.alpha(MAX + 1000.0D, ARRIVE, MAX));
        }

        @Test
        @DisplayName("is full in the middle, and ramps at both ends rather than snapping")
        void rampsAtBothEnds() {
            assertEquals(1.0F, MarkerGeometry.alpha(128.0D, ARRIVE, MAX));

            float justOutside = MarkerGeometry.alpha(ARRIVE + 1.0D, ARRIVE, MAX);
            assertTrue(justOutside > 0.0F && justOutside < 1.0F,
                    "a step outside the arrival radius should be faint, not solid: " + justOutside);

            float nearMax = MarkerGeometry.alpha(MAX - 1.0D, ARRIVE, MAX);
            assertTrue(nearMax > 0.0F && nearMax < 1.0F,
                    "the far edge should fade out, not blink off: " + nearMax);
        }

        @Test
        @DisplayName("never leaves the 0..1 range, whatever the config says")
        void staysInRange() {
            // A draw distance set below the fade bands is the case that would otherwise produce an
            // alpha above 1 or below 0 and, with it, a beam that renders as a solid white slab.
            for (int max : new int[]{16, 24, 64, 256, 4096}) {
                for (double d = 0.0D; d <= max + 32.0D; d += 0.5D) {
                    float alpha = MarkerGeometry.alpha(d, ARRIVE, max);
                    assertTrue(alpha >= 0.0F && alpha <= 1.0F,
                            "alpha " + alpha + " at distance " + d + " with max " + max);
                }
            }
        }

        @Test
        @DisplayName("is zero throughout when the draw distance is inside the arrival radius")
        void degenerateRangeShowsNothing() {
            // Not a configuration anyone should choose, but it must produce no marker rather than a
            // marker that flickers between fully on and fully off as the player breathes.
            for (double d = 0.0D; d <= 40.0D; d += 0.5D) {
                assertEquals(0.0F, MarkerGeometry.alpha(d, 32, 16), "distance " + d);
            }
        }
    }

    @Nested
    @DisplayName("the distance")
    class Distance {

        @Test
        @DisplayName("ignores height, so a target down a ravine is not reported as further to walk")
        void isHorizontal() {
            assertEquals(5.0D, MarkerGeometry.horizontalDistance(3.0D, 4.0D), 1.0E-9D);
            assertEquals(0.0D, MarkerGeometry.horizontalDistance(0.0D, 0.0D), 1.0E-9D);
        }

        @Test
        @DisplayName("is the same in every direction")
        void isSymmetric() {
            assertEquals(MarkerGeometry.horizontalDistance(12.0D, -5.0D),
                    MarkerGeometry.horizontalDistance(-12.0D, 5.0D), 1.0E-9D);
        }
    }

    @Test
    @DisplayName("the label always clears the beam")
    void labelClearsTheBeam() {
        for (double d = 0.0D; d <= 4096.0D; d += 16.0D) {
            assertTrue(MarkerGeometry.labelHeight(d) > MarkerGeometry.BEAM_HEIGHT,
                    "the label must not sit inside the beam at distance " + d);
        }
    }
}
