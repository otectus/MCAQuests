package dev.otectus.mcaquests.client.marker;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Where a world position lands on the screen, and where to put an arrow when it lands off it.
 *
 * <p>Pure arithmetic over two matrices, so the whole thing is checked by {@code MarkerProjectionTest}
 * rather than by standing in a world and turning round. That test was written before this class: the
 * multiplication order below is the sort of mistake that produces an indicator which points somewhere
 * confidently and wrongly, and no screenshot proves it right.
 */
public final class MarkerProjection {

    /** Below this, a direction is no direction at all and the answer has to be chosen. */
    private static final double DEGENERATE = 1.0E-5D;

    private MarkerProjection() {
    }

    /**
     * A world offset in normalised device coordinates.
     *
     * @param behind true when the point is behind the camera plane, where the perspective divide
     *               flips the sign and the coordinates mean the opposite of what they say
     * @param ndcX   -1 at the left edge of the screen, +1 at the right
     * @param ndcY   -1 at the bottom, +1 at the top — clip space, not screen space
     */
    public record Projection(boolean behind, double ndcX, double ndcY) {
    }

    /**
     * Where to draw, in GUI-scaled pixels with Y growing downward.
     *
     * @param angleRadians the direction from the centre of the screen to the target, zero to the
     *                     right and growing clockwise, for rotating a chevron toward it
     * @param onScreen     true when the target is genuinely visible inside the safe rectangle, so the
     *                     world marker should be drawn instead of an edge indicator
     */
    public record EdgePoint(double x, double y, double angleRadians, boolean onScreen) {
    }

    /**
     * Project a camera-relative offset through the frame's matrices.
     *
     * <p>{@code Vector4f.mul(Matrix4fc)} in JOML computes <em>matrix × vector</em>, so chaining the
     * model-view and then the projection applies them in that order — the same order the game does.
     *
     * @param relX world X minus camera X, and likewise for the other two axes
     */
    public static Projection project(double relX, double relY, double relZ,
                                     Matrix4f modelView, Matrix4f projection) {
        Vector4f clip = new Vector4f((float) relX, (float) relY, (float) relZ, 1.0F)
                .mul(modelView)
                .mul(projection);
        double w = Math.max(Math.abs(clip.w()), 1.0E-6D);
        return new Projection(clip.w() <= 0.0F, clip.x() / w, clip.y() / w);
    }

    /**
     * Where the indicator goes: the target's own position when it is comfortably on screen, otherwise
     * the point on the safe rectangle's edge in its direction.
     *
     * <p>Behind the camera the projected direction is inverted, because the divide by a negative
     * {@code w} has already mirrored it; a target behind and to the right belongs on the left edge,
     * which is the way the player turns to bring it into view. Exactly behind is the one case with no
     * answer in the arithmetic at all, so it is chosen: bottom centre, meaning "turn around", the
     * same every frame rather than flickering between two edges on the noise of a mouse.
     *
     * @param inset how far inside the screen the indicator stays, in GUI-scaled pixels
     */
    public static EdgePoint clamp(Projection projection, int guiWidth, int guiHeight, double inset) {
        double halfW = guiWidth * 0.5D;
        double halfH = guiHeight * 0.5D;

        double screenX = halfW + projection.ndcX() * halfW;
        double screenY = halfH - projection.ndcY() * halfH;

        // Screen Y grows downward while clip Y grows upward, so the vertical direction is negated.
        double dirX = projection.ndcX();
        double dirY = -projection.ndcY();
        if (projection.behind()) {
            dirX = -dirX;
            dirY = -dirY;
        }
        if (Math.abs(dirX) < DEGENERATE && Math.abs(dirY) < DEGENERATE) {
            dirX = 0.0D;
            dirY = 1.0D;
        }
        double angle = Math.atan2(dirY, dirX);

        boolean onScreen = !projection.behind()
                && screenX >= inset && screenX <= guiWidth - inset
                && screenY >= inset && screenY <= guiHeight - inset;
        if (onScreen) {
            return new EdgePoint(screenX, screenY, angle, true);
        }

        double scale = Math.min((halfW - inset) / Math.max(Math.abs(dirX), 1.0E-6D),
                (halfH - inset) / Math.max(Math.abs(dirY), 1.0E-6D));
        return new EdgePoint(halfW + dirX * scale, halfH + dirY * scale, angle, false);
    }
}
