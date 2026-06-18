package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/** Small HUD tracker for active MCA quests, pinned top-left (spec section 21). */
public class QuestHudOverlay implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!McaQuestsConfig.CLIENT.showQuestTrackerHud.get() || minecraft.options.hideGui) {
            return;
        }
        List<QuestLogEntry> entries = ClientQuestData.active();
        if (entries.isEmpty()) {
            return;
        }
        int max = Math.min(entries.size(), McaQuestsConfig.CLIENT.questTrackerMaxEntries.get());
        int x = 4;
        int y = 4;
        graphics.drawString(minecraft.font, Component.translatable("mcaquests.hud.title"), x, y, 0xFFE08A);
        y += 11;
        for (int i = 0; i < max; i++) {
            QuestLogEntry entry = entries.get(i);
            graphics.drawString(minecraft.font, entry.title(), x + 2, y, entry.ready() ? 0x5CFF5C : 0xFFFFFF);
            y += 10;
            if (!entry.objectives().isEmpty()) {
                graphics.drawString(minecraft.font, entry.objectives().get(0), x + 6, y, 0xBFBFBF);
                y += 10;
            }
        }
    }
}
