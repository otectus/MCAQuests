package dev.otectus.mcaquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Toast shown when a nearby village opens a situation that needs help (0.8.0). */
public class SituationToast implements Toast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");

    private final Component title;

    public SituationToast(Component title) {
        this.title = title;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        graphics.drawString(font, Component.translatable("mcaquests.toast.situation_opened"), 18, 7, 0xFFD15C, false);
        graphics.drawString(font, title, 18, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
