package dev.otectus.mcaquests.client.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns a target into the {@link MarkerAnchor} the renderer draws at, in primitives so it can be
 * tested without a world.
 *
 * <p>Every method here takes numbers rather than an {@code Entity} or a {@code Level} for the reason
 * {@link MarkerGeometry} gives: a marker in the wrong place is only visible by standing somewhere
 * particular, and that is precisely the kind of arithmetic worth pinning down in a test instead.
 *
 * <p><b>Never call {@code Entity#getY(double)} here.</b> Its argument is not a partial tick: in
 * 1.20.1 the overload samples the bounding box, returning {@code position.y + height * scale}. Read
 * as interpolation it makes the marker climb one entity height per tick and drop back, which is
 * exactly the sawtooth this class was written to remove. Interpolation is {@code xo}/{@code yo}/
 * {@code zo} lerped against the current position, and nothing else.
 */
public final class MarkerAnchorResolver {

    /**
     * How far up a body the glyph sits, as a fraction of bounding-box height.
     *
     * <p>Just under the top: on the chest of a villager, on the head of a baby, inside the box for
     * both. An eye position would need every target to be a {@code LivingEntity}, and a fixed offset
     * in blocks would leave a tall target wearing its marker at the knee.
     */
    public static final double BODY_ANCHOR = 0.72D;

    /** The width assumed for a target that is not a live entity: a villager's, near enough. */
    private static final double DEFAULT_WIDTH = 0.6D;

    private MarkerAnchorResolver() {
    }

    /**
     * A loaded entity, interpolated onto this frame.
     *
     * <p>All three axes use the same partial tick. The old code lerped none of them, so the marker
     * stepped sideways twenty times a second while the camera moved smoothly past it.
     */
    public static MarkerAnchor forEntity(double previousX, double previousY, double previousZ,
                                         double x, double y, double z,
                                         float bbWidth, float bbHeight, float partialTick) {
        double t = Mth.clamp((double) partialTick, 0.0D, 1.0D);
        double feetY = Mth.lerp(t, previousY, y);
        return new MarkerAnchor(
                Mth.lerp(t, previousX, x),
                feetY,
                Mth.lerp(t, previousZ, z),
                feetY + bbHeight * BODY_ANCHOR,
                MarkerAnchor.VerticalAlignment.CENTER_ON_BODY,
                bbWidth,
                false);
    }

    /**
     * A fixed block position, standing on whatever supports it.
     *
     * <p>The block's own shape first — a bed, an anvil or a workstation is what the player is being
     * sent to, and the marker belongs on its top face. The block below next, for the empty air above
     * a floor. Neither means there is nothing here to stand on, so the marker stays at the
     * coordinate and says it is approximate; no height map is consulted and nothing scans downward,
     * because this is resolved for a target that may sit in the player's view for minutes.
     *
     * @param shapeAt    the shape of the block at {@code pos}, empty when there is none
     * @param shapeBelow the shape of the block one below {@code pos}, empty when there is none
     */
    public static MarkerAnchor forFixed(BlockPos pos, VoxelShape shapeAt, VoxelShape shapeBelow) {
        double surfaceY;
        boolean approximate;
        if (!shapeAt.isEmpty()) {
            surfaceY = pos.getY() + shapeAt.max(Direction.Axis.Y);
            approximate = false;
        } else if (!shapeBelow.isEmpty()) {
            surfaceY = (pos.getY() - 1) + shapeBelow.max(Direction.Axis.Y);
            approximate = false;
        } else {
            surfaceY = pos.getY();
            approximate = true;
        }
        return new MarkerAnchor(pos.getX() + 0.5D, surfaceY, pos.getZ() + 0.5D, surfaceY,
                MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE, DEFAULT_WIDTH, approximate);
    }

    /**
     * An entity the client cannot see, at the position and height the server last sent.
     *
     * <p>This is when the marker earns its keep — a villager who has walked out of render distance
     * is exactly the one worth pointing at — so the glyph still goes on the body rather than at the
     * feet. A non-positive height means the sender had nothing to say (or the wire was corrupt), and
     * a body anchor computed from it would be no anchor at all, so the marker falls back to standing
     * on the coordinate.
     */
    public static MarkerAnchor forUnloadedEntity(BlockPos pos, float entityHeight) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (!(entityHeight > 0.0F)) {
            return new MarkerAnchor(x, pos.getY(), z, pos.getY(),
                    MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE, DEFAULT_WIDTH, true);
        }
        return new MarkerAnchor(x, pos.getY(), z, pos.getY() + entityHeight * BODY_ANCHOR,
                MarkerAnchor.VerticalAlignment.CENTER_ON_BODY, DEFAULT_WIDTH, true);
    }
}
