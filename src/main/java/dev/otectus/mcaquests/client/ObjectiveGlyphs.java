package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.network.CardObjective;

/**
 * What an objective's state looks like, as something other than a colour.
 *
 * <p>The four states were previously told apart by the colour of their line and nothing else — green
 * for done, grey for everything else — which meant "done", "waiting on a mod that is not installed"
 * and "the villager this was about is dead" were indistinguishable to a player who could not rely on
 * colour, and the last two were indistinguishable to anybody. The sheets have carried a tick, a cross,
 * a dash and an empty box since 1.5.0; only two of them were ever drawn.
 *
 * <p>The gutter these are drawn in is the width of the bullet the lines used to start with, so putting
 * a glyph there costs no layout and no wrapping change: the text begins exactly where it did.
 */
public final class ObjectiveGlyphs {

    /**
     * Three spaces, 12 pixels: the gutter every objective line is indented by, and the room an 8×8
     * glyph is drawn in. Shared by the height calculation and the draw, as the old bullet was, so a
     * line that wraps to three rows is counted as three rows.
     */
    public static final String GUTTER = "   ";

    /** How far into the gutter the glyph sits, so it is not flush against the card frame. */
    public static final int GLYPH_X = 1;
    /** Nudges the 8×8 glyph onto the text baseline of a 10-pixel row. */
    public static final int GLYPH_Y = -1;
    /** Half of 16, so every sprite lands on exact pixel boundaries with no filtering. */
    public static final float GLYPH_SCALE = 0.5F;

    private ObjectiveGlyphs() {
    }

    public static GuiTextures.Sprite of(CardObjective.State state) {
        return switch (state) {
            case DONE -> GuiTextures.ICON_OBJ_DONE;
            case UNAVAILABLE -> GuiTextures.ICON_OBJ_SUSPENDED;
            case LOST -> GuiTextures.ICON_OBJ_FAILED;
            case PENDING -> GuiTextures.ICON_OBJ_PENDING;
        };
    }

    /**
     * The ink for an objective's line.
     *
     * <p>Red for a lost target, amber for on-hold and green for done, so colour and glyph say the same
     * thing twice rather than the glyph carrying it alone — redundant on purpose, since two weak
     * signals agreeing is easier to read at a glance than one strong one.
     */
    public static int colour(CardObjective.State state) {
        return switch (state) {
            case DONE -> Palette.READY;
            case UNAVAILABLE -> Palette.WARNING;
            case LOST -> Palette.URGENT;
            case PENDING -> Palette.OBJECTIVE;
        };
    }
}
