package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind where the marker stands, which is where both reported marker bugs lived.
 *
 * <p>Neither was visible in a test before: the anchor was computed inline in the renderer, so "the
 * icon is twenty-five blocks up in the sky" and "the icon climbs an entity height every tick" were
 * only observable by standing in front of a villager and watching. These cases are the observation.
 */
class MarkerAnchorResolverTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final double EPSILON = 1.0E-9D;

    @Nested
    @DisplayName("a loaded entity")
    class LoadedEntity {

        /** One tick of movement: a metre east, half a metre up, two metres north. */
        private MarkerAnchor at(float partialTick) {
            return MarkerAnchorResolver.forEntity(10.0D, 64.0D, -20.0D, 11.0D, 64.5D, -22.0D,
                    0.6F, 1.95F, partialTick);
        }

        @Test
        @DisplayName("interpolates all three axes by exactly the partial tick")
        void lerpsEveryAxis() {
            // X and Z were not interpolated at all before this, so the marker stepped sideways at
            // 20 Hz while the camera moved smoothly past it.
            assertEquals(10.0D, at(0.0F).x(), EPSILON);
            assertEquals(10.25D, at(0.25F).x(), EPSILON);
            assertEquals(10.5D, at(0.5F).x(), EPSILON);
            assertEquals(10.75D, at(0.75F).x(), EPSILON);
            assertEquals(11.0D, at(1.0F).x(), EPSILON);

            assertEquals(64.0D, at(0.0F).baseY(), EPSILON);
            assertEquals(64.125D, at(0.25F).baseY(), EPSILON);
            assertEquals(64.25D, at(0.5F).baseY(), EPSILON);
            assertEquals(64.375D, at(0.75F).baseY(), EPSILON);
            assertEquals(64.5D, at(1.0F).baseY(), EPSILON);

            assertEquals(-20.0D, at(0.0F).z(), EPSILON);
            assertEquals(-20.5D, at(0.25F).z(), EPSILON);
            assertEquals(-21.0D, at(0.5F).z(), EPSILON);
            assertEquals(-21.5D, at(0.75F).z(), EPSILON);
            assertEquals(-22.0D, at(1.0F).z(), EPSILON);
        }

        @Test
        @DisplayName("puts the glyph 72% up the bounding box, whatever its height")
        void glyphSitsOnTheBody() {
            // The three shapes that matter: a baby, an adult, and something tall. Each anchor must
            // land inside its own box -- the bug shipped an anchor a whole height above the top.
            for (float height : new float[] {0.975F, 1.95F, 3.0F}) {
                MarkerAnchor anchor = MarkerAnchorResolver.forEntity(0.0D, 64.0D, 0.0D,
                        0.0D, 64.0D, 0.0D, 0.6F, height, 1.0F);
                assertEquals(64.0D + height * 0.72D, anchor.glyphY(), 1.0E-6D,
                        "glyph must sit at 72% of a " + height + "-block box");
                assertTrue(anchor.glyphY() < 64.0D + height,
                        "the glyph must stay inside the box, not float above it");
                assertEquals(MarkerAnchor.VerticalAlignment.CENTER_ON_BODY, anchor.alignment());
            }
        }

        @Test
        @DisplayName("clamps a partial tick that arrives outside its range")
        void clampsPartialTick() {
            assertEquals(10.0D, at(-0.5F).x(), EPSILON);
            assertEquals(11.0D, at(2.0F).x(), EPSILON);
        }
    }

    @Nested
    @DisplayName("a fixed position")
    class FixedPosition {

        private final BlockPos pos = new BlockPos(8, 64, -3);

        @Test
        @DisplayName("stands on the top of the block at the position")
        void usesTheShapeAtThePosition() {
            // A bed is nine sixteenths tall, and the marker belongs on it rather than at its foot.
            VoxelShape bed = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5625D, 1.0D);
            MarkerAnchor anchor = MarkerAnchorResolver.forFixed(pos, bed, Shapes.block());

            assertEquals(64.5625D, anchor.baseY(), EPSILON);
            assertEquals(anchor.baseY(), anchor.glyphY(), EPSILON);
            assertEquals(MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE, anchor.alignment());
            assertEquals(8.5D, anchor.x(), EPSILON, "centred on the block, not on its corner");
            assertEquals(-2.5D, anchor.z(), EPSILON);
            assertFalse(anchor.approximate());
        }

        @Test
        @DisplayName("falls to the block below when the position itself is air")
        void usesTheShapeBelow() {
            // The usual case: the objective names the space a player stands in, not the floor.
            MarkerAnchor anchor = MarkerAnchorResolver.forFixed(pos, Shapes.empty(), Shapes.block());

            assertEquals(64.0D, anchor.baseY(), EPSILON, "the top of the block one below");
            assertFalse(anchor.approximate());
        }

        @Test
        @DisplayName("stays at the coordinate, and says so, when nothing supports it")
        void nothingToStandOn() {
            // Mid-air. No height map is consulted and nothing scans downward: the marker admits the
            // height is a guess instead of spending a chunk lookup every frame to dress it up.
            MarkerAnchor anchor = MarkerAnchorResolver.forFixed(pos, Shapes.empty(), Shapes.empty());

            assertEquals(64.0D, anchor.baseY(), EPSILON);
            assertTrue(anchor.approximate());
        }
    }

    @Nested
    @DisplayName("an entity the client cannot see")
    class UnloadedEntity {

        private final BlockPos pos = new BlockPos(100, 70, 100);

        @Test
        @DisplayName("keeps the glyph on the body using the transmitted height")
        void usesTheTransmittedHeight() {
            MarkerAnchor anchor = MarkerAnchorResolver.forUnloadedEntity(pos, 1.95F);

            assertEquals(70.0D + 1.95D * 0.72D, anchor.glyphY(), 1.0E-6D);
            assertEquals(MarkerAnchor.VerticalAlignment.CENTER_ON_BODY, anchor.alignment());
            assertTrue(anchor.approximate(), "a last-known position is not a live reading");
        }

        @Test
        @DisplayName("stands on the coordinate when the height is missing or corrupt")
        void corruptHeightFallsBackToTheSurface() {
            // Zero is what an older server sends and what the decoder clamps a nonsense value to.
            for (float height : new float[] {0.0F, -1.0F, Float.NaN}) {
                MarkerAnchor anchor = MarkerAnchorResolver.forUnloadedEntity(pos, height);
                assertEquals(MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE, anchor.alignment(),
                        "height " + height + " must not produce a body anchor");
                assertEquals(70.0D, anchor.glyphY(), EPSILON);
            }
        }
    }
}
