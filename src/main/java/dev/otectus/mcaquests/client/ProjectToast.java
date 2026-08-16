package dev.otectus.mcaquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Toast shown when a community-project phase advances (spec 0.4.0). */
public class ProjectToast implements Toast {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/toasts.png");

    private final Component projectTitle;
    private final Component phaseLabel;

    public ProjectToast(Component projectTitle, Component phaseLabel) {
        this.projectTitle = projectTitle;
        this.phaseLabel = phaseLabel;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        graphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        Font font = toastComponent.getMinecraft().font;
        graphics.drawString(font, Component.translatable("mcaquests.toast.project_advanced"), 18, 7, 0x5CFF5C, false);
        graphics.drawString(font, projectTitle, 18, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
