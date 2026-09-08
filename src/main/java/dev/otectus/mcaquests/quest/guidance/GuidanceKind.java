package dev.otectus.mcaquests.quest.guidance;

/**
 * What sort of place or person a {@link GuidanceTarget} points at.
 *
 * <p>Almost purely presentational: it chooses the glyph the HUD and the world marker draw, and
 * nothing on the server branches on it. It exists because "84 blocks, ahead-right" says how far but
 * never what, and a player who cannot tell a bed from a fortress from a person cannot plan the next
 * two minutes. {@link #INSTRUCTION} is the one entry that is more than a glyph, and even that is a
 * rule about drawing rather than about the world: it says there is nothing to draw.
 *
 * <p>The ordinal is on the wire, so entries are <b>appended</b>, never reordered or removed.
 */
public enum GuidanceKind {

    /** A specific villager: the escortee, the recipient, the person to heal, cure or find. */
    VILLAGER,
    /** A villager's assigned bed. */
    HOME,
    /** A villager's assigned job site. */
    WORKSTATION,
    /** A village centre, or anywhere inside its border. */
    VILLAGE,
    /** A generated structure the server located: a fortress, an ancient city, an ocean ruin. */
    STRUCTURE,
    /** A biome the server located. */
    BIOME,
    /** A portal into the dimension the quest wants, in the dimension the player is standing in. */
    PORTAL,
    /** A plain position: an authored coordinate, or an anchor that is none of the above. */
    LOCATION,
    /**
     * Not a place at all: a line of text about a destination in a dimension the player has no way
     * into from where they are standing.
     *
     * <p>The one kind that carries no geometry. {@code pos} is {@link net.minecraft.core.BlockPos#ZERO}
     * and means nothing, {@code dimension} names the world the destination is in, and {@code label}
     * is the whole instruction. Everything that draws a position — the world marker, the map
     * waypoints, the coordinate buttons in the log — must skip it rather than draw the zero.
     */
    INSTRUCTION;

    private static final GuidanceKind[] VALUES = values();

    /** Decodes an ordinal off the wire, defaulting to {@link #LOCATION} rather than throwing. */
    public static GuidanceKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LOCATION;
    }
}
