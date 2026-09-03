package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world marker's arithmetic, which is the part of it that can be wrong in a way you would only
 * notice by standing in the right place.
 *
 * <p>Same bar as {@code PanelGeometryTest} and {@code ScrollViewTest}: no rendering, so the fade
 * curves and the projection maths are checked without a running game, and a marker that stays solid
 * as you walk into it fails here rather than in a bug report that says "it looks wrong sometimes".
 */
class MarkerGeometryTest {

    private static final int ARRIVE = 8;
    private static final int MAX = 256;

    @Nested
    @DisplayName("smoothstep")
    class Smoothstep {

        @Test
        @DisplayName("is exactly nothing and exactly everything at its edges")
        void edgesAreExact() {
            // Not "close to zero". A fade that ends at 0.004 leaves a marker faintly present forever.
            assertEquals(0.0D, MarkerGeometry.smoothstep(10.0D, 20.0D, 10.0D));
            assertEquals(0.0D, MarkerGeometry.smoothstep(10.0D, 20.0D, -100.0D));
            assertEquals(1.0D, MarkerGeometry.smoothstep(10.0D, 20.0D, 20.0D));
            assertEquals(1.0D, MarkerGeometry.smoothstep(10.0D, 20.0D, 1000.0D));
            assertEquals(0.5D, MarkerGeometry.smoothstep(10.0D, 20.0D, 15.0D), 1.0E-9D);
        }

        @Test
        @DisplayName("answers with a step when the two edges are the same, rather than dividing by zero")
        void degenerateEdgesStep() {
            assertEquals(0.0D, MarkerGeometry.smoothstep(10.0D, 10.0D, 9.9D));
            assertEquals(1.0D, MarkerGeometry.smoothstep(10.0D, 10.0D, 10.0D));
            // Inverted edges are the same case: a configuration nobody chose, answered without a NaN.
            assertEquals(1.0D, MarkerGeometry.smoothstep(20.0D, 10.0D, 15.0D));
        }
    }

    @Nested
    @DisplayName("the acquire curve")
    class Acquire {

        @Test
        @DisplayName("starts at nothing, ends at everything, and is mostly there early")
        void easesOut() {
            assertEquals(0.0D, MarkerGeometry.easeOutCubic(0.0D));
            assertEquals(1.0D, MarkerGeometry.easeOutCubic(1.0D));
            assertEquals(1.0D, MarkerGeometry.easeOutCubic(4.0D), "past the end is still the end");
            assertTrue(MarkerGeometry.easeOutCubic(0.5D) > 0.8D,
                    "half way through should be most of the way there");
        }
    }

    @Nested
    @DisplayName("the apparent size")
    class ApparentSize {

        @Test
        @DisplayName("stays between 18 and 24 pixels at every distance and every configured range")
        void staysWithinBounds() {
            for (int max : new int[]{16, 64, 256, 4096}) {
                for (double d = 0.0D; d <= max + 64.0D; d += 0.5D) {
                    double pixels = MarkerGeometry.apparentPixels(d, max);
                    assertTrue(pixels >= MarkerGeometry.MIN_APPARENT_PIXELS - 1.0E-9D
                                    && pixels <= MarkerGeometry.MAX_APPARENT_PIXELS + 1.0E-9D,
                            pixels + " px at " + d + " blocks with max " + max);
                }
            }
        }

        @Test
        @DisplayName("is largest up close and smallest at the far end, never the other way round")
        void shrinksWithDistance() {
            assertEquals(MarkerGeometry.MAX_APPARENT_PIXELS,
                    MarkerGeometry.apparentPixels(0.0D, MAX), 1.0E-9D);
            assertEquals(MarkerGeometry.MAX_APPARENT_PIXELS,
                    MarkerGeometry.apparentPixels(MarkerGeometry.SIZE_NEAR, MAX), 1.0E-9D);
            assertEquals(MarkerGeometry.MIN_APPARENT_PIXELS,
                    MarkerGeometry.apparentPixels(MAX, MAX), 1.0E-9D);
            assertTrue(MarkerGeometry.apparentPixels(60.0D, MAX)
                    > MarkerGeometry.apparentPixels(200.0D, MAX));
        }
    }

    @Nested
    @DisplayName("the world size")
    class WorldSize {

        /** A 70-degree field of view on a 1080-pixel-tall window. */
        private static final double M11 = 1.0D / Math.tan(Math.toRadians(35.0D));
        private static final int HEIGHT = 1080;

        @Test
        @DisplayName("grows with depth, because a pixel covers more world further away")
        void growsWithDepth() {
            double near = MarkerGeometry.worldPerPixel(5.0D, M11, HEIGHT);
            double far = MarkerGeometry.worldPerPixel(50.0D, M11, HEIGHT);
            assertTrue(far > near * 9.0D, "ten times the depth is ten times the world per pixel");
        }

        @Test
        @DisplayName("is capped at six blocks, so the marker never spans the terrain behind it")
        void isCapped() {
            double wpp = MarkerGeometry.worldPerPixel(4000.0D, M11, HEIGHT);
            assertEquals(MarkerGeometry.MAX_WORLD_SIZE,
                    MarkerGeometry.worldSize(200.0D, MAX, wpp), 1.0E-9D);
        }

        @Test
        @DisplayName("hands over to the HUD exactly when the cap has taken it below 18 pixels")
        void fallsBackAtTheThreshold() {
            // Just under the threshold: 18 px would need more than six blocks, so the world glyph
            // cannot be drawn at a readable size and the HUD draws it instead.
            double tooFar = MarkerGeometry.MAX_WORLD_SIZE / MarkerGeometry.MIN_APPARENT_PIXELS * 1.01D;
            assertTrue(MarkerGeometry.usesHudFallback(
                    MarkerGeometry.worldSize(200.0D, MAX, tooFar), tooFar));

            double fits = MarkerGeometry.MAX_WORLD_SIZE / MarkerGeometry.MIN_APPARENT_PIXELS * 0.99D;
            assertFalse(MarkerGeometry.usesHudFallback(
                    MarkerGeometry.worldSize(200.0D, MAX, fits), fits));
        }

        @Test
        @DisplayName("answers zero rather than infinity for a projection that has not been set up")
        void degenerateProjection() {
            assertEquals(0.0D, MarkerGeometry.worldPerPixel(10.0D, 0.0D, HEIGHT));
            assertEquals(0.0D, MarkerGeometry.worldPerPixel(10.0D, M11, 0));
            assertFalse(MarkerGeometry.usesHudFallback(0.0D, 0.0D));
        }
    }

    @Nested
    @DisplayName("the distance fades")
    class Fades {

        @Test
        @DisplayName("are nothing inside the arrival radius, so arriving removes the marker")
        void invisibleOnArrival() {
            // The whole point: walking up to the bed must take the marker away rather than leave it
            // standing in the player's face while they try to see what they came for.
            assertEquals(0.0F, MarkerGeometry.arrivalAlpha(0.0D, ARRIVE));
            assertEquals(0.0F, MarkerGeometry.arrivalAlpha(ARRIVE, ARRIVE));
            assertEquals(1.0F, MarkerGeometry.arrivalAlpha(ARRIVE + 8.0D, ARRIVE));
        }

        @Test
        @DisplayName("are nothing past the configured draw distance")
        void invisibleBeyondMax() {
            assertEquals(0.0F, MarkerGeometry.farAlpha(MAX, ARRIVE, MAX));
            assertEquals(0.0F, MarkerGeometry.farAlpha(MAX + 1000.0D, ARRIVE, MAX));
            assertEquals(1.0F, MarkerGeometry.farAlpha(MAX - 32.0D, ARRIVE, MAX));
        }

        @Test
        @DisplayName("never leave the 0..1 range, whatever the config says")
        void stayInRange() {
            for (int max : new int[]{16, 24, 64, 256, 4096}) {
                for (double d = 0.0D; d <= max + 32.0D; d += 0.5D) {
                    float alpha = Math.min(MarkerGeometry.arrivalAlpha(d, ARRIVE),
                            MarkerGeometry.farAlpha(d, ARRIVE, max));
                    assertTrue(alpha >= 0.0F && alpha <= 1.0F,
                            "alpha " + alpha + " at distance " + d + " with max " + max);
                }
            }
        }

        @Test
        @DisplayName("hold the far band above the arrival radius where the two would overlap")
        void overlappingBandsAreClamped() {
            // A draw distance of 16 with an arrival radius of 8 would otherwise start fading out at
            // -16 blocks, which is to say the marker would never be drawn at full strength at all.
            assertEquals(1.0F, MarkerGeometry.farAlpha(ARRIVE + 0.5D, ARRIVE, 16));
            for (double d = 0.0D; d <= 40.0D; d += 0.5D) {
                float alpha = Math.min(MarkerGeometry.arrivalAlpha(d, 32),
                        MarkerGeometry.farAlpha(d, 32, 16));
                assertEquals(0.0F, alpha, "a range inside the arrival radius shows nothing, at " + d);
            }
        }
    }

    @Nested
    @DisplayName("the ground ring")
    class Ring {

        @Test
        @DisplayName("scales with the target but never becomes a dot or a crop circle")
        void isClamped() {
            assertEquals(0.30D, MarkerGeometry.ringRadius(0.0D), 1.0E-9D);
            assertEquals(0.30D, MarkerGeometry.ringRadius(0.3D), 1.0E-9D);
            assertEquals(0.39D, MarkerGeometry.ringRadius(0.6D), 1.0E-9D);
            assertEquals(0.90D, MarkerGeometry.ringRadius(4.0D), 1.0E-9D);
        }
    }

    @Nested
    @DisplayName("the label")
    class Label {

        @Test
        @DisplayName("appears within forty-eight blocks by default, and either always or never on request")
        void followsTheSetting() {
            assertTrue(MarkerGeometry.labelVisible(
                    McaQuestsConfig.Client.MarkerLabels.NEARBY, 48.0D));
            assertFalse(MarkerGeometry.labelVisible(
                    McaQuestsConfig.Client.MarkerLabels.NEARBY, 48.1D));
            assertTrue(MarkerGeometry.labelVisible(
                    McaQuestsConfig.Client.MarkerLabels.ALWAYS, 4000.0D));
            assertFalse(MarkerGeometry.labelVisible(
                    McaQuestsConfig.Client.MarkerLabels.NEVER, 0.0D));
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
}
