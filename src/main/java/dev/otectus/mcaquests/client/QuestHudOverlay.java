package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.HudAnchor;
import dev.otectus.mcaquests.McaQuestsConfig.HudBackground;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.client.gui.TrackerBackground;
import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

/**
 * HUD tracker for active MCA quests — each shows its title, giver, and first objective. Position is
 * fully configurable via {@code questTrackerAnchor} (corner) + {@code questTrackerX/Y} offsets (spec section 21).
 *
 * <p>The background is either the mod's nine-sliced panel or a soft, borderless gradient wash that
 * fades in from the top; {@code questTrackerStyle} picks between them, {@code questTrackerOpacity}
 * scales whichever one is drawn without touching the text on top of it, and {@code questTrackerBackground}
 * still decides whether there is one at all.
 *
 * <p>Right-anchored lines keep their indent. They used to lose it — every line was flushed to the
 * right edge, so heading, quest and objective all started in the same column and the hierarchy the
 * indents exist to show was visible in two corners out of four.
 */
public class QuestHudOverlay implements LayeredDraw.Layer {

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
    /** The widest a tracker row may get before it wraps, in scaled pixels. */
    private static final int MAX_LINE_WIDTH = 200;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
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
        int screenWidth = graphics.guiWidth();
        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;

        List<Line> lines = new ArrayList<>();
        if (!entries.isEmpty()) {
            int max = Math.min(entries.size(), McaQuestsConfig.CLIENT.questTrackerMaxEntries.get());
            addHeading(lines, font, screenWidth, Component.translatable("mcaquests.hud.title"),
                    Palette.Hud.TITLE, GuiTextures.ICON_QUEST);
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
                if (entry.tracked()) {
                    addIcon(lines, font, screenWidth, title, color, 2, GuiTextures.ICON_DOT, gap);
                } else {
                    addText(lines, font, screenWidth, title, color, 2, gap);
                }
                if (!entry.objectives().isEmpty()) {
                    // The counts used to be inside the sentence; now they are numbers, so the tracker
                    // adds them back as text and draws the bar the numbers were always describing.
                    CardObjective first = firstIncomplete(entry.objectives());
                    if (first.unavailable() || first.required() <= 0) {
                        addText(lines, font, screenWidth, first.text(), Palette.Hud.OBJECTIVE, 6, 0);
                    } else {
                        addBar(lines, font, screenWidth, first.text().copy().append(Component.literal(
                                        "  " + first.current() + "/" + first.required())),
                                first.satisfied() ? Palette.Hud.READY : Palette.Hud.OBJECTIVE, 6,
                                first.current(), first.required());
                    }
                }
                // Where to go, how far, which way, and — since 1.5.0 — the coordinates. Every row
                // gets its own, because the server now resolves a destination per quest rather than
                // one per player; the world marker still stands on exactly one of them.
                if (McaQuestsConfig.CLIENT.showQuestTargetDirection.get()) {
                    guidanceLine(entry, minecraft).ifPresent(line ->
                            addText(lines, font, screenWidth, line, Palette.Hud.DIRECTION, 6, 0));
                }
                // A live deadline countdown for quests with a time-based failure (none when ready to turn in).
                if (entry.deadlineGameTime().isPresent() && !entry.ready()) {
                    long remaining = Math.max(0L, entry.deadlineGameTime().getAsLong() - gameTime);
                    addText(lines, font, screenWidth,
                            Component.translatable("mcaquests.hud.deadline", formatCountdown(remaining)),
                            remaining <= URGENT_TICKS ? Palette.Hud.URGENT : Palette.Hud.WARNING, 6, 0);
                }
            }
        }
        if (!projects.isEmpty()) {
            int pmax = Math.min(projects.size(), McaQuestsConfig.CLIENT.projectTrackerMaxEntries.get());
            addHeading(lines, font, screenWidth, Component.translatable("mcaquests.hud.projects"),
                    Palette.Hud.HEADING, GuiTextures.ICON_PROJECT);
            for (int i = 0; i < pmax; i++) {
                ProjectLogEntry project = projects.get(i);
                MutableComponent header = project.title().copy()
                        .append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(project.phaseLabel().copy().withStyle(ChatFormatting.GRAY));
                addText(lines, font, screenWidth, header, Palette.Hud.TEXT, 2, i == 0 ? 0 : GROUP_GAP);
                ProjectObjectiveLine first = firstIncomplete(project);
                if (first != null) {
                    // The counts were already here; the bar under them is what makes "nearly there"
                    // readable without stopping to do the division.
                    addBar(lines, font, screenWidth, first.label().copy()
                                    .append(Component.literal("  " + first.sharedCurrent() + "/" + first.required())),
                            Palette.Hud.OBJECTIVE, 6, first.sharedCurrent(), first.required());
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

        int originX = right ? graphics.guiWidth() - offsetX - blockWidth : offsetX;
        int originY = bottom ? graphics.guiHeight() - offsetY - blockHeight : offsetY;
        int rightEdge = originX + blockWidth;

        // Two distinct treatments, not two names for the same dark rectangle: PANEL is the nine-sliced
        // HUD plate, SHADED a soft wash that fades in from the top and leaves the world showing through.
        // questTrackerOpacity scales whichever one is drawn, and never the text on top of it.
        if (McaQuestsConfig.CLIENT.questTrackerBackground.get()) {
            int padded = PADDING + 2;
            int opacity = McaQuestsConfig.CLIENT.questTrackerOpacity.get();
            if (McaQuestsConfig.CLIENT.questTrackerStyle.get() == HudBackground.PANEL) {
                // The plate is a texture, so it can only be dimmed through the shader colour — and the
                // tint has to come off again in a finally, or everything drawn after it (hotbar, chat)
                // inherits it.
                try {
                    if (opacity < 100) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, TrackerBackground.panelAlpha(opacity));
                    }
                    Panel.hud(graphics, originX - padded, originY - padded,
                            blockWidth + padded * 2, blockHeight + padded * 2);
                } finally {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
            } else {
                int[] wash = TrackerBackground.shadedGradient(opacity);
                graphics.fillGradient(originX - PADDING, originY - PADDING,
                        rightEdge + PADDING, originY + blockHeight + PADDING, wash[0], wash[1]);
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

    /** A plain row of text, wrapped if it is too wide or carries line breaks. */
    private static void addText(List<Line> lines, Font font, int screenWidth, Component text, int color,
                                int indent, int gapAbove) {
        addWrapped(lines, font, screenWidth, text, color, indent, null, false, 0, 0, gapAbove);
    }

    /** A section heading, wrapped like any other row. */
    private static void addHeading(List<Line> lines, Font font, int screenWidth, Component text, int color,
                                   GuiTextures.Sprite icon) {
        addWrapped(lines, font, screenWidth, text, color, 0, icon, true, 0, 0, 0);
    }

    /** A glyphed row that is not a section heading. */
    private static void addIcon(List<Line> lines, Font font, int screenWidth, Component text, int color,
                                int indent, GuiTextures.Sprite icon, int gapAbove) {
        addWrapped(lines, font, screenWidth, text, color, indent, icon, false, 0, 0, gapAbove);
    }

    /** A row with a progress bar beneath it; the bar goes under the last wrapped line. */
    private static void addBar(List<Line> lines, Font font, int screenWidth, Component text, int color,
                               int indent, int current, int max) {
        addWrapped(lines, font, screenWidth, text, color, indent, null, false, current, max, 0);
    }

    /**
     * Splits one built component into as many rows as it needs and appends them.
     *
     * <p>Every row goes through here because the tracker does not own its text: an add-on can hand it
     * an objective component with newlines in it (a bounty poster, say), and drawn as a single string
     * that is a row of missing-glyph boxes running off the screen. The screens have always wrapped
     * their text through {@link CardText}; the HUD does the same, and caps its own width besides.
     *
     * <p>The glyph and the gap above belong to the first row, the bar to the last, so a wrapped row
     * still reads — and measures — as one block.
     */
    private static void addWrapped(List<Line> lines, Font font, int screenWidth, Component text, int color,
                                   int indent, GuiTextures.Sprite icon, boolean heading,
                                   int barCurrent, int barMax, int gapAbove) {
        int gutter = icon != null ? ICON_GUTTER : 0;
        int wrapWidth = Math.max(40, Math.min(MAX_LINE_WIDTH, screenWidth / 2) - gutter - indent);
        List<FormattedCharSequence> parts = font.split(text, wrapWidth);
        for (int i = 0; i < parts.size(); i++) {
            FormattedCharSequence part = parts.get(i);
            boolean first = i == 0;
            boolean last = i == parts.size() - 1;
            int gap = first ? gapAbove : 0;
            if (first && heading) {
                lines.add(Line.heading(part, color, icon));
            } else if (first && icon != null) {
                lines.add(Line.icon(part, color, indent, icon, gap));
            } else if (last && barMax > 0) {
                lines.add(Line.withBar(part, color, indent, gap, barCurrent, barMax));
            } else {
                lines.add(Line.of(part, color, indent, gap));
            }
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
        Optional<Component> line = ClientGuidanceData.forQuest(entry.questId(), entry.villagerUuid())
                .map(guidance -> GuidanceText.line(guidance.target(), player, minecraft.level));
        if (line.isPresent() || !entry.ready() || entry.suspended()) {
            return line;
        }
        // Ready, and the server had nowhere to point: the giver is in no loaded chunk.
        return Optional.of(GuidanceText.awaitingGiver(entry.giverName()));
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
    private record Line(FormattedCharSequence text, int color, int indent, GuiTextures.Sprite icon,
                        boolean heading, int barCurrent, int barMax, int gapAbove) {

        static Line of(FormattedCharSequence text, int color, int indent, int gapAbove) {
            return new Line(text, color, indent, null, false, 0, 0, gapAbove);
        }

        static Line heading(FormattedCharSequence text, int color, GuiTextures.Sprite icon) {
            return new Line(text, color, 0, icon, true, 0, 0, 0);
        }

        /** A row with a glyph in the gutter that is not a section heading. */
        static Line icon(FormattedCharSequence text, int color, int indent, GuiTextures.Sprite icon, int gapAbove) {
            return new Line(text, color, indent, icon, false, 0, 0, gapAbove);
        }

        static Line withBar(FormattedCharSequence text, int color, int indent, int gapAbove, int current, int max) {
            return new Line(text, color, indent, null, false, current, max, gapAbove);
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
