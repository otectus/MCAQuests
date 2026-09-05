package dev.otectus.mcaquests.client.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the arrow settles at the same speed on every machine, and never points nowhere.
 *
 * <p>The frame-rate independence is the whole reason this class exists rather than a
 * {@code lerp(old, raw, 0.2)} in the renderer, so it is the property tested hardest: two simulated
 * machines four and a half times apart in frame rate must end up in the same place.
 */
class EdgeIndicatorSmootherTest {

    private static final double TAU = 0.080D;

    @Test
    @DisplayName("always produces a finite unit vector, for any frame time up to the clamp")
    void alwaysUnitLength() {
        Random random = new Random(4242L);
        EdgeIndicatorSmoother smoother = new EdgeIndicatorSmoother();
        for (int i = 0; i < 5_000; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dt = random.nextDouble() * 0.100D;
            assertTrue(smoother.update(Math.cos(angle), Math.sin(angle), dt, TAU));
            double length = Math.hypot(smoother.x(), smoother.y());
            assertTrue(Double.isFinite(length));
            assertEquals(1.0D, length, 1.0E-9D);
        }
    }

    @Test
    @DisplayName("reaches the same direction at 30 and at 144 frames a second")
    void frameRateIndependence() {
        EdgeIndicatorSmoother slow = new EdgeIndicatorSmoother();
        EdgeIndicatorSmoother fast = new EdgeIndicatorSmoother();
        slow.snap(1.0D, 0.0D);
        fast.snap(1.0D, 0.0D);

        double targetX = 0.0D;
        double targetY = 1.0D;
        for (int i = 0; i < 15; i++) {
            slow.update(targetX, targetY, 1.0D / 30.0D, TAU);
        }
        for (int i = 0; i < 72; i++) {
            fast.update(targetX, targetY, 1.0D / 144.0D, TAU);
        }
        // Half a second of the same swing, simulated twice.
        assertEquals(slow.x(), fast.x(), 0.02D);
        assertEquals(slow.y(), fast.y(), 0.02D);
    }

    @Test
    @DisplayName("settles a right-angle swing to 95 percent in about 240 milliseconds")
    void settlesInThreeTimeConstants() {
        EdgeIndicatorSmoother smoother = new EdgeIndicatorSmoother();
        smoother.snap(1.0D, 0.0D);
        for (int i = 0; i < 24; i++) {
            smoother.update(0.0D, 1.0D, 0.010D, TAU);
        }
        double angle = Math.toDegrees(Math.atan2(smoother.y(), smoother.x()));

        // Three time constants. What decays exactly exponentially here is not the angle but the
        // half-angle tangent: the filter blends unit vectors and renormalises, so the remaining
        // angle obeys tan(theta/2) = tan(45 degrees) * exp(-t/tau). Three time constants therefore
        // leave exp(-3) of it, which is the 95 percent settle -- and about six degrees of a
        // ninety-degree swing rather than four and a half, because a chord is not an arc.
        double remaining = Math.tan(Math.toRadians((90.0D - angle) / 2.0D));
        assertTrue(remaining <= 0.05D, "half-angle tangent still " + remaining);
        assertTrue(angle >= 84.0D, "only reached " + angle + " degrees");
    }

    @Test
    @DisplayName("snaps straight to the raw direction, and forgets everything on a reset")
    void snapAndReset() {
        EdgeIndicatorSmoother smoother = new EdgeIndicatorSmoother();
        assertFalse(smoother.primed());
        smoother.snap(3.0D, 4.0D);
        assertTrue(smoother.primed());
        assertEquals(0.6D, smoother.x(), 1.0E-9D);
        assertEquals(0.8D, smoother.y(), 1.0E-9D);

        smoother.reset();
        assertFalse(smoother.primed());
        // The first update after a reset is a snap, not a blend from a stale direction.
        smoother.update(0.0D, -1.0D, 1.0D / 60.0D, TAU);
        assertEquals(0.0D, smoother.x(), 1.0E-9D);
        assertEquals(-1.0D, smoother.y(), 1.0E-9D);
    }

    @Test
    @DisplayName("does not smooth at all when the time constant is zero")
    void zeroTauSnaps() {
        EdgeIndicatorSmoother smoother = new EdgeIndicatorSmoother();
        smoother.snap(1.0D, 0.0D);
        smoother.update(0.0D, 1.0D, 1.0D / 60.0D, 0.0D);
        assertEquals(0.0D, smoother.x(), 1.0E-9D);
        assertEquals(1.0D, smoother.y(), 1.0E-9D);
    }
}
