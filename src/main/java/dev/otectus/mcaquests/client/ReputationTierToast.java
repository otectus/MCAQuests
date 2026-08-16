package dev.otectus.mcaquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Toast shown when the player reaches a new reputation tier with a village (spec 0.7.0). */
public class ReputationTierToast implements Toast {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/toasts.png");

    private final Component tierName;

    public ReputationTierToast(Component tierName) {
        this.tierName = tierName;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        graphics.drawString(font, Component.translatable("mcaquests.toast.tier_up"), 18, 7, 0xFFD24C, false);
        graphics.drawString(font, tierName, 18, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
