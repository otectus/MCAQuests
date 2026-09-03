package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;

/**
 * What colour a marker is, by what it is standing on.
 *
 * <p>Separate from {@code Palette}, and deliberately so. Every colour in {@code Palette} is a dark ink
 * chosen to be legible on vanilla's {@code #C6C6C6} container grey; these are lights, drawn
 * translucent against grass, stone, lava and night sky.
 *
 * <p>The eight are Okabe–Ito, the standard colourblind-safe qualitative palette: no two of them
 * collapse into each other under any of the three common forms of colour blindness, which the earlier
 * hand-picked set did — its village green and its biome green were the same colour to a deuteranope,
 * and its person blue and portal purple were close. Colour is never the only signal even so; every
 * kind keeps its own glyph, and {@link #initials} exists for the maps that can carry neither.
 *
 * <p>Read by the in-world marker and by the map integrations, so a JourneyMap waypoint is the same
 * colour as the glyph standing on the same place.
 */
public final class MarkerColours {

    /** A person: sky blue. */
    private static final int PERSON = 0x56B4E9;
    /** Somewhere to live, or to work: orange. */
    private static final int DWELLING = 0xE69F00;
    /** A village: bluish green. */
    private static final int SETTLEMENT = 0x009E73;
    /** A generated structure — in this mod, usually somewhere that will try to kill you: vermillion. */
    private static final int STRUCTURE = 0xD55E00;
    /** A biome: somewhere to be, rather than something to find. Blue. */
    private static final int LAND = 0x0072B2;
    /** A portal, in reddish purple — the closest this palette comes to the game's own. */
    private static final int PORTAL = 0xCC79A7;
    /** Anything else: yellow. */
    private static final int PLACE = 0xF0E442;

    private MarkerColours() {
    }

    public static int of(GuidanceKind kind) {
        return switch (kind) {
            case VILLAGER -> PERSON;
            case HOME, WORKSTATION -> DWELLING;
            case VILLAGE -> SETTLEMENT;
            case STRUCTURE -> STRUCTURE;
            case BIOME -> LAND;
            case PORTAL -> PORTAL;
            case LOCATION -> PLACE;
        };
    }

    /**
     * The nearest name in Xaero's fixed waypoint palette.
     *
     * <p>Xaero's waypoints take a named enum constant, not an RGB value, so parity there is the same
     * kind in the same colour <em>family</em> — never the same pixels. Returned as a name so the
     * binding can resolve the constant at runtime and read its ordinal; the ordinals themselves are
     * not a contract Xaero has ever made.
     */
    public static String xaeroColourName(GuidanceKind kind) {
        return switch (kind) {
            case VILLAGER -> "LIGHT_BLUE";
            case HOME, WORKSTATION -> "GOLD";
            case VILLAGE -> "GREEN";
            case STRUCTURE -> "RED";
            case BIOME -> "BLUE";
            case PORTAL -> "PURPLE";
            case LOCATION -> "YELLOW";
        };
    }

    /**
     * One or two letters saying what a marker is, for anywhere that cannot carry a glyph.
     *
     * <p>Xaero draws a waypoint as its initials on the minimap, and takes them from the label unless
     * told otherwise — which for "Nether Fortress" and "Nitwit" gives "NE" and "NI", two labels that
     * say nothing about what either place is. These do.
     */
    public static String initials(GuidanceKind kind) {
        return switch (kind) {
            case VILLAGER -> "V";
            case HOME -> "H";
            case WORKSTATION -> "W";
            case VILLAGE -> "VG";
            case STRUCTURE -> "ST";
            case BIOME -> "B";
            case PORTAL -> "P";
            case LOCATION -> "Q";
        };
    }
}
