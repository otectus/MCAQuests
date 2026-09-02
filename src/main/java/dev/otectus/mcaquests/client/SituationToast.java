package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import net.minecraft.network.chat.Component;

/** Toast shown when a nearby village opens a situation that needs help (0.8.0). */
public class SituationToast extends McaToast {

    public SituationToast(Component title) {
        super(Component.translatable("mcaquests.toast.situation_opened"), Palette.TOAST_SITUATION, title,
                GuiTextures.ICON_SITUATION);
    }
}
