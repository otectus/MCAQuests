package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;

/**
 * What the world renderer worked out this frame, for the HUD to draw a moment later.
 *
 * <p>The edge indicator needs the frame's projection matrices, which only the level-render event has,
 * and it has to be drawn in the GUI pass, which only the HUD event has. Rather than recompute the
 * projection twice or keep a matrix alive across events, the renderer leaves its answer here and the
 * HUD picks it up.
 *
 * <p>One retained object, mutated in place: this runs every frame at whatever frame rate the player
 * has, and a small object per frame is still a hundred and forty of them a second for the collector
 * to think about.
 *
 * <p>Each answer is stamped with a frame id and read once. The render tick alone is not enough — it
 * changes twenty times a second while frames go past at a hundred and forty — so a counter goes with
 * it. Without the stamp, a frame in which the renderer never ran at all (no quest, GUI hidden, the
 * player in another dimension) would leave the HUD drawing the last thing it was told, forever.
 */
public final class MarkerFrameState {

    /** The one the renderer writes and the HUD reads. */
    public static final MarkerFrameState CURRENT = new MarkerFrameState();

    private long counter;

    // Both zero to start with, so an unpublished state reads as already consumed; no frame id the
    // counter produces is ever zero.
    private long frameId;
    private long consumedFrameId;

    private boolean edgeActive;
    private boolean hudGlyph;
    private double screenX;
    private double screenY;
    private double angleRadians;
    private int rgb;
    private long roundedDistance;
    private GuidanceKind kind = GuidanceKind.LOCATION;
    private float alpha;

    /** A stamp nothing else this session will produce, for {@code renderTick}'s frame. */
    public long nextFrameId(int renderTick) {
        return ((long) renderTick << 32) | (++counter & 0xFFFFFFFFL);
    }

    /**
     * Record this frame's answer.
     *
     * @param edgeActive true when the target is off screen or behind, so the HUD draws the arrow and
     *                   the renderer draws no world billboard
     * @param hudGlyph   true when the world glyph would be too small to read and the HUD draws it flat
     * @param screenX    where the target projects to, in GUI-scaled pixels; the edge point when
     *                   {@code edgeActive}
     */
    public void publish(long frameId, boolean edgeActive, boolean hudGlyph,
                        double screenX, double screenY, double angleRadians,
                        int rgb, long roundedDistance, GuidanceKind kind, float alpha) {
        this.frameId = frameId;
        this.edgeActive = edgeActive;
        this.hudGlyph = hudGlyph;
        this.screenX = screenX;
        this.screenY = screenY;
        this.angleRadians = angleRadians;
        this.rgb = rgb;
        this.roundedDistance = roundedDistance;
        this.kind = kind;
        this.alpha = alpha;
    }

    /**
     * Take this frame's answer, if there is one that has not been taken already.
     *
     * @return false when the renderer did not publish this frame, in which case nothing is drawn
     */
    public boolean consume() {
        if (frameId == consumedFrameId) {
            return false;
        }
        consumedFrameId = frameId;
        return true;
    }

    public boolean edgeActive() {
        return edgeActive;
    }

    public boolean hudGlyph() {
        return hudGlyph;
    }

    public double screenX() {
        return screenX;
    }

    public double screenY() {
        return screenY;
    }

    public double angleRadians() {
        return angleRadians;
    }

    public int rgb() {
        return rgb;
    }

    public long roundedDistance() {
        return roundedDistance;
    }

    public GuidanceKind kind() {
        return kind;
    }

    public float alpha() {
        return alpha;
    }
}
