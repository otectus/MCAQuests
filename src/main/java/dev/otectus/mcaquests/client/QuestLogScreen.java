package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.McaButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.QuestAbandonFromLogC2SPacket;
import dev.otectus.mcaquests.client.gui.IconButton;
import dev.otectus.mcaquests.network.QuestTrackC2SPacket;
import net.minecraft.client.gui.components.Tooltip;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import dev.otectus.mcaquests.compat.MapWaypointBridge;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keybind-accessible list of the player's active MCA quests (spec section 21), with a per-quest
 * Abandon button.
 *
 * <p>Abandoning here needs no villager, unlike the button in the villager menu — that is the point:
 * it is the only way to drop a quest whose giver is dead, unloaded, or in another dimension.
 *
 * <p>It is the first page of the player's book; the journal is the second, reached by the tab strip
 * above the window rather than by the Back-then-Journal round trip it used to take.
 */
public class QuestLogScreen extends McaQuestsScreen {

    /**
     * The gutter every objective line is indented by, shared by the height calculation and the draw so
     * the indents match. It used to be the literal bullet {@code "  - "}; it is now blank space with a
     * state glyph drawn into it, which costs no layout because the text still starts where it did.
     */
    private static final String BULLET = ObjectiveGlyphs.GUTTER;
    /** Inset from an entry's frame to its text. Matches the card sprite's nine-slice border. */
    private static final int CARD_PAD = 5;
    private static final int CARD_GAP = 4;
    /** The Abandon button, and the room the title line must leave for it. */
    private static final int ABANDON_W = 60;
    private static final int ABANDON_H = 12;
    /** The follow toggle, square and the same height, sitting to the left of Abandon. */
    private static final int TRACK_W = 12;
    /** Space between the two controls, so a misclick does not abandon a quest you meant to follow. */
    private static final int TRACK_GAP = 3;
    /** The copy-coordinates and add-waypoint buttons share the pin's square footprint. */
    private static final int SIDE_W = TRACK_W;

    /**
     * The entry list the current buttons were built from. Held so {@link #render} draws exactly the
     * rows {@link #init} positioned buttons against, and so {@link #tick} can notice a server push.
     */
    private List<QuestLogEntry> rendered = List.of();
    private List<ProjectLogEntry> renderedProjects = List.of();
    /**
     * The guidance the current layout was measured against.
     *
     * <p>A destination line changes an entry's height, and the Abandon and follow buttons are placed
     * from that height at {@code init} time. The server pushes guidance about once a second and quite
     * independently of the quest log, so without watching it a quest that gained or lost a destination
     * while the screen was open would leave every button below it sitting off its row.
     */
    private List<ActiveGuidance> renderedGuidance = List.of();
    private final List<ScrolledButton> scrolledButtons = new ArrayList<>();

    /** An Abandon button, remembered with its content-space y so scrolling can reposition it. */
    private record ScrolledButton(Button button, int contentY) {
    }

    public QuestLogScreen() {
        super(Component.translatable("mcaquests.screen.log.title"));
    }

    @Override
    protected int tabStripHeight() {
        return TAB_H;
    }

    /** Text wraps to an entry's interior, inside its frame. */
    private int wrapWidth() {
        return Math.max(1, contentWidth() - CARD_PAD * 2);
    }

    /** The title line stops short of the Abandon button rather than running under it. */
    private int titleWidth() {
        return Math.max(1, wrapWidth() - ABANDON_W - TRACK_W - TRACK_GAP - 6);
    }

    @Override
    protected void init() {
        super.init();
        rendered = ClientQuestData.active();
        renderedProjects = ClientProjectData.projects();
        renderedGuidance = ClientGuidanceData.all();
        scrolledButtons.clear();

        // Entries are laid out in their own space starting at 0 and clipped into the well, so a long
        // list scrolls instead of running off the bottom.
        int y = 0;
        for (QuestLogEntry entry : rendered) {
            int buttonY = y + CARD_PAD - 1;
            McaButton abandon = McaButton.create(Component.translatable("mcaquests.button.abandon"),
                            b -> confirmAbandon(entry))
                    .style(McaButton.Style.COMPACT)
                    .bounds(contentRight() - CARD_PAD - ABANDON_W, view.screenY(buttonY),
                            ABANDON_W, ABANDON_H)
                    .tooltip(Component.translatable("mcaquests.tooltip.abandon"))
                    .build();
            addRenderableWidget(abandon);
            scrolledButtons.add(new ScrolledButton(abandon, buttonY));

            // Follow this quest: the marker, the tracker's guidance line and the villager outline all
            // point at whichever one is followed. Clicking the quest already followed clears it, so
            // one button turns the marker on, moves it, and turns it off.
            boolean tracked = entry.tracked();
            IconButton track = new IconButton(
                    contentRight() - CARD_PAD - ABANDON_W - TRACK_GAP - TRACK_W, view.screenY(buttonY),
                    TRACK_W, ABANDON_H,
                    Component.translatable(tracked ? "mcaquests.button.untrack" : "mcaquests.button.track"),
                    tracked ? GuiTextures.ICON_DOT : GuiTextures.ICON_COMPASS,
                    IconButton.Look.BUTTON,
                    b -> QuestNetwork.CHANNEL.sendToServer(tracked
                            ? QuestTrackC2SPacket.none()
                            : QuestTrackC2SPacket.of(entry.villagerUuid(), entry.questId())));
            track.setTooltip(Tooltip.create(Component.translatable(
                    tracked ? "mcaquests.tooltip.untrack" : "mcaquests.tooltip.track")));
            addRenderableWidget(track);
            scrolledButtons.add(new ScrolledButton(track, buttonY));

            // Doing something with the coordinates, for the quests that have any. Real widgets rather
            // than a hand-rolled clickable rectangle: the journal's View Deeds link was drawn and
            // hit-tested by hand, which made it invisible to the keyboard and to the narrator, and that
            // is not a mistake worth making twice.
            int sideX = contentRight() - CARD_PAD - ABANDON_W - TRACK_GAP - TRACK_W;
            for (ActiveGuidance guidance : destination(entry).stream().toList()) {
                BlockPos pos = guidance.target().pos();
                sideX -= TRACK_GAP + SIDE_W;
                IconButton copy = new IconButton(sideX, view.screenY(buttonY), SIDE_W, ABANDON_H,
                        Component.translatable("mcaquests.tooltip.copy_coords"),
                        GuiTextures.ICON_DISTANCE, IconButton.Look.BUTTON,
                        b -> copyCoordinates(pos));
                copy.setTooltip(Tooltip.create(Component.translatable("mcaquests.tooltip.copy_coords")));
                addRenderableWidget(copy);
                scrolledButtons.add(new ScrolledButton(copy, buttonY));

                // Only where there is a map to add one to. A button that silently does nothing is
                // worse than no button.
                if (!MapWaypointBridge.Holder.get().isAvailable()) {
                    continue;
                }
                sideX -= TRACK_GAP + SIDE_W;
                IconButton waypoint = new IconButton(sideX, view.screenY(buttonY), SIDE_W, ABANDON_H,
                        Component.translatable("mcaquests.tooltip.add_waypoint"),
                        GuiTextures.ICON_STAR, IconButton.Look.BUTTON,
                        b -> addWaypoint(guidance));
                waypoint.setTooltip(
                        Tooltip.create(Component.translatable("mcaquests.tooltip.add_waypoint")));
                addRenderableWidget(waypoint);
                scrolledButtons.add(new ScrolledButton(waypoint, buttonY));
            }
            y += entryHeight(entry) + CARD_GAP;
        }
        view.setContentHeight(y + projectsHeight(renderedProjects));

        addBookTabs(BookTab.LOG);
        addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX() - 50, footerButtonY(), 100, 20)
                .build());
    }

    @Override
    public void tick() {
        // The server pushes a fresh log on every quest change (including the abandon we just sent) and
        // fresh guidance about once a second, but our layout and buttons are fixed at init() time. All
        // three caches swap the list reference on update, so an identity check is a cheap, sufficient
        // "something the layout depends on changed" signal.
        if (rendered != ClientQuestData.active() || renderedProjects != ClientProjectData.projects()
                || renderedGuidance != ClientGuidanceData.all()) {
            rebuildWidgets();
        }
    }

    private void confirmAbandon(QuestLogEntry entry) {
        // Abandoning destroys progress irreversibly, so make the player name the quest they mean.
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                QuestNetwork.CHANNEL.sendToServer(
                        new QuestAbandonFromLogC2SPacket(entry.villagerUuid(), entry.questId()));
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("mcaquests.screen.log.abandon_title", entry.title()),
                Component.translatable("mcaquests.screen.log.abandon_confirm")));
    }

    /**
     * Whether this player wants the Townstead context panel. Read here rather than on the server: a
     * CLIENT config spec is not loaded on a dedicated server, and a display preference should be the
     * viewer's own rather than server-wide. Used by both {@link #entryHeight} and the render pass, so
     * the two cannot disagree about how tall an entry is.
     */
    private static boolean showContext() {
        return McaQuestsConfig.CLIENT.showTownsteadQuestContext.get();
    }

    /**
     * An objective's line with its counts appended.
     *
     * <p>The server used to append these itself; sending them as numbers is what lets the bar below
     * exist, and the text has to put them back for the many players who would rather read "3/24" than
     * estimate it off a bar.
     */
    private static Component objectiveText(CardObjective objective) {
        if (objective.unavailable() || objective.required() <= 0) {
            return objective.text();
        }
        return objective.text().copy()
                .append(Component.literal("  (" + objective.current() + "/" + objective.required() + ")"));
    }

    /**
     * Where this quest is sending the player, if anywhere.
     *
     * <p>Read from the client's guidance snapshot rather than from the log entry, because the log
     * entry no longer carries one: {@code QuestLogEntry.TargetHint} named a villager, dropped the
     * dimension and had nothing to say about a quest that was about an ancient city. Both surfaces
     * that answer *where* now read the same source.
     */
    private static Optional<ActiveGuidance> destination(QuestLogEntry entry) {
        if (!McaQuestsConfig.CLIENT.showQuestLogDestination.get() || entry.suspended()) {
            return Optional.empty();
        }
        return ClientGuidanceData.forQuest(entry.questId(), entry.villagerUuid());
    }

    /** The destination as one line, wrapped like everything else on the card. */
    private static Optional<Component> destinationLine(QuestLogEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Optional.empty();
        }
        return destination(entry)
                .map(g -> GuidanceText.line(g.target(), minecraft.player, minecraft.level));
    }

    private void copyCoordinates(BlockPos pos) {
        String plain = GuidanceText.plain(pos);
        this.minecraft.keyboardHandler.setClipboard(plain);
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("mcaquests.message.coords_copied", plain), true);
        }
    }

    /**
     * Drops a waypoint the player owns.
     *
     * <p>Deliberately separate from the automatic ones the tracker publishes: those belong to the
     * quest and are taken away when it ends, which is right for a marker and wrong for a place
     * somebody has decided is worth remembering.
     */
    private void addWaypoint(ActiveGuidance guidance) {
        boolean added = MapWaypointBridge.Holder.get().pin(guidance.target().pos(),
                guidance.target().dimension(), guidance.target().label(), guidance.target().kind());
        if (added && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("mcaquests.message.waypoint_added",
                            GuidanceText.coordinates(guidance.target().pos())), true);
        }
    }

    /** A bar is worth drawing only for an objective that is counted and can actually advance. */
    private static boolean showsBar(CardObjective objective) {
        return !objective.unavailable() && objective.required() > 1;
    }

    /** Built once so the height calculation and the draw wrap identical text. */
    private static Component titleLine(QuestLogEntry entry) {
        return entry.title().copy()
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(entry.giverName().copy().withStyle(ChatFormatting.GRAY));
    }

    /**
     * Total vertical space one quest occupies, excluding the gap after it. Shared by {@link #init}
     * (button placement) and {@link #render} (drawing) so the two can never drift apart: a line that
     * wraps to three rows and is counted as one puts the Abandon button on top of the text.
     */
    private int entryHeight(QuestLogEntry entry) {
        int height = CARD_PAD * 2;
        height += CardText.height(this.font, titleLine(entry), titleWidth()) + 1;
        height += entry.chainLabel().getString().isEmpty() ? 0 : 10;
        for (CardObjective objective : entry.objectives()) {
            height += CardText.heightBulleted(this.font, BULLET, objectiveText(objective), wrapWidth());
            height += showsBar(objective) ? Panel.barHeight() + 2 : 0;
        }
        height += entry.ready() ? 10 : 0;
        for (Component line : destinationLine(entry).stream().toList()) {
            height += CardText.height(this.font, line, wrapWidth());
        }
        if (showContext()) {
            for (Component context : entry.townsteadContext()) {
                height += CardText.height(this.font, context, wrapWidth() - 6);
            }
        }
        height += entry.suspended() ? 10 : 0;
        return height;
    }

    /** Height of the whole "Village Projects" section, including its heading; 0 when there are none. */
    private int projectsHeight(List<ProjectLogEntry> projects) {
        if (projects.isEmpty()) {
            return 0;
        }
        int height = 12 + 5; // section heading + divider
        for (ProjectLogEntry project : projects) {
            height += 11 + 10 + 6; // title + scope/phase + gap
            for (ProjectObjectiveLine line : project.objectives()) {
                height += CardText.heightBulleted(this.font, BULLET, projectObjectiveLabel(line),
                        wrapWidth());
                height += Panel.barHeight() + 2;
                height += line.yourContribution() > 0 ? 9 : 0;
            }
        }
        return height;
    }

    /** Built once so the height calculation and the draw wrap identical text. */
    private static Component projectObjectiveLabel(ProjectObjectiveLine line) {
        return Component.empty().append(line.label()).append(Component.literal("  "))
                .append(Component.translatable("mcaquests.label.project.shared",
                        line.sharedCurrent(), line.required()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderPanel(graphics);

        List<QuestLogEntry> entries = rendered;
        List<ProjectLogEntry> projects = renderedProjects;
        if (entries.isEmpty() && projects.isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.status.no_active_quests"));
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Hidden rather than clipped: super.render draws widgets outside our scissor, and an
        // invisible widget is also unclickable (AbstractWidget.clicked tests visible), so a
        // scrolled-away Abandon can't be hit or sit over the footer.
        for (ScrolledButton scrolled : scrolledButtons) {
            scrolled.button().setY(view.screenY(scrolled.contentY()));
            scrolled.button().visible = view.isFullyVisible(scrolled.contentY(), ABANDON_H);
        }

        beginContentClip(graphics);
        int y = 0;
        for (QuestLogEntry entry : entries) {
            y = renderEntry(graphics, entry, y, mouseX, mouseY) + CARD_GAP;
        }
        if (!projects.isEmpty()) {
            renderProjects(graphics, projects, y);
        }
        endContentClip(graphics);
        renderScrollbar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** @return the content-space y of the entry's bottom edge */
    private int renderEntry(GuiGraphics graphics, QuestLogEntry entry, int contentY,
                            int mouseX, int mouseY) {
        int height = entryHeight(entry);
        int top = view.screenY(contentY);
        boolean hovered = hoveringInWell(contentLeft(), top, contentWidth(), height, mouseX, mouseY);
        Panel.card(graphics, contentLeft(), top, contentWidth(), height,
                entry.ready() ? Panel.CardStyle.READY
                        : hovered ? Panel.CardStyle.HOVERED : Panel.CardStyle.RESTING);

        int left = contentLeft() + CARD_PAD;
        int lineY = top + CARD_PAD;
        lineY = CardText.draw(graphics, this.font, titleLine(entry), left, lineY, titleWidth(),
                entry.ready() ? Palette.READY : Palette.TITLE) + 1;
        if (!entry.chainLabel().getString().isEmpty()) {
            Panel.icon(graphics, GuiTextures.ICON_CHAIN, left - 1, lineY - 4);
            graphics.drawString(this.font, entry.chainLabel(), left + 14, lineY, Palette.SUBTITLE, false);
            lineY += 10;
        }
        for (CardObjective objective : entry.objectives()) {
            Panel.iconScaled(graphics, ObjectiveGlyphs.of(objective.state()),
                    left + ObjectiveGlyphs.GLYPH_X, lineY + ObjectiveGlyphs.GLYPH_Y,
                    ObjectiveGlyphs.GLYPH_SCALE);
            lineY = CardText.drawBulleted(graphics, this.font, BULLET, objectiveText(objective),
                    left, lineY, wrapWidth(), ObjectiveGlyphs.colour(objective.state()));
            if (showsBar(objective)) {
                Panel.bar(graphics, left + 10, lineY, wrapWidth() - 18,
                        objective.current(), objective.required(), GuiTextures.BAR_GREEN);
                lineY += Panel.barHeight() + 2;
            }
        }
        if (entry.ready()) {
            Panel.icon(graphics, GuiTextures.ICON_OBJ_DONE, left + 3, lineY - 4);
            graphics.drawString(this.font, Component.translatable("mcaquests.status.ready"),
                    left + 20, lineY, Palette.READY, false);
            lineY += 10;
        }
        for (Component line : destinationLine(entry).stream().toList()) {
            // Must stay in lockstep with entryHeight, or every button below drifts off its row.
            // Palette.DIRECTION, not Palette.Hud.DIRECTION: this is the light container panel, and the
            // HUD's light-on-dark blue is the one colour in the mod that would be invisible on it.
            lineY = CardText.draw(graphics, this.font, line, left, lineY, wrapWidth(),
                    Palette.DIRECTION);
        }
        for (Component context : showContext() ? entry.townsteadContext() : List.<Component>of()) {
            // Dimmer than the objectives: this is background about the villager, not a task.
            lineY = CardText.draw(graphics, this.font, context, left + 6, lineY,
                    wrapWidth() - 6, Palette.CONTEXT);
        }
        if (entry.suspended()) {
            // Amber, not red: the quest is on hold, not lost. Progress and frozen baselines are
            // intact and it resumes untouched once whatever it reads is back. Must stay in
            // lockstep with entryHeight, or the Abandon buttons drift off their rows.
            Panel.icon(graphics, GuiTextures.ICON_OBJ_SUSPENDED, left + 3, lineY - 4);
            graphics.drawString(this.font, Component.translatable("mcaquests.status.suspended"),
                    left + 20, lineY, Palette.WARNING, false);
        }
        return contentY + height;
    }

    private void renderProjects(GuiGraphics graphics, List<ProjectLogEntry> projects, int contentY) {
        int left = contentLeft() + CARD_PAD;
        int y = view.screenY(contentY);
        Panel.divider(graphics, contentLeft(), y, contentWidth());
        y += 5;
        Panel.icon(graphics, GuiTextures.ICON_PROJECT, contentLeft(), y - 4);
        graphics.drawString(this.font, Component.translatable("mcaquests.screen.log.projects"),
                contentLeft() + 18, y, Palette.HEADING, false);
        y += 12;
        for (ProjectLogEntry project : projects) {
            graphics.drawString(this.font, project.title().copy()
                            .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                            .append(project.sponsorLabel().copy().withStyle(ChatFormatting.GRAY)),
                    left, y, Palette.TITLE, false);
            y += 11;
            graphics.drawString(this.font, Component.empty().append(project.scopeLabel())
                    .append(Component.literal("  ")).append(project.phaseLabel()), left + 2, y,
                    Palette.SUBTITLE, false);
            y += 10;
            for (ProjectObjectiveLine line : project.objectives()) {
                boolean done = line.sharedCurrent() >= line.required();
                Panel.iconScaled(graphics,
                        done ? GuiTextures.ICON_OBJ_DONE : GuiTextures.ICON_OBJ_PENDING,
                        left + ObjectiveGlyphs.GLYPH_X, y + ObjectiveGlyphs.GLYPH_Y,
                        ObjectiveGlyphs.GLYPH_SCALE);
                y = CardText.drawBulleted(graphics, this.font, BULLET, projectObjectiveLabel(line),
                        left, y, wrapWidth(), done ? Palette.READY : Palette.OBJECTIVE);
                // Everybody's progress in green, your own share over it in blue -- the numbers were
                // already on the line, but a bar is what makes "nearly there" legible at a glance.
                Panel.contributionBar(graphics, left + 8, y, wrapWidth() - 16,
                        line.sharedCurrent(), line.yourContribution(), line.required());
                y += Panel.barHeight() + 2;
                if (line.yourContribution() > 0) {
                    graphics.drawString(this.font,
                            Component.translatable("mcaquests.label.project.you", line.yourContribution()),
                            left + 10, y, Palette.CONTRIBUTION, false);
                    y += 9;
                }
            }
            y += 6;
        }
    }
}
