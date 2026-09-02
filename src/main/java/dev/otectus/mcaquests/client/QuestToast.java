package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import net.minecraft.network.chat.Component;

/** Toast shown when a quest becomes ready to turn in (spec section 21). */
public class QuestToast extends McaToast {

    public QuestToast(Component questTitle) {
        super(Component.translatable("mcaquests.toast.ready"), Palette.TOAST_READY, questTitle,
                GuiTextures.ICON_QUEST);
    }
}
