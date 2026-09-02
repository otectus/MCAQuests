package dev.otectus.mcaquests.client;

/**
 * Where the window and everything inside it sits, for a given screen size.
 *
 * <p>Deliberately pure integer math with no Minecraft types, for the same reason {@link ScrollView}
 * is: the acceptance bar for this interface is "320×240-equivalent through 4K, at GUI scales 1 to 4,
 * with long Portuguese lines and nothing clipped", and that is a claim a unit test can check at a
 * hundred sizes in a millisecond and a human can check at three sizes in an afternoon.
 *
 * <p>The panel is <em>adaptive</em>. Every sprite it is drawn from is nine-sliced, so rather than one
 * hard-coded window size the frame grows with the screen and is clamped into a range that stays
 * readable at both ends. The clamp is applied twice on each axis — once into that range, and once
 * against the screen itself — because on a very short or narrow display the range's own minimum
 * would otherwise be larger than the screen it has to fit inside.
 *
 * <p>Every accessor is derived, never stored, so there is exactly one definition of each edge and no
 * way for two of them to drift apart.
 */
final class PanelGeometry {

    /** Thickness of the window frame; content starts inside it. Matches the sprite's 8px slice. */
    static final int FRAME = 8;
    /** The band the title and any tabs sit on. */
    static final int HEADER_H = 22;
    /** The band the footer buttons sit on: a 20px button with 3px above and below. */
    static final int FOOTER_H = 26;
    /** Breathing room between the well's edge and the content inside it. */
    static final int PAD = 6;
    /** Width of the scrollbar gutter, always reserved so the layout does not shift as content grows. */
    static final int SCROLLBAR_W = 6;

    /** Below this the panel is too narrow for a wrapped objective line to read as one. */
    static final int MIN_PANEL_W = 240;
    /** Above this the eye has to travel too far along a line of text. */
    static final int MAX_PANEL_W = 352;
    static final int MIN_PANEL_H = 180;
    static final int MAX_PANEL_H = 232;

    private final int screenWidth;
    private final int screenHeight;
    private final int tabStrip;
    private final int extraHeader;

    /**
     * @param tabStrip    pixels reserved above the window for a tab strip, or 0
     * @param extraHeader fixed rows between the title band and the well, or 0
     */
    PanelGeometry(int screenWidth, int screenHeight, int tabStrip, int extraHeader) {
        this.screenWidth = Math.max(0, screenWidth);
        this.screenHeight = Math.max(0, screenHeight);
        this.tabStrip = Math.max(0, tabStrip);
        this.extraHeader = Math.max(0, extraHeader);
    }

    int panelWidth() {
        return Math.min(screenWidth, clamp(screenWidth - 32, MIN_PANEL_W, MAX_PANEL_W));
    }

    int panelHeight() {
        int available = Math.max(0, screenHeight - tabStrip);
        return Math.min(available, clamp(screenHeight - 32 - tabStrip, MIN_PANEL_H, MAX_PANEL_H));
    }

    int left() {
        return (screenWidth - panelWidth()) / 2;
    }

    int top() {
        return tabStrip + Math.max(0, (screenHeight - tabStrip - panelHeight()) / 2);
    }

    int centerX() {
        return left() + panelWidth() / 2;
    }

    int wellLeft() {
        return left() + FRAME;
    }

    /** Exclusive. */
    int wellRight() {
        return Math.max(wellLeft(), left() + panelWidth() - FRAME);
    }

    int wellTop() {
        return top() + FRAME + HEADER_H + extraHeader;
    }

    /** Exclusive. Never above {@link #wellTop()}, however little room the screen leaves. */
    int wellBottom() {
        return Math.max(wellTop(), top() + panelHeight() - FRAME - FOOTER_H);
    }

    int scrollbarX() {
        return Math.max(wellLeft(), wellRight() - PAD - SCROLLBAR_W);
    }

    int contentLeft() {
        return wellLeft() + PAD;
    }

    /** Exclusive, and clear of the scrollbar gutter. */
    int contentRight() {
        return Math.max(contentLeft() + 1, scrollbarX() - PAD);
    }

    int contentWidth() {
        return contentRight() - contentLeft();
    }

    int contentTop() {
        return wellTop() + PAD;
    }

    /** Exclusive. */
    int contentBottom() {
        return Math.max(contentTop(), wellBottom() - PAD);
    }

    /** Top of the header band, where a screen's own header rows begin. */
    int headerBandBottom() {
        return top() + FRAME + HEADER_H;
    }

    int footerButtonY() {
        return top() + panelHeight() - FRAME - FOOTER_H + 3;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
