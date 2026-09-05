package dev.otectus.mcaquests.client.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy that keeps the optional occluded mode from being a raycast a frame.
 *
 * <p>Only the policy is tested: the raycast itself is one vanilla call and needs a world, while the
 * rate limiting, the triggers and the debounce are the part that can be wrong in a way nobody notices
 * until a profiler is open. The clock is a counter and the world is a boolean.
 */
class MarkerOcclusionSamplerTest {

    /** Counts how many times the sampler actually asked the world anything. */
    private static final class Casts implements BooleanSupplier {

        private final boolean answer;
        private int count;

        private Casts(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean getAsBoolean() {
            count++;
            return answer;
        }
    }

    @Test
    @DisplayName("never casts more than twenty times in a simulated second, whatever the frame rate")
    void hardCapPerSecond() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        Casts casts = new Casts(false);
        // 144 frames a second, the camera moving far enough every frame to trigger a sample, and the
        // configured interval set to zero so only the cap can stop it.
        for (int i = 0; i < 144; i++) {
            long now = (long) (i * (1000.0D / 144.0D));
            sampler.sample(now, 0L, i * 5.0D, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D,
                    0.0D, 0.0D, -10.0D, casts);
        }
        assertTrue(casts.count <= 20, "cast " + casts.count + " times in one second");
    }

    @Test
    @DisplayName("casts nothing at all while the camera and the target both stand still")
    void nothingMovedNothingAsked() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        Casts casts = new Casts(false);
        for (int i = 0; i < 200; i++) {
            sampler.sample(i * 50L, 50L, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D,
                    0.0D, 0.0D, -10.0D, casts);
        }
        // One: the very first frame, which has nothing to compare against.
        assertEquals(1, casts.count);
    }

    @Test
    @DisplayName("respects the configured interval even when the camera is moving constantly")
    void intervalHolds() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        Casts casts = new Casts(false);
        // Ten simulated milliseconds a frame, a moving camera, a fifty-millisecond interval.
        for (int i = 0; i < 50; i++) {
            sampler.sample(i * 10L, 50L, i * 1.0D, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D,
                    0.0D, 0.0D, -10.0D, casts);
        }
        assertTrue(casts.count <= 11, "cast " + casts.count + " times in 500ms at a 50ms interval");
    }

    @Test
    @DisplayName("asks again when the camera turns two degrees without moving")
    void rotationTriggers() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        Casts casts = new Casts(false);
        sampler.sample(0L, 50L, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, -1.0D, 0.0D, 0.0D, -10.0D, casts);
        assertEquals(1, casts.count);

        double radians = Math.toRadians(5.0D);
        sampler.sample(100L, 50L, 0.0D, 0.0D, 0.0D,
                Math.sin(radians), 0.0D, -Math.cos(radians), 0.0D, 0.0D, -10.0D, casts);
        assertEquals(2, casts.count);
    }

    @Test
    @DisplayName("holds a changed answer for a hundred milliseconds before believing it")
    void debounceBothWays() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        assertFalse(sampler.occluded());

        // A doorway the player walks past: one frame of "occluded" is not an answer.
        assertFalse(sampler.accept(true, 0L));
        assertFalse(sampler.accept(true, 50L));
        assertTrue(sampler.accept(true, 100L));

        // And back, on the same terms.
        assertTrue(sampler.accept(false, 150L));
        assertTrue(sampler.accept(false, 200L));
        assertFalse(sampler.accept(false, 260L));
    }

    @Test
    @DisplayName("forgets a flicker that does not hold, rather than counting it toward the next one")
    void debounceRestarts() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        assertFalse(sampler.accept(true, 0L));
        assertFalse(sampler.accept(false, 50L));
        assertFalse(sampler.accept(true, 60L));
        // The clock on the change restarted at 60, so 100 is not yet enough.
        assertFalse(sampler.accept(true, 120L));
        assertTrue(sampler.accept(true, 170L));
    }

    @Test
    @DisplayName("forgets everything on a reset, so a new world starts visible")
    void resetClears() {
        MarkerOcclusionSampler sampler = new MarkerOcclusionSampler();
        sampler.accept(true, 0L);
        sampler.accept(true, 200L);
        assertTrue(sampler.occluded());
        sampler.reset();
        assertFalse(sampler.occluded());
    }
}
