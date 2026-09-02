package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * The one toast the mod shows, in four flavours.
 *
 * <p>All four were separate, byte-identical twenty-nine-line classes, and all four had the same three
 * faults:
 *
 * <ul>
 *   <li><b>No icon.</b> Every one of them drew its text at {@code x = 18} — which is the vanilla
 *       <em>icon gutter</em>. Each toast the mod has ever shown had an empty eighteen-pixel square
 *       where a glyph belongs.</li>
 *   <li><b>No wrapping.</b> Vanilla splits toast text to 125 pixels and cross-fades between the lines
 *       when it does not fit; these drew one unwrapped string, so a long quest title simply ran off
 *       the right edge of the frame.</li>
 *   <li><b>A hardcoded five seconds.</b> Vanilla multiplies its display time by
 *       {@link ToastComponent#getNotificationDisplayTimeMultiplier()}, the player's notification-time
 *       accessibility setting. Turning that slider up did nothing to any toast this mod showed.</li>
 * </ul>
 *
 * <p>The frame stays vanilla's own {@code textures/gui/toasts.png} rather than becoming a mod texture,
 * so a resource pack restyles these along with every other toast on screen.
 */
public abstract class McaToast implements Toast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");

    /** Vanilla's own display time, before the accessibility multiplier. */
    private static final long DISPLAY_TIME = 5000L;
    /** Where the icon sits, and where the text starts beside it — vanilla's own offsets. */
    private static final int ICON_X = 8;
    private static final int ICON_Y = 8;
    private static final int TEXT_X = 30;
    /** The width vanilla wraps toast text to. */
    private static final int TEXT_WIDTH = 125;
    /** How long a two-line body shows its heading before cross-fading to the body, in ms. */
    private static final long SWAP_AT = 1500L;
    private static final float FADE_MS = 300.0F;

    private final Component heading;
    private final int headingColour;
    private final Component body;
    private final GuiTextures.Sprite icon;

    protected McaToast(Component heading, int headingColour, Component body, GuiTextures.Sprite icon) {
        this.heading = heading;
        this.headingColour = headingColour;
        this.body = body;
        this.icon = icon;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        Panel.icon(graphics, icon, ICON_X, ICON_Y);

        List<FormattedCharSequence> lines = font.split(body, TEXT_WIDTH);
        if (lines.size() <= 1) {
            graphics.drawString(font, heading, TEXT_X, 7, headingColour | 0xFF000000, false);
            if (!lines.isEmpty()) {
                graphics.drawString(font, lines.get(0), TEXT_X, 18, Palette.TEXT | 0xFF000000, false);
            }
        } else {
            renderTwoPhase(graphics, font, lines, timeSinceLastVisible);
        }
        return timeSinceLastVisible >= displayTime(toastComponent) ? Visibility.HIDE : Visibility.SHOW;
    }

    /**
     * A body too long for one line gets vanilla's treatment: the heading alone first, then a fade
     * across to the wrapped body, both vertically centred in the frame.
     */
    private void renderTwoPhase(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines,
                                long timeSinceLastVisible) {
        if (timeSinceLastVisible < SWAP_AT) {
            int alpha = Mth.floor(Mth.clamp((float) (SWAP_AT - timeSinceLastVisible) / FADE_MS, 0.0F, 1.0F)
                    * 255.0F) << 24;
            graphics.drawString(font, heading, TEXT_X, 11, headingColour | alpha, false);
            return;
        }
        int alpha = Mth.floor(Mth.clamp((float) (timeSinceLastVisible - SWAP_AT) / FADE_MS, 0.0F, 1.0F)
                * 252.0F) << 24;
        int y = this.height() / 2 - lines.size() * 9 / 2;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, TEXT_X, y, Palette.TEXT | alpha, false);
            y += 9;
        }
    }

    /** Vanilla's five seconds, scaled by the player's notification-time accessibility setting. */
    private static double displayTime(ToastComponent toastComponent) {
        return DISPLAY_TIME * toastComponent.getNotificationDisplayTimeMultiplier();
    }
}
