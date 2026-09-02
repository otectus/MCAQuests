package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import net.minecraft.network.chat.Component;

/** Toast shown when the player reaches a new reputation tier with a village (spec 0.7.0). */
public class ReputationTierToast extends McaToast {

    public ReputationTierToast(Component tierName) {
        super(Component.translatable("mcaquests.toast.tier_up"), Palette.WARNING, tierName,
                GuiTextures.ICON_REPUTATION);
    }
}
