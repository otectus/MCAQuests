package dev.otectus.mcaquests.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the mod's chrome: windows, wells, cards, bands, tabs, badges, slots, rules and progress bars.
 *
 * <p>Every one of these was a {@code fill()} rectangle before, sized from {@code this.width} and
 * {@code this.height} at each call site. Routing them through one class is what makes the panels
 * <em>adaptive</em>: because each sprite is nine-sliced rather than blitted whole, a window can be any
 * size from a 320×240-equivalent screen at GUI scale 4 up to a maximised 4K one without a second
 * texture and without a hard-coded layout.
 *
 * <p>Blending is enabled around every draw and disabled again afterwards. The HUD background sprite
 * carries real alpha, and {@link GuiGraphics#blit} on this path does <b>not</b> enable blending for
 * you — vanilla's own {@code AbstractButton} calls {@code RenderSystem.enableBlend()} before blitting
 * for exactly this reason.
 */
public final class Panel {

    private Panel() {
    }

    /** Which of the three card frames a row should wear. */
    public enum CardStyle {
        /** An offer or an in-progress quest. */
        RESTING(GuiTextures.CARD),
        /** Every objective satisfied — the same green the log and the HUD already use for "ready". */
        READY(GuiTextures.CARD_READY),
        /** Under the cursor. */
        HOVERED(GuiTextures.CARD_HOVER);

        private final GuiTextures.Sprite sprite;

        CardStyle(GuiTextures.Sprite sprite) {
            this.sprite = sprite;
        }
    }

    private static void draw(GuiTextures.Sprite sprite, GuiGraphics graphics, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        sprite.nineSlice(graphics, x, y, w, h);
        RenderSystem.disableBlend();
    }

    /** The outer frame of a full screen. */
    public static void window(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.WINDOW, graphics, x, y, w, h);
    }

    /** The inset area content scrolls inside. Draw it after {@link #window}, inside the frame. */
    public static void well(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.WELL, graphics, x, y, w, h);
    }

    /** The band a screen title and its tabs sit on. */
    public static void header(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.BAND_HEADER, graphics, x, y, w, h);
    }

    /** The band the footer buttons sit on. */
    public static void footer(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.BAND_FOOTER, graphics, x, y, w, h);
    }

    public static void card(GuiGraphics graphics, int x, int y, int w, int h, CardStyle style) {
        draw(style.sprite, graphics, x, y, w, h);
    }

    /** The translucent tracker background. Sits over the world, so it must not become a wall. */
    public static void hud(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.HUD, graphics, x, y, w, h);
    }

    /** The pill behind a difficulty or category label. */
    public static void badge(GuiGraphics graphics, int x, int y, int w, int h) {
        draw(GuiTextures.BADGE, graphics, x, y, w, h);
    }

    /** An 18×18 item slot. An item drawn at {@code (x + 1, y + 1)} sits in it correctly. */
    public static void slot(GuiGraphics graphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiTextures.SLOT.blit(graphics, x, y);
        RenderSystem.disableBlend();
    }

    /** A horizontal rule between sections. Three pixels tall. */
    public static void divider(GuiGraphics graphics, int x, int y, int w) {
        draw(GuiTextures.DIVIDER, graphics, x, y, w, GuiTextures.DIVIDER.height());
    }

    /** Height of a progress bar, so callers can reserve the row without guessing. */
    public static int barHeight() {
        return GuiTextures.BAR_TRACK.height();
    }

    /**
     * How many pixels of a {@code width}-wide bar are filled.
     *
     * <p>Kept separate from the drawing, and pure, so the saturating cases are unit-testable without
     * a render context: a required count of zero reads as complete rather than dividing by it, and
     * progress beyond the requirement never overruns the track. The multiplication widens to
     * {@code long} because a project's shared total is unbounded.
     */
    public static int fillWidth(int width, int current, int required) {
        if (width <= 0) {
            return 0;
        }
        if (required <= 0) {
            return width;
        }
        long filled = (long) width * Math.max(0, current) / required;
        return (int) Math.max(0, Math.min(width, filled));
    }

    /** Track plus one fill. */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int current, int required,
                           GuiTextures.Sprite fill) {
        int height = barHeight();
        draw(GuiTextures.BAR_TRACK, graphics, x, y, width, height);
        int filled = fillWidth(width, current, required);
        if (filled > 0) {
            draw(fill, graphics, x, y, filled, height);
        }
    }

    /**
     * A project bar: everybody's progress in green, your own share drawn over it in blue.
     *
     * <p>Your contribution is clamped to the shared total, because a project's requirement can be
     * lowered by a datapack reload after you have already given more than it now asks for — and a
     * blue segment sticking out past the green one would read as a bug rather than as generosity.
     */
    public static void contributionBar(GuiGraphics graphics, int x, int y, int width,
                                       int shared, int yours, int required) {
        bar(graphics, x, y, width, shared, required, GuiTextures.BAR_GREEN);
        int mine = fillWidth(width, Math.min(yours, shared), required);
        if (mine > 0) {
            draw(GuiTextures.BAR_BLUE, graphics, x, y, mine, barHeight());
        }
    }

    /** An icon at its natural 16×16. */
    public static void icon(GuiGraphics graphics, GuiTextures.Sprite sprite, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        sprite.blit(graphics, x, y);
        RenderSystem.disableBlend();
    }

    /**
     * An icon at {@code scale}, for a row too short to give it its full height.
     *
     * <p>Scaled rather than drawn a second time at a smaller size: at 0.5 every sprite on the sheet
     * lands on exact pixel boundaries, so an 8×8 draw is a clean halving with no filtering artefacts,
     * and the art stays a single source.
     */
    public static void iconScaled(GuiGraphics graphics, GuiTextures.Sprite sprite, int x, int y,
                                  float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        icon(graphics, sprite, 0, 0);
        graphics.pose().popPose();
    }
}
