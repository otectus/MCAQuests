package dev.otectus.mcaquests.client.marker;

import java.util.function.BooleanSupplier;

/**
 * When the mod is allowed to ask the world whether the target is behind a wall.
 *
 * <p>Only {@code marker.edge.mode = OFFSCREEN_OR_OCCLUDED} ever consults this, and the reason it
 * exists at all is that the honest answer costs a block raycast: at 144 frames a second that is 144
 * traversals a second to answer a question whose answer changes when the player walks round a corner.
 * So the policy is separate from the raycast, and it is the policy that is tested.
 *
 * <p>Four limits, all of them from the spec: a minimum interval, a set of triggers (nothing moved,
 * nothing to ask), a debounce in both directions so a doorway does not strobe the arrow, and a hard
 * cap of twenty casts a second whatever the triggers say.
 *
 * <p>Split into "is a cast due" and "here is what it said" so that the caller can own the raycast and
 * this can be driven from a unit test with no world in it.
 */
public final class MarkerOcclusionSampler {

    /** How far the camera moves before the answer is worth asking again, in blocks. */
    private static final double CAMERA_MOVE = 0.25D;
    /** How far it turns, in degrees. */
    private static final double CAMERA_ROTATE_DEGREES = 2.0D;
    /** How far the target moves, in blocks. */
    private static final double ANCHOR_MOVE = 0.5D;
    /** How long a new answer must hold before it is believed, in milliseconds, both ways. */
    private static final long DEBOUNCE_MS = 100L;
    /** The ceiling, whatever the triggers say. */
    private static final int MAX_PER_SECOND = 20;
    /** A hit this much short of the target is a wall rather than the target's own block. */
    public static final double OCCLUSION_FRACTION = 0.90D;

    private boolean primed;
    private double lastCameraX;
    private double lastCameraY;
    private double lastCameraZ;
    private double lastLookX;
    private double lastLookY;
    private double lastLookZ;
    private double lastAnchorX;
    private double lastAnchorY;
    private double lastAnchorZ;
    private long lastSampleMillis;

    private long windowStartMillis;
    private int castsThisWindow;

    private boolean occluded;
    private boolean pending;
    private long pendingSinceMillis;

    /**
     * Ask, if asking is allowed, and answer with what is currently believed.
     *
     * @param intervalMs the configured minimum gap between casts
     * @param raycast    performs one cast and says whether the target was behind something; called at
     *                   most once, and only when a cast is due
     */
    public boolean sample(long nowMillis, long intervalMs,
                          double cameraX, double cameraY, double cameraZ,
                          double lookX, double lookY, double lookZ,
                          double anchorX, double anchorY, double anchorZ,
                          BooleanSupplier raycast) {
        if (due(nowMillis, intervalMs, cameraX, cameraY, cameraZ, lookX, lookY, lookZ,
                anchorX, anchorY, anchorZ)) {
            accept(raycast.getAsBoolean(), nowMillis);
        }
        return occluded;
    }

    /** Whether a cast is allowed and warranted right now; records that one is about to happen. */
    public boolean due(long nowMillis, long intervalMs,
                       double cameraX, double cameraY, double cameraZ,
                       double lookX, double lookY, double lookZ,
                       double anchorX, double anchorY, double anchorZ) {
        if (nowMillis - windowStartMillis >= 1000L) {
            windowStartMillis = nowMillis;
            castsThisWindow = 0;
        }
        if (castsThisWindow >= MAX_PER_SECOND) {
            return false;
        }
        if (primed) {
            if (nowMillis - lastSampleMillis < Math.max(intervalMs, 0L)) {
                return false;
            }
            if (!moved(cameraX, cameraY, cameraZ, lastCameraX, lastCameraY, lastCameraZ, CAMERA_MOVE)
                    && !turned(lookX, lookY, lookZ)
                    && !moved(anchorX, anchorY, anchorZ, lastAnchorX, lastAnchorY, lastAnchorZ,
                            ANCHOR_MOVE)) {
                // Standing still looking at a target that is standing still: the last answer is still
                // the answer, and a cast would only confirm it.
                return false;
            }
        }
        primed = true;
        lastSampleMillis = nowMillis;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        lastCameraZ = cameraZ;
        lastLookX = lookX;
        lastLookY = lookY;
        lastLookZ = lookZ;
        lastAnchorX = anchorX;
        lastAnchorY = anchorY;
        lastAnchorZ = anchorZ;
        castsThisWindow++;
        return true;
    }

    /**
     * Take one cast's answer, which is believed only once it has held for the debounce.
     *
     * @return what is believed now, which is not necessarily what was just measured
     */
    public boolean accept(boolean rawOccluded, long nowMillis) {
        if (rawOccluded == occluded) {
            pending = false;
            return occluded;
        }
        if (!pending) {
            pending = true;
            pendingSinceMillis = nowMillis;
            return occluded;
        }
        if (nowMillis - pendingSinceMillis >= DEBOUNCE_MS) {
            occluded = rawOccluded;
            pending = false;
        }
        return occluded;
    }

    /** What is currently believed, without asking anything. */
    public boolean occluded() {
        return occluded;
    }

    /** Forget everything: a new world, a new target, or the mode being switched off. */
    public void reset() {
        primed = false;
        occluded = false;
        pending = false;
        castsThisWindow = 0;
        windowStartMillis = 0L;
    }

    private static boolean moved(double ax, double ay, double az,
                                 double bx, double by, double bz, double threshold) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz > threshold * threshold;
    }

    /** Both vectors are unit length, so their dot product is the cosine of the angle between them. */
    private boolean turned(double lookX, double lookY, double lookZ) {
        double dot = lookX * lastLookX + lookY * lastLookY + lookZ * lastLookZ;
        return dot < Math.cos(Math.toRadians(CAMERA_ROTATE_DEGREES));
    }
}
