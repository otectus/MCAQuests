package dev.otectus.mcaquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Toast shown when a quest becomes ready to turn in (spec section 21). */
public class QuestToast implements Toast {

    /**
     * The toast frame is a GUI sprite since 1.20.2 — {@code textures/gui/toasts.png} no longer
     * exists, and blitting it draws the missing-texture checkerboard. {@code toast/advancement} is
     * the sprite carved from the (0,0,160,32) region this used to sample.
     */
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/advancement");

    private final Component questTitle;

    public QuestToast(Component questTitle) {
        this.questTitle = questTitle;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        graphics.drawString(font, Component.translatable("mcaquests.toast.ready"), 18, 7, 0xFFCC44, false);
        graphics.drawString(font, questTitle, 18, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
