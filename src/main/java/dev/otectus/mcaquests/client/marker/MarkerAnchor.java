package dev.otectus.mcaquests.client.marker;

/**
 * Where in the world one marker stands, resolved once per frame and drawn from twice.
 *
 * <p>Two heights rather than one, because the marker has two feet on different ground: whatever
 * stands on the target stands at {@link #baseY()}, and the glyph the player actually reads floats at
 * {@link #glyphY()}. Collapsing them is how the icon ended up in the sky — the old renderer had only
 * a base and translated the glyph twenty-five blocks off it to clear the beam.
 *
 * @param x           world X of the target, already interpolated for this frame
 * @param baseY       world Y the marker stands on: an entity's feet, or a block's top surface
 * @param z           world Z of the target, already interpolated for this frame
 * @param glyphY      world Y the glyph is anchored at, interpreted by {@link #alignment()}
 * @param alignment   whether {@code glyphY} is the glyph's centre or the surface it sits above
 * @param targetWidth how wide the target is, in blocks, for sizing anything drawn around it
 * @param approximate true when the vertical answer is a guess rather than a resolved surface or a
 *                    live bounding box, so the renderer can decline to promise precision
 */
public record MarkerAnchor(double x, double baseY, double z, double glyphY,
                           MarkerAnchor.VerticalAlignment alignment, double targetWidth,
                           boolean approximate) {

    /** How {@link MarkerAnchor#glyphY()} is to be read. */
    public enum VerticalAlignment {
        /** The glyph's centre sits at {@code glyphY}: an upper-body anchor on something alive. */
        CENTER_ON_BODY,
        /** The glyph's bottom sits just above {@code glyphY}: a block's top surface. */
        BOTTOM_ON_SURFACE
    }
}
