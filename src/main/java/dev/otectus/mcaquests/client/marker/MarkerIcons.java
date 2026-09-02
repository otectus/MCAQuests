package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;

/**
 * The glyph and the colour that say <em>what</em> a marker is standing on.
 *
 * <p>"84 blocks, ahead-right" tells the player how far and which way, and nothing about whether they
 * are walking to a person, a bed or a fortress — which is most of what they need in order to decide
 * whether to set off now or gear up first. The sheets already carried compass, distance, village and
 * family glyphs with no call site anywhere; this is what they were drawn for.
 */
public final class MarkerIcons {

    private MarkerIcons() {
    }

    public static GuiTextures.Sprite of(GuidanceKind kind) {
        return switch (kind) {
            case VILLAGER -> GuiTextures.ICON_PROF_VILLAGER;
            case HOME -> GuiTextures.ICON_FAMILY;
            case WORKSTATION -> GuiTextures.ICON_PROF_TOOLSMITH;
            case VILLAGE -> GuiTextures.ICON_VILLAGE;
            // A skull. It said "something here will kill you", which is a guess about a fortress and
            // simply false about trail ruins or a village -- and in either case it answers a question
            // the player did not ask. The construction glyph says "a built place", which is what every
            // structure this can point at has in common.
            case STRUCTURE -> GuiTextures.ICON_PROJECT;
            // A dashed trail running off to an arrowhead. It reads as "distance" out of context, but a
            // located biome is precisely a long walk in one direction, so it stays.
            case BIOME -> GuiTextures.ICON_DISTANCE;
            case PORTAL -> GuiTextures.ICON_SITUATION;
            case LOCATION -> GuiTextures.ICON_COMPASS;
        };
    }
}
