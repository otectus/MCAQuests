package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.HudAnchor;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD tracker for active MCA quests — each shows its title, giver, and first objective. Position is
 * fully configurable via {@code questTrackerAnchor} (corner) + {@code questTrackerX/Y} offsets (spec section 21).
 */
public class QuestHudOverlay implements IGuiOverlay {

    private static final int LINE_HEIGHT = 10;

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
        Font font = minecraft.font;
        int max = Math.min(entries.size(), McaQuestsConfig.CLIENT.questTrackerMaxEntries.get());

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("mcaquests.hud.title"), 0xFFE08A, 0));
        for (int i = 0; i < max; i++) {
            QuestLogEntry entry = entries.get(i);
            MutableComponent title = entry.title().copy()
                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                    .append(entry.giverName().copy().withStyle(ChatFormatting.GRAY));
            lines.add(new Line(title, entry.ready() ? 0x5CFF5C : 0xFFFFFF, 2));
            if (!entry.objectives().isEmpty()) {
                lines.add(new Line(entry.objectives().get(0), 0xBFBFBF, 6));
            }
        }

        int blockWidth = 0;
        for (Line line : lines) {
            blockWidth = Math.max(blockWidth, line.indent + font.width(line.text));
        }
        int blockHeight = lines.size() * LINE_HEIGHT;

        HudAnchor anchor = McaQuestsConfig.CLIENT.questTrackerAnchor.get();
        int offsetX = McaQuestsConfig.CLIENT.questTrackerX.get();
        int offsetY = McaQuestsConfig.CLIENT.questTrackerY.get();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;

        int originX = right ? screenWidth - offsetX - blockWidth : offsetX;
        int originY = bottom ? screenHeight - offsetY - blockHeight : offsetY;
        int rightEdge = originX + blockWidth;

        int y = originY;
        for (Line line : lines) {
            int x = right ? rightEdge - font.width(line.text) : originX + line.indent;
            graphics.drawString(font, line.text, x, y, line.color);
            y += LINE_HEIGHT;
        }
    }

    private record Line(Component text, int color, int indent) {
    }
}
