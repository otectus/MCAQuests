package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.HudAnchor;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.project.ProjectLogEntry;
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
    private static final int PADDING = 2;
    /** Below this many ticks remaining the countdown turns red ("expiring"); amber above it. */
    private static final long URGENT_TICKS = 1200L; // ~1 minute

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientQuestData.isHudVisible() || minecraft.options.hideGui) {
            return;
        }
        List<QuestLogEntry> entries = McaQuestsConfig.CLIENT.showQuestTrackerHud.get()
                ? ClientQuestData.active() : List.of();
        List<ProjectLogEntry> projects = McaQuestsConfig.CLIENT.showProjectTrackerHud.get()
                ? ClientProjectData.projects() : List.of();
        if (entries.isEmpty() && projects.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;

        List<Line> lines = new ArrayList<>();
        if (!entries.isEmpty()) {
            int max = Math.min(entries.size(), McaQuestsConfig.CLIENT.questTrackerMaxEntries.get());
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
                // A live deadline countdown for quests with a time-based failure (none when ready to turn in).
                if (entry.deadlineGameTime().isPresent() && !entry.ready()) {
                    long remaining = Math.max(0L, entry.deadlineGameTime().getAsLong() - gameTime);
                    lines.add(new Line(Component.translatable("mcaquests.hud.deadline", formatCountdown(remaining)),
                            remaining <= URGENT_TICKS ? 0xFF5C5C : 0xFFD24D, 6));
                }
            }
        }
        if (!projects.isEmpty()) {
            int pmax = Math.min(projects.size(), McaQuestsConfig.CLIENT.projectTrackerMaxEntries.get());
            lines.add(new Line(Component.translatable("mcaquests.hud.projects"), 0x5CC8FF, 0));
            for (int i = 0; i < pmax; i++) {
                ProjectLogEntry project = projects.get(i);
                MutableComponent header = project.title().copy()
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(project.phaseLabel().copy().withStyle(ChatFormatting.GRAY));
                lines.add(new Line(header, 0xFFFFFF, 2));
                ProjectObjectiveLine first = firstIncomplete(project);
                if (first != null) {
                    lines.add(new Line(first.label().copy()
                            .append(Component.literal("  " + first.sharedCurrent() + "/" + first.required())), 0xBFBFBF, 6));
                }
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

        if (McaQuestsConfig.CLIENT.questTrackerBackground.get()) {
            graphics.fill(originX - PADDING, originY - PADDING,
                    rightEdge + PADDING, originY + blockHeight + PADDING, 0x80000000);
        }

        int y = originY;
        for (Line line : lines) {
            int x = right ? rightEdge - font.width(line.text) : originX + line.indent;
            graphics.drawString(font, line.text, x, y, line.color);
            y += LINE_HEIGHT;
        }
    }

    /** The first not-yet-complete objective of a project (or the first objective if all are done). */
    private static ProjectObjectiveLine firstIncomplete(ProjectLogEntry project) {
        for (ProjectObjectiveLine line : project.objectives()) {
            if (line.sharedCurrent() < line.required()) {
                return line;
            }
        }
        return project.objectives().isEmpty() ? null : project.objectives().get(0);
    }

    /** Formats remaining ticks (20/sec) as {@code m:ss}, e.g. {@code 1:05}. */
    private static String formatCountdown(long remainingTicks) {
        long seconds = remainingTicks / 20L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    private record Line(Component text, int color, int indent) {
    }
}
