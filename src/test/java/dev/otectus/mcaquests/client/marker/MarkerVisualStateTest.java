package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marker's fades over time, checked without a clock.
 *
 * <p>These are the only animation in the marker, and the one thing they must never do is restart
 * while nothing has actually changed — a villager who walks is the same marker, and re-acquiring it
 * every time the server speaks would be a pulse once a second for as long as the quest lasts.
 */
class MarkerVisualStateTest {

    private static final UUID GIVER = UUID.nameUUIDFromBytes("giver".getBytes());
    private static final MarkerVisualState.Key BED = new MarkerVisualState.Key(
            new ResourceLocation("mcaquests", "bed"), GIVER, 7);
    private static final MarkerVisualState.Key FORGE = new MarkerVisualState.Key(
            new ResourceLocation("mcaquests", "forge"), GIVER, 9);

    @Nested
    @DisplayName("acquiring")
    class Acquiring {

        @Test
        @DisplayName("goes from nothing to everything over a hundred and sixty milliseconds")
        void acquiresOverTheBand() {
            MarkerVisualState state = new MarkerVisualState();
            assertFalse(state.observe(BED, 1000L, false), "the first target is not a retarget");
            assertEquals(0.0F, state.lifetimeAlpha(1000L));
            assertTrue(state.lifetimeAlpha(1080L) > 0.5F, "half way should be most of the way there");
            assertEquals(1.0F, state.lifetimeAlpha(1000L + MarkerVisualState.ACQUIRE_MS));
            assertEquals(1.0F, state.lifetimeAlpha(9000L));
        }

        @Test
        @DisplayName("does not restart when the same target simply moves")
        void sameKeyDoesNotRestart() {
            // A villager walking is not a new objective. Restarting here is a visible pulse once a
            // second, which is exactly the instability the marker rework was for.
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 0L, false);
            for (long t = 0L; t <= 2000L; t += 16L) {
                state.observe(BED, t, false);
            }
            assertEquals(1.0F, state.lifetimeAlpha(2000L));
        }
    }

    @Nested
    @DisplayName("clearing")
    class Clearing {

        @Test
        @DisplayName("fades the departed target out over a hundred and twenty milliseconds")
        void clearsOverTheBand() {
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 0L, false);
            assertTrue(state.clear(1000L), "there was something to clear");
            assertEquals(1.0F, state.previousAlpha(1000L));
            assertTrue(state.previousAlpha(1060L) < 1.0F);
            assertEquals(0.0F, state.previousAlpha(1000L + MarkerVisualState.CLEAR_MS));
            assertEquals(0.0F, state.lifetimeAlpha(1000L), "and nothing is current any more");
        }

        @Test
        @DisplayName("says there was nothing to clear when there was nothing to clear")
        void clearingNothingIsNothing() {
            assertFalse(new MarkerVisualState().clear(500L));
        }

        @Test
        @DisplayName("drops both slots at once when the marker is switched off")
        void resetIsImmediate() {
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 0L, false);
            state.observe(FORGE, 100L, false);
            state.reset();
            assertEquals(0.0F, state.lifetimeAlpha(100L));
            assertEquals(0.0F, state.previousAlpha(100L));
        }
    }

    @Nested
    @DisplayName("retargeting")
    class Retargeting {

        @Test
        @DisplayName("cross-fades: the old one going as the new one arrives")
        void crossFades() {
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 0L, false);
            assertTrue(state.observe(FORGE, 1000L, false), "a different target is a retarget");

            assertEquals(0.0F, state.lifetimeAlpha(1000L));
            assertEquals(1.0F, state.previousAlpha(1000L));

            float arriving = state.lifetimeAlpha(1060L);
            float leaving = state.previousAlpha(1060L);
            assertTrue(arriving > 0.0F && arriving < 1.0F, "arriving: " + arriving);
            assertTrue(leaving > 0.0F && leaving < 1.0F, "leaving: " + leaving);

            assertEquals(0.0F, state.previousAlpha(1000L + MarkerVisualState.CLEAR_MS));
            assertEquals(1.0F, state.lifetimeAlpha(1000L + MarkerVisualState.ACQUIRE_MS));
        }

        @Test
        @DisplayName("does not fade a half-acquired target out from full strength")
        void carriesTheAlphaItHad() {
            // Retargeting eighty milliseconds into an acquire must not make the outgoing marker
            // brighter than it ever was.
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 0L, false);
            float atSwitch = state.lifetimeAlpha(80L);
            state.observe(FORGE, 80L, false);
            assertEquals(atSwitch, state.previousAlpha(80L), 1.0E-6F);
        }
    }

    @Nested
    @DisplayName("reduced motion")
    class ReducedMotion {

        @Test
        @DisplayName("acquires immediately and cross-fades not at all")
        void isImmediate() {
            MarkerVisualState state = new MarkerVisualState();
            state.observe(BED, 1000L, true);
            assertEquals(1.0F, state.lifetimeAlpha(1000L));

            state.observe(FORGE, 2000L, true);
            assertEquals(1.0F, state.lifetimeAlpha(2000L));
            assertEquals(0.0F, state.previousAlpha(2000L), "nothing lingers");

            state.clear(3000L);
            assertEquals(0.0F, state.previousAlpha(3000L));
        }
    }

    @Nested
    @DisplayName("the frame state")
    class Frames {

        @Test
        @DisplayName("hands each frame's answer to the HUD exactly once")
        void staleFramesAreRejected() {
            // Otherwise a frame in which the renderer never ran -- no quest, GUI hidden, wrong
            // dimension -- would leave the HUD redrawing the last thing it was told, forever.
            MarkerFrameState state = new MarkerFrameState();
            assertFalse(state.consume(), "nothing has been published yet");

            state.publish(state.nextFrameId(4), true, false, 10.0D, 20.0D, 0.5D,
                    0x56B4E9, 42L, GuidanceKind.VILLAGER, 1.0F,
                    EdgeIndicatorState.EdgeSide.RIGHT);
            assertTrue(state.consume());
            assertFalse(state.consume(), "the same frame is not drawn twice");

            state.publish(state.nextFrameId(4), true, false, 11.0D, 20.0D, 0.5D,
                    0x56B4E9, 41L, GuidanceKind.VILLAGER, 1.0F,
                    EdgeIndicatorState.EdgeSide.RIGHT);
            assertTrue(state.consume(), "a second frame inside the same render tick is a new frame");
            assertEquals(41L, state.roundedDistance());
        }
    }
}
