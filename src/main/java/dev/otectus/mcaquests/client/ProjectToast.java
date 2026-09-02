package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import net.minecraft.network.chat.Component;

/**
 * Toast shown when a community-project phase advances (spec 0.4.0).
 *
 * <p>It now says <em>which</em> phase. {@code phaseLabel} was accepted by the constructor, stored in a
 * field, sent over the wire by {@code ProjectPhaseToastS2CPacket} — and never drawn, so the toast for
 * every phase of every project read identically.
 */
public class ProjectToast extends McaToast {

    public ProjectToast(Component projectTitle, Component phaseLabel) {
        super(Component.translatable("mcaquests.toast.project_advanced"), Palette.READY,
                Component.translatable("mcaquests.toast.project_phase", projectTitle, phaseLabel),
                GuiTextures.ICON_PROJECT);
    }
}
