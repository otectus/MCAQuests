package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.TrackerBackground;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tracker's two background styles have to <em>look</em> different, and the opacity slider has to
 * move something.
 *
 * <p>This is the regression guard for the bug that motivated the setting: {@code SHADED} used to be a
 * flat {@code 0x80000000} rectangle over the same footprint as the {@code PANEL} plate, whose fill
 * samples at roughly {@code 0xD8181818}. Both are dark and near-opaque, so switching between them did
 * nothing a player could see. "Lighter than the plate" and "not flat" are claims about numbers, so
 * they are checked here rather than by opening the game.
 *
 * <p>{@link TrackerBackground} pulls in no Minecraft, Mojang or Forge types — not even the config
 * class — so this needs no game bootstrap.
 */
class TrackerBackgroundTest {

    /** The nine-sliced HUD plate's fill, sampled from {@code panel.png} region (0,48,48,48). */
    private static final int PANEL_FILL = 0xD8181818;

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    @Test
    @DisplayName("SHADED is a gradient, and lighter than the panel plate")
    void shadedDiffersFromPanel() {
        int[] wash = TrackerBackground.shadedGradient(100);
        assertNotEquals(alpha(wash[0]), alpha(wash[1]), "the wash must fade, not sit flat");
        assertTrue(alpha(wash[0]) < alpha(PANEL_FILL),
                "the top of the wash must be lighter than the plate, or the styles look identical");
        assertTrue(alpha(wash[1]) < alpha(PANEL_FILL),
                "even the densest end of the wash must stay lighter than the plate");
    }

    @Test
    @DisplayName("Opacity scales the alpha byte and nothing else")
    void opacityScalesAlphaOnly() {
        int colour = 0x80123456;
        assertEquals(colour, TrackerBackground.applyOpacity(colour, 100));
        assertEquals(0, alpha(TrackerBackground.applyOpacity(colour, 0)));
        assertEquals(0x123456, TrackerBackground.applyOpacity(colour, 0) & 0x00FFFFFF);
        assertEquals(0x123456, TrackerBackground.applyOpacity(colour, 50) & 0x00FFFFFF);
        assertEquals(64, alpha(TrackerBackground.applyOpacity(colour, 50)));
    }

    @Test
    @DisplayName("The panel plate's shader alpha runs 0 to 1")
    void panelAlphaSpansTheRange() {
        assertEquals(0.0F, TrackerBackground.panelAlpha(0));
        assertEquals(1.0F, TrackerBackground.panelAlpha(100));
        assertEquals(0.5F, TrackerBackground.panelAlpha(50));
    }

    @Test
    @DisplayName("A hand-edited config cannot wrap the alpha byte")
    void outOfRangePercentsClamp() {
        int colour = 0x80123456;
        assertEquals(TrackerBackground.applyOpacity(colour, 100), TrackerBackground.applyOpacity(colour, 400));
        assertEquals(TrackerBackground.applyOpacity(colour, 0), TrackerBackground.applyOpacity(colour, -30));
        assertEquals(1.0F, TrackerBackground.panelAlpha(250));
        assertEquals(0.0F, TrackerBackground.panelAlpha(-1));
    }
}
