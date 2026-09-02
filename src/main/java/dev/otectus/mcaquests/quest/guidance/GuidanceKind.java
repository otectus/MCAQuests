package dev.otectus.mcaquests.quest.guidance;

/**
 * What sort of place or person a {@link GuidanceTarget} points at.
 *
 * <p>Purely presentational: it chooses the glyph the HUD and the world marker draw, and nothing on
 * the server branches on it. It exists because "84 blocks, ahead-right" says how far but never what,
 * and a player who cannot tell a bed from a fortress from a person cannot plan the next two minutes.
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
    LOCATION;

    private static final GuidanceKind[] VALUES = values();

    /** Decodes an ordinal off the wire, defaulting to {@link #LOCATION} rather than throwing. */
    public static GuidanceKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LOCATION;
    }
}
