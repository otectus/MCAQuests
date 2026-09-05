package dev.otectus.mcaquests.client.marker;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Where a world position lands on the screen.
 *
 * <p>Pure arithmetic over two matrices and nothing remembered between frames, so the whole thing is
 * checked by {@code MarkerProjectionTest}
 * rather than by standing in a world and turning round. That test was written before this class: the
 * multiplication order below is the sort of mistake that produces an indicator which points somewhere
 * confidently and wrongly, and no screenshot proves it right.
 */
public final class MarkerProjection {

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
     * The same projection, into a scratch vector, leaving normalised device coordinates in it.
     *
     * <p>{@link #project} allocates a {@code Vector4f} and a {@code Projection} every call, which is
     * two objects a frame for as long as a marker is on screen. This one allocates nothing: the caller
     * owns the scratch vector and reads {@code scratch.x()/y()/z()} back out of it.
     *
     * <p>Unlike {@link #project} this refuses a point behind the camera rather than reporting it,
     * because the caller has a camera-space bearing to fall back on that is better than mirrored
     * arithmetic on a negative {@code w}.
     *
     * @return false when the point is behind the near plane or any coordinate is not finite, in which
     *         case the scratch contents mean nothing
     */
    public static boolean projectInto(double relX, double relY, double relZ,
                                      Matrix4f modelView, Matrix4f projection, Vector4f scratch) {
        scratch.set((float) relX, (float) relY, (float) relZ, 1.0F).mul(modelView).mul(projection);
        float w = scratch.w();
        if (!Float.isFinite(w) || w <= 0.0F) {
            return false;
        }
        float x = scratch.x() / w;
        float y = scratch.y() / w;
        float z = scratch.z() / w;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return false;
        }
        scratch.set(x, y, z, w);
        return true;
    }
}
