package dev.otectus.mcaquests.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * A square button that shows a glyph instead of a label: the window's tab strip, and the journal's
 * link through to a village's deeds.
 *
 * <p>The journal's link is why this is a real widget rather than a drawn rectangle. It used to be a
 * {@code DeedsLink} record — an {@code (x0, y0, x1, y1)} rectangle rebuilt every frame and hit-tested
 * by hand in {@code mouseClicked}. That works for a mouse and for nothing else: it could not be
 * reached by keyboard, could not be focused, and was invisible to the narrator, which the UI
 * acceptance bar requires. Being a real {@link Button} makes all three true for free.
 *
 * <p>The message is not drawn — it is the accessible name, so the narrator has something to say.
 */
public class IconButton extends Button {

    /** How a tab-strip button should draw when it is the tab you are already looking at. */
    public enum Look {
        /** A normal pressable control. */
        BUTTON,
        /** The tab whose page is showing: framed as continuous with the panel below it. */
        TAB_SELECTED,
        /** A tab you could switch to. */
        TAB_UNSELECTED
    }

    private final GuiTextures.Sprite icon;
    private final Look look;

    public IconButton(int x, int y, int width, int height, Component narration,
                      GuiTextures.Sprite icon, Look look, Button.OnPress onPress) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.look = look;
    }

    /** Convenience for the common case: a 20×20 button with a tooltip and no tab framing. */
    public static IconButton of(int x, int y, GuiTextures.Sprite icon, Component narration,
                                @Nullable Component tooltip, Button.OnPress onPress) {
        IconButton button = new IconButton(x, y, 20, 20, narration, icon, Look.BUTTON, onPress);
        button.setTooltip(tooltip == null ? null : Tooltip.create(tooltip));
        return button;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        background().nineSlice(graphics, getX(), getY(), getWidth(), getHeight());

        // Centred, and rounded the same way in both axes so a glyph never lands half a pixel off in
        // one direction and dead centre in the other.
        int iconX = getX() + (getWidth() - icon.width()) / 2;
        int iconY = getY() + (getHeight() - icon.height()) / 2;
        icon.blit(graphics, iconX, iconY);
        RenderSystem.disableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private GuiTextures.Sprite background() {
        boolean highlighted = isHoveredOrFocused() && this.active;
        return switch (look) {
            case TAB_SELECTED -> GuiTextures.TAB_SELECTED;
            case TAB_UNSELECTED -> highlighted ? GuiTextures.TAB_HOVER : GuiTextures.TAB_UNSELECTED;
            case BUTTON -> {
                if (!this.active) {
                    yield GuiTextures.BUTTON_DISABLED;
                }
                yield highlighted ? GuiTextures.BUTTON_HOVER : GuiTextures.BUTTON;
            }
        };
    }
}
