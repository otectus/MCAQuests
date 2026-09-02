package dev.otectus.mcaquests.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * A button drawn from the mod's own sheet, in two heights.
 *
 * <p>The reason this exists rather than a vanilla {@link Button} is the quest log's Abandon button,
 * which is <b>60×12</b>. Vanilla nine-slices a 200×20 sprite with 4px vertical slices, so at twelve
 * pixels tall the top and bottom borders consume two thirds of the button and the face is squeezed to
 * a sliver — it has rendered visibly squashed for as long as that row has existed.
 * {@link Style#COMPACT} is drawn to work at that height.
 *
 * <p>It also carries the mod's palette rather than vanilla's flat white label, so a disabled control
 * reads as disabled instead of merely unresponsive.
 *
 * <p>It extends {@link Button} rather than {@code AbstractButton} so that {@link Button.OnPress} and
 * vanilla's narration come along unchanged, and a call site moving here keeps its lambda exactly as
 * written.
 *
 * <p>Vanilla's own {@code Button} is deliberately still used in one place: the Quests button
 * {@code McaScreenButtons} injects into MCA's interact screen, which has to match the buttons around
 * it rather than the ones in here.
 */
public class McaButton extends Button {

    /** Which sprite family a button wears, chosen by the height it has to survive. */
    public enum Style {
        /** The 20px-tall default, matching every vanilla button's height. */
        STANDARD(GuiTextures.BUTTON, GuiTextures.BUTTON_HOVER, GuiTextures.BUTTON_DISABLED),
        /** Drawn to stay legible down to twelve pixels tall. See the class note. */
        COMPACT(GuiTextures.BUTTON_COMPACT, GuiTextures.BUTTON_COMPACT_HOVER,
                GuiTextures.BUTTON_COMPACT_DISABLED);

        private final GuiTextures.Sprite resting;
        private final GuiTextures.Sprite hovered;
        private final GuiTextures.Sprite disabled;

        Style(GuiTextures.Sprite resting, GuiTextures.Sprite hovered, GuiTextures.Sprite disabled) {
            this.resting = resting;
            this.hovered = hovered;
            this.disabled = disabled;
        }

        private GuiTextures.Sprite spriteFor(boolean active, boolean highlighted) {
            if (!active) {
                return disabled;
            }
            return highlighted ? hovered : resting;
        }
    }

    private final Style style;

    protected McaButton(int x, int y, int width, int height, Component message, Style style,
                        Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    /**
     * Mirrors the shape of {@link Button#builder} on purpose — the screens were written against it,
     * so moving a call site here keeps its lambda and its {@code .bounds(...)} exactly as written.
     * It cannot share the name: {@code builder} is already a static method on {@code Button} with a
     * different return type, and a subclass may not hide it.
     */
    public static Builder create(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        style.spriteFor(this.active, isHoveredOrFocused())
                .nineSlice(graphics, getX(), getY(), getWidth(), getHeight());
        RenderSystem.disableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int colour = labelColour() | Mth.ceil(this.alpha * 255.0F) << 24;
        // renderString scrolls a label too long for the button rather than clipping it, which the
        // Portuguese locale needs more often than English does.
        renderString(graphics, Minecraft.getInstance().font, colour);
    }

    /**
     * Vanilla's own two answers, because the button face is vanilla's own dark widget grey.
     *
     * <p>This used to brighten from a warm tan at rest to white on hover, which the old dark panel
     * needed to show the control was live. The nine-sliced sprite now changes face on hover, so the
     * label changing colour as well was two signals for one event — and a coloured label on a dark
     * button is not something Minecraft does.
     */
    private int labelColour() {
        return this.active ? Palette.BUTTON_LABEL : Palette.BUTTON_LABEL_DISABLED;
    }

    /** A small mirror of vanilla's button builder, plus the style. */
    public static final class Builder {

        private final Component message;
        private final Button.OnPress onPress;
        private Style style = Style.STANDARD;
        private int x;
        private int y;
        private int width = Button.DEFAULT_WIDTH;
        private int height = Button.DEFAULT_HEIGHT;
        @Nullable
        private Tooltip tooltip;

        private Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        /** Hover and keyboard-focus help. The mod had none of this before. */
        public Builder tooltip(@Nullable Component tooltip) {
            this.tooltip = tooltip == null ? null : Tooltip.create(tooltip);
            return this;
        }

        public McaButton build() {
            McaButton button = new McaButton(x, y, width, height, message, style, onPress);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
