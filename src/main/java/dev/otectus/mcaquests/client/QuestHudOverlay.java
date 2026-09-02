package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.HudAnchor;
import dev.otectus.mcaquests.McaQuestsConfig.HudBackground;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

/**
 * HUD tracker for active MCA quests — each shows its title, giver, and first objective. Position is
 * fully configurable via {@code questTrackerAnchor} (corner) + {@code questTrackerX/Y} offsets (spec section 21).
 *
 * <p>The background is the mod's nine-sliced panel rather than the flat translucent rectangle it used
 * to be; {@code questTrackerStyle} keeps the old one available, and {@code questTrackerBackground}
 * still decides whether there is one at all.
 *
 * <p>Right-anchored lines keep their indent. They used to lose it — every line was flushed to the
 * right edge, so heading, quest and objective all started in the same column and the hierarchy the
 * indents exist to show was visible in two corners out of four.
 */
public class QuestHudOverlay implements IGuiOverlay {

    private static final int LINE_HEIGHT = 12;
    /** A section heading's row, which is taller because it carries a 16px glyph. */
    private static final int HEADING_HEIGHT = 15;
    /** Blank space above the first row of a quest or project, so the tracker reads as blocks. */
    private static final int GROUP_GAP = 4;
    private static final int PADDING = 2;
    /** The glyph gutter on a heading row. */
    private static final int ICON_GUTTER = 18;
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
            lines.add(Line.heading(Component.translatable("mcaquests.hud.title"), Palette.Hud.TITLE,
                    GuiTextures.ICON_QUEST));
            for (int i = 0; i < max; i++) {
                QuestLogEntry entry = entries.get(i);
                MutableComponent title = entry.title().copy()
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(entry.giverName().copy().withStyle(ChatFormatting.GRAY));
                // "Ready" in words as well as in colour. The row was green and nothing else, which is
                // no information at all to a player who cannot tell it from the objective green above
                // it, and none whatsoever to one who is colour-blind.
                if (entry.ready()) {
                    title.append(Component.translatable("mcaquests.hud.ready_suffix")
                            .withStyle(ChatFormatting.GRAY));
                }
                // A dot marks the quest the marker and the outline are about. Drawn rather than
                // implied by position, because the tracker lists several and the followed one is not
                // necessarily first.
                int color = entry.ready() ? Palette.Hud.READY : Palette.Hud.TEXT;
                // Every quest after the first opens with a gap, so its title, objective, guidance and
                // deadline rows read as one block rather than as more of the quest above.
                int gap = i == 0 ? 0 : GROUP_GAP;
                lines.add(entry.tracked()
                        ? Line.icon(title, color, 2, GuiTextures.ICON_DOT, gap)
                        : Line.of(title, color, 2, gap));
                if (!entry.objectives().isEmpty()) {
                    // The counts used to be inside the sentence; now they are numbers, so the tracker
                    // adds them back as text and draws the bar the numbers were always describing.
                    CardObjective first = firstIncomplete(entry.objectives());
                    if (first.unavailable() || first.required() <= 0) {
                        lines.add(Line.of(first.text(), Palette.Hud.OBJECTIVE, 6));
                    } else {
                        lines.add(Line.withBar(first.text().copy().append(Component.literal(
                                        "  " + first.current() + "/" + first.required())),
                                first.satisfied() ? Palette.Hud.READY : Palette.Hud.OBJECTIVE, 6,
                                first.current(), first.required()));
                    }
                }
                // Where to go, how far, which way, and — since 1.5.0 — the coordinates. Every row
                // gets its own, because the server now resolves a destination per quest rather than
                // one per player; the world marker still stands on exactly one of them.
                if (McaQuestsConfig.CLIENT.showQuestTargetDirection.get()) {
                    guidanceLine(entry, minecraft).ifPresent(line ->
                            lines.add(Line.of(line, Palette.Hud.DIRECTION, 6)));
                }
                // A live deadline countdown for quests with a time-based failure (none when ready to turn in).
                if (entry.deadlineGameTime().isPresent() && !entry.ready()) {
                    long remaining = Math.max(0L, entry.deadlineGameTime().getAsLong() - gameTime);
                    lines.add(Line.of(Component.translatable("mcaquests.hud.deadline", formatCountdown(remaining)),
                            remaining <= URGENT_TICKS ? Palette.Hud.URGENT : Palette.Hud.WARNING, 6));
                }
            }
        }
        if (!projects.isEmpty()) {
            int pmax = Math.min(projects.size(), McaQuestsConfig.CLIENT.projectTrackerMaxEntries.get());
            lines.add(Line.heading(Component.translatable("mcaquests.hud.projects"), Palette.Hud.HEADING,
                    GuiTextures.ICON_PROJECT));
            for (int i = 0; i < pmax; i++) {
                ProjectLogEntry project = projects.get(i);
                MutableComponent header = project.title().copy()
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(project.phaseLabel().copy().withStyle(ChatFormatting.GRAY));
                lines.add(Line.of(header, Palette.Hud.TEXT, 2, i == 0 ? 0 : GROUP_GAP));
                ProjectObjectiveLine first = firstIncomplete(project);
                if (first != null) {
                    // The counts were already here; the bar under them is what makes "nearly there"
                    // readable without stopping to do the division.
                    lines.add(Line.withBar(first.label().copy()
                                    .append(Component.literal("  " + first.sharedCurrent() + "/" + first.required())),
                            Palette.Hud.OBJECTIVE, 6, first.sharedCurrent(), first.required()));
                }
            }
        }

        int blockWidth = 0;
        int blockHeight = 0;
        for (Line line : lines) {
            blockWidth = Math.max(blockWidth, line.leftGutter() + line.indent() + font.width(line.text()));
            blockHeight += line.height();
        }

        HudAnchor anchor = McaQuestsConfig.CLIENT.questTrackerAnchor.get();
        int offsetX = McaQuestsConfig.CLIENT.questTrackerX.get();
        int offsetY = McaQuestsConfig.CLIENT.questTrackerY.get();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;

        int originX = right ? screenWidth - offsetX - blockWidth : offsetX;
        int originY = bottom ? screenHeight - offsetY - blockHeight : offsetY;
        int rightEdge = originX + blockWidth;

        if (McaQuestsConfig.CLIENT.questTrackerBackground.get()) {
            int padded = PADDING + 2;
            if (McaQuestsConfig.CLIENT.questTrackerStyle.get() == HudBackground.PANEL) {
                Panel.hud(graphics, originX - padded, originY - padded,
                        blockWidth + padded * 2, blockHeight + padded * 2);
            } else {
                graphics.fill(originX - PADDING, originY - PADDING,
                        rightEdge + PADDING, originY + blockHeight + PADDING, Palette.Hud.FILL_SHADED);
            }
        }

        int y = originY;
        for (Line line : lines) {
            // Right-anchored lines are mirrored rather than flattened: the indent is measured from the
            // right edge, so the heading/quest/objective hierarchy survives in all four corners.
            int textWidth = font.width(line.text());
            int rowY = y + line.gapAbove();
            int x = right
                    ? rightEdge - line.indent() - textWidth
                    : originX + line.leftGutter() + line.indent();
            if (line.icon() != null) {
                int iconX = right ? rightEdge - line.indent() - textWidth - ICON_GUTTER : originX;
                // A heading's glyph is full size and sits proud of its taller row; a glyph on an
                // ordinary row shares a 10px line with the text, so it is drawn at half scale and
                // centred on the baseline rather than overlapping the row above.
                if (line.heading()) {
                    Panel.icon(graphics, line.icon(), iconX, rowY - 4);
                } else {
                    Panel.iconScaled(graphics, line.icon(), iconX + 4, rowY - 1, 0.5F);
                }
            }
            graphics.drawString(font, line.text(), x, rowY, line.color());
            if (line.barMax() > 0) {
                Panel.bar(graphics, x, rowY + LINE_HEIGHT - 1, Math.max(8, textWidth), line.barCurrent(), line.barMax(),
                        GuiTextures.BAR_GREEN);
            }
            y += line.height();
        }
    }

    /**
     * The one line that answers "where do I go next", for this row's quest.
     *
     * <p>Every active quest that can name a place gets one. It used to be the marked quest's line and
     * nothing else, so a player holding "enter an ancient city" and "kill eight blazes in a fortress"
     * was told where one of them was and left to guess at the other — both answers existed on the
     * server, and only one was ever sent.
     *
     * <p>Silent once the quest is ready to hand in, because at that point the guidance is the giver
     * and the row already names them.
     */
    private static Optional<Component> guidanceLine(QuestLogEntry entry, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return Optional.empty();
        }
        return ClientGuidanceData.forQuest(entry.questId(), entry.villagerUuid())
                .map(guidance -> GuidanceText.line(guidance.target(), player, minecraft.level));
    }

    /**
     * The first not-yet-satisfied objective of a quest, or the first one when they are all done.
     *
     * <p>The tracker showed objective zero whatever its state, so a three-part quest reported the part
     * that was finished for as long as it was held — the projects below have always shown the next
     * thing to do, and this is the same rule.
     */
    private static CardObjective firstIncomplete(List<CardObjective> objectives) {
        for (CardObjective objective : objectives) {
            if (!objective.satisfied()) {
                return objective;
            }
        }
        return objectives.get(0);
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

    /**
     * One tracker row.
     *
     * @param icon    a glyph drawn in the gutter, or null
     * @param heading whether this row is a section heading. Separate from {@code icon} because a row
     *                can now carry a glyph without being one — the followed quest is marked with a dot
     *                beside its title, and it is a quest, not a section
     * @param barMax   a denominator to draw a progress bar under the row, or 0 for no bar
     * @param gapAbove blank space reserved above the row, used to separate one quest from the next
     */
    private record Line(Component text, int color, int indent, GuiTextures.Sprite icon, boolean heading,
                        int barCurrent, int barMax, int gapAbove) {

        static Line of(Component text, int color, int indent) {
            return of(text, color, indent, 0);
        }

        static Line of(Component text, int color, int indent, int gapAbove) {
            return new Line(text, color, indent, null, false, 0, 0, gapAbove);
        }

        static Line heading(Component text, int color, GuiTextures.Sprite icon) {
            return new Line(text, color, 0, icon, true, 0, 0, 0);
        }

        /** A row with a glyph in the gutter that is not a section heading. */
        static Line icon(Component text, int color, int indent, GuiTextures.Sprite icon, int gapAbove) {
            return new Line(text, color, indent, icon, false, 0, 0, gapAbove);
        }

        static Line withBar(Component text, int color, int indent, int current, int max) {
            return new Line(text, color, indent, null, false, current, max, 0);
        }

        /** Any row with a glyph reserves the gutter, so its text lines up with every other glyphed row. */
        int leftGutter() {
            return icon != null ? ICON_GUTTER : 0;
        }

        int height() {
            if (heading) {
                return gapAbove + HEADING_HEIGHT;
            }
            // A bar row reserves the bar's own height as well as the text's, so the bar sits inside
            // its row instead of running into the line below it.
            return gapAbove + (barMax > 0 ? LINE_HEIGHT + Panel.barHeight() + 2 : LINE_HEIGHT);
        }
    }
}
