package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;

/**
 * What colour the beam is, by what it is standing on.
 *
 * <p>Separate from {@code Palette}, and deliberately so. Every colour in {@code Palette} is a dark ink
 * chosen to be legible on vanilla's {@code #C6C6C6} container grey; these are lights, drawn
 * translucent against grass, stone, lava and night sky. Reusing the screen palette here would have
 * given the world a beam of {@code #404040}, which is a beam you cannot see.
 *
 * <p>Kept close to the palette in <em>meaning</em> even so: a person is the same friendly blue the
 * tracker names a direction in, somewhere to live is warm, and a structure — which in this mod is
 * nearly always somewhere dangerous — is the same red the interface uses for a deadline running out.
 */
public final class MarkerColours {

    /** A person: the tracker's direction blue, brightened for the world. */
    private static final int PERSON = 0x6FC8FF;
    /** Somewhere to live, or to work. */
    private static final int DWELLING = 0xFFC46B;
    /** A village. */
    private static final int SETTLEMENT = 0x8CE08C;
    /** A generated structure — in this mod, usually somewhere that will try to kill you. */
    private static final int STRUCTURE = 0xFF7A6B;
    /** A biome: somewhere to be, rather than something to find. */
    private static final int LAND = 0x9CE0C8;
    /** A portal, in the purple the game itself uses for one. */
    private static final int PORTAL = 0xC89CFF;
    /** Anything else. */
    private static final int PLACE = 0xE0E0E0;

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
}
