package dev.otectus.mcaquests.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window fits on the screen, at every screen there is.
 *
 * <p>{@code MCAQuests-Townstead-1.20.1-Compatibility-Implementation-Spec.md} §8.4 sets the bar for
 * this interface: <em>320×240-equivalent through 4K, GUI scales 1 to 4 and auto</em>. That is a claim
 * about arithmetic, and checking it by opening the game at three window sizes proves almost nothing —
 * the failure it is guarding against is a panel whose <em>minimum</em> size is larger than the screen
 * it has to sit inside, which only appears at the very edge of the range.
 *
 * <p>So the whole range is swept here instead. Minecraft's logical screen size is the window size
 * divided by the GUI scale, and the game refuses to use a scale that would take it below 320×240 —
 * which makes 320×240 the true floor and the interesting case, not a hypothetical one.
 *
 * <p>{@link PanelGeometry} is package-private and pulls in no Minecraft types, so this needs no Forge
 * bootstrap, exactly like {@link ScrollViewTest}.
 */
class PanelGeometryTest {

    /** Logical sizes a real client actually produces, floor and ceiling included. */
    private static List<int[]> screenSizes() {
        List<int[]> sizes = new ArrayList<>();
        sizes.add(new int[]{320, 240});   // the floor Minecraft itself enforces
        sizes.add(new int[]{427, 240});   // 1280×720 at GUI scale 3
        sizes.add(new int[]{480, 270});   // 1920×1080 at GUI scale 4
        sizes.add(new int[]{640, 360});   // 1920×1080 at GUI scale 3
        sizes.add(new int[]{960, 540});   // 1920×1080 at GUI scale 2
        sizes.add(new int[]{1920, 1080}); // 1920×1080 at GUI scale 1
        sizes.add(new int[]{960, 540});   // 3840×2160 at GUI scale 4
        sizes.add(new int[]{3840, 2160}); // 4K at GUI scale 1
        return sizes;
    }

    /** Every combination a screen in this mod can ask for. */
    private static List<int[]> variants() {
        List<int[]> variants = new ArrayList<>();
        variants.add(new int[]{0, 0});    // a modal with neither tabs nor its own header
        variants.add(new int[]{22, 0});   // the tabbed log and journal
        variants.add(new int[]{0, 48});   // the offer menu's villager header
        variants.add(new int[]{22, 48});  // both, so the worst case is covered too
        return variants;
    }

    /** Names the case in a failure message, so a swept assertion still says which size broke. */
    private static String describe(int[] screen, int[] variant) {
        return "at " + screen[0] + "x" + screen[1]
                + " (tabStrip=" + variant[0] + ", extraHeader=" + variant[1] + ")";
    }

    @Test
    @DisplayName("the panel never leaves the screen, at any size the game can produce")
    void panelStaysOnScreen() {
        for (int[] screen : screenSizes()) {
            for (int[] variant : variants()) {
                PanelGeometry g = new PanelGeometry(screen[0], screen[1], variant[0], variant[1]);
                String at = describe(screen, variant);

                assertTrue(g.panelWidth() <= screen[0], "panel wider than the screen " + at);
                assertTrue(g.panelHeight() <= screen[1], "panel taller than the screen " + at);
                assertTrue(g.left() >= 0, "panel starts left of the screen " + at);
                assertTrue(g.top() >= 0, "panel starts above the screen " + at);
                assertTrue(g.left() + g.panelWidth() <= screen[0], "panel runs off the right " + at);
                assertTrue(g.top() + g.panelHeight() <= screen[1], "panel runs off the bottom " + at);
            }
        }
    }

    @Test
    @DisplayName("no edge is ever inverted, however little room there is")
    void edgesStayOrdered() {
        for (int[] screen : screenSizes()) {
            for (int[] variant : variants()) {
                PanelGeometry g = new PanelGeometry(screen[0], screen[1], variant[0], variant[1]);
                String at = describe(screen, variant);

                assertTrue(g.wellLeft() <= g.wellRight(), "well is inside out " + at);
                assertTrue(g.wellTop() <= g.wellBottom(), "well bottom is above its top " + at);
                assertTrue(g.contentTop() <= g.contentBottom(), "content bottom above its top " + at);
                assertTrue(g.contentWidth() >= 1,
                        "content column collapsed to nothing " + at);
                assertTrue(g.scrollbarX() >= g.wellLeft(),
                        "scrollbar pushed out of the well " + at);
                assertTrue(g.contentRight() <= g.scrollbarX(),
                        "text column runs under the scrollbar " + at);
            }
        }
    }

    @Test
    @DisplayName("content stays inside the well it is clipped to")
    void contentStaysInsideTheWell() {
        for (int[] screen : screenSizes()) {
            for (int[] variant : variants()) {
                PanelGeometry g = new PanelGeometry(screen[0], screen[1], variant[0], variant[1]);
                String at = describe(screen, variant);

                assertTrue(g.contentLeft() >= g.wellLeft(), "content starts left of the well " + at);
                assertTrue(g.contentRight() <= g.wellRight(), "content runs past the well " + at);
                assertTrue(g.contentTop() >= g.wellTop(), "content starts above the well " + at);
                assertTrue(g.contentBottom() <= g.wellBottom(), "content runs below the well " + at);
                assertTrue(g.wellLeft() >= g.left() && g.wellRight() <= g.left() + g.panelWidth(),
                        "well escapes the frame " + at);
            }
        }
    }

    @Test
    @DisplayName("a screen's own header pushes the well down by exactly what it asked for")
    void extraHeaderShiftsTheWell() {
        PanelGeometry plain = new PanelGeometry(640, 360, 0, 0);
        PanelGeometry withHeader = new PanelGeometry(640, 360, 0, 48);

        assertEquals(plain.wellTop() + 48, withHeader.wellTop());
        assertEquals(plain.headerBandBottom(), withHeader.headerBandBottom(),
                "the title band does not move when a screen adds its own header below it");
        assertEquals(plain.wellBottom(), withHeader.wellBottom(),
                "the footer stays put; the header eats into the content, not the buttons");
    }

    @Test
    @DisplayName("a tab strip is reserved above the window, not taken out of it")
    void tabStripReservesSpaceAbove() {
        PanelGeometry plain = new PanelGeometry(640, 360, 0, 0);
        PanelGeometry tabbed = new PanelGeometry(640, 360, 22, 0);

        assertTrue(tabbed.top() >= 22, "no room left above the window for the tabs to sit in");
        assertTrue(tabbed.top() >= plain.top(), "the tabbed window should sit no higher");
    }

    @Test
    @DisplayName("the panel is centred, and stays centred as the screen grows")
    void panelIsCentred() {
        for (int[] screen : screenSizes()) {
            PanelGeometry g = new PanelGeometry(screen[0], screen[1], 0, 0);
            int leftGap = g.left();
            int rightGap = screen[0] - (g.left() + g.panelWidth());
            assertTrue(Math.abs(leftGap - rightGap) <= 1,
                    "off centre by " + Math.abs(leftGap - rightGap) + " at " + screen[0] + "x" + screen[1]);
            assertEquals(g.left() + g.panelWidth() / 2, g.centerX());
        }
    }

    @Test
    @DisplayName("the panel grows with the screen, then stops")
    void panelClampsIntoItsReadableRange() {
        assertEquals(PanelGeometry.MAX_PANEL_W, new PanelGeometry(3840, 2160, 0, 0).panelWidth(),
                "a 4K screen should not get a 3808-pixel-wide line of text");
        assertEquals(PanelGeometry.MAX_PANEL_H, new PanelGeometry(3840, 2160, 0, 0).panelHeight());
        assertTrue(new PanelGeometry(640, 360, 0, 0).panelWidth() > PanelGeometry.MIN_PANEL_W,
                "a mid-sized screen should get more than the minimum");

        // At Minecraft's own 320×240 floor the panel keeps its margins and is still comfortably
        // above the readable minimum -- the floor is not a squeeze.
        PanelGeometry floor = new PanelGeometry(320, 240, 0, 0);
        assertEquals(288, floor.panelWidth(), "the panel should be the screen less its 32px margin");
        assertTrue(floor.panelWidth() >= PanelGeometry.MIN_PANEL_W);
    }

    @Test
    @DisplayName("below the readable minimum the panel gives up its margins rather than overflowing")
    void panelYieldsRatherThanOverflow() {
        // This is the case the second clamp exists for and the only one that can produce a panel
        // wider than its screen: below MIN_PANEL_W the range's own floor is larger than the screen.
        // Minecraft will not hand a screen this small to a GUI, but the arithmetic must not depend
        // on that promise.
        PanelGeometry tiny = new PanelGeometry(200, 150, 0, 0);

        assertEquals(200, tiny.panelWidth(), "the panel is capped by the screen, not by its minimum");
        assertEquals(150, tiny.panelHeight());
        assertEquals(0, tiny.left());
        assertEquals(0, tiny.top());
        assertTrue(tiny.contentWidth() >= 1, "the text column survives even here");
        assertTrue(tiny.wellTop() <= tiny.wellBottom(), "the well does not invert");
    }
}
