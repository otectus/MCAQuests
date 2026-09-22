package dev.otectus.mcaquests.project.scope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

/**
 * Where a project's positional work counts, as one value shared by server credit, operator diagnostics
 * and the client's build-area outline (1.7.0).
 *
 * <p>Before this existed the three disagreed. Credit asked MCA whether a block was inside the village
 * with a margin of zero — the box spanned by the village's registered buildings and nothing more — while
 * nothing at all told a player where that box was. A defensive wall sits around a village by definition,
 * so every block of one landed just outside it and counted for nothing.
 *
 * <ul>
 *   <li>{@link Shape#VILLAGE_BOX}: MCA's building box inflated by {@code margin}. {@link #contains} is
 *       exactly MCA's own {@code isWithinBorder(pos, margin)}, so the outline a player sees is the
 *       predicate the server applies. {@code exact} is true.</li>
 *   <li>{@link Shape#ANCHOR_RADIUS}: a project with no village behind it, a radius around its frozen
 *       anchor. Exact.</li>
 *   <li>{@link Shape#VILLAGE_APPROXIMATE}: a village whose box this MCA does not expose. The server still
 *       asks MCA, so credit is exact, but the outline can only be drawn as a circle around the village
 *       centre and is labelled approximate rather than drawn with false confidence.</li>
 * </ul>
 */
public record ScopeGeometry(ResourceLocation dimension, Shape shape, BlockPos anchor, int radius,
                            Optional<BoundingBox> box, int margin, boolean exact) {

    public enum Shape {
        VILLAGE_BOX, ANCHOR_RADIUS, VILLAGE_APPROXIMATE
    }

    /** MCA's building box inflated by {@code margin}. */
    public static ScopeGeometry villageBox(ResourceLocation dimension, BlockPos center, BoundingBox box, int margin) {
        return new ScopeGeometry(dimension, Shape.VILLAGE_BOX, center, 0, Optional.of(box), margin, true);
    }

    public static ScopeGeometry anchorRadius(ResourceLocation dimension, BlockPos anchor, int radius) {
        return new ScopeGeometry(dimension, Shape.ANCHOR_RADIUS, anchor, radius, Optional.empty(), 0, true);
    }

    public static ScopeGeometry villageApproximate(ResourceLocation dimension, BlockPos center, int radius, int margin) {
        return new ScopeGeometry(dimension, Shape.VILLAGE_APPROXIMATE, center, radius, Optional.empty(), margin, false);
    }

    /** The inflated box actually tested, when there is one. */
    public Optional<BoundingBox> effectiveBox() {
        return box.map(b -> b.inflatedBy(margin));
    }

    /**
     * Whether {@code pos} in {@code dim} is inside. For {@link Shape#VILLAGE_APPROXIMATE} this is the
     * drawn circle, which is a guide only; the server never credits from it.
     */
    public boolean contains(ResourceLocation dim, BlockPos pos) {
        if (!dimension.equals(dim)) {
            return false;
        }
        return switch (shape) {
            case VILLAGE_BOX -> effectiveBox().map(b -> b.isInside(pos)).orElse(false);
            case ANCHOR_RADIUS -> anchor.distSqr(pos) <= (long) radius * radius;
            case VILLAGE_APPROXIMATE -> horizontalDistance(anchor, pos) <= radius + margin;
        };
    }

    /** Whole blocks outside the area, horizontally, for feedback; 0 when inside. */
    public int blocksOutside(BlockPos pos) {
        return switch (shape) {
            case VILLAGE_BOX -> effectiveBox().map(b -> {
                int dx = Math.max(0, Math.max(b.minX() - pos.getX(), pos.getX() - b.maxX()));
                int dz = Math.max(0, Math.max(b.minZ() - pos.getZ(), pos.getZ() - b.maxZ()));
                return Math.max(dx, dz);
            }).orElse(0);
            case ANCHOR_RADIUS -> Math.max(0, (int) Math.ceil(Math.sqrt(anchor.distSqr(pos))) - radius);
            case VILLAGE_APPROXIMATE -> Math.max(0, (int) Math.ceil(horizontalDistance(anchor, pos)) - radius - margin);
        };
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeEnum(shape);
        buf.writeBlockPos(anchor);
        buf.writeVarInt(radius);
        buf.writeBoolean(box.isPresent());
        box.ifPresent(b -> {
            buf.writeInt(b.minX());
            buf.writeInt(b.minY());
            buf.writeInt(b.minZ());
            buf.writeInt(b.maxX());
            buf.writeInt(b.maxY());
            buf.writeInt(b.maxZ());
        });
        buf.writeVarInt(margin);
        buf.writeBoolean(exact);
    }

    public static ScopeGeometry decode(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        Shape shape = buf.readEnum(Shape.class);
        BlockPos anchor = buf.readBlockPos();
        int radius = buf.readVarInt();
        Optional<BoundingBox> box = buf.readBoolean()
                ? Optional.of(new BoundingBox(buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt()))
                : Optional.empty();
        int margin = buf.readVarInt();
        boolean exact = buf.readBoolean();
        return new ScopeGeometry(dimension, shape, anchor, radius, box, margin, exact);
    }
}
