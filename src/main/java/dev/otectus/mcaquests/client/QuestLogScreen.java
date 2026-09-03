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
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import dev.otectus.mcaquests.client.map.ClientMapWaypointRegistry;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * The layout the current buttons were built for, as {@link #layoutSignature} words it.
     *
     * <p>A destination line changes an entry's height, and the Abandon and follow buttons are placed
     * from that height at {@code init} time. The server pushes guidance about once a second and quite
     * independently of the quest log, so without watching it a quest that gained or lost a destination
     * while the screen was open would leave every button below it sitting off its row.
     */
    private List<String> renderedLayout = List.of();

    /**
     * Every row control, addressed by the quest it belongs to and what it does.
     *
     * <p>A rebuild replaces every widget on the screen, so focus cannot be held as a reference: it has
     * to be named before the rebuild and looked up again after it.
     */
    private final Map<ControlKey, AbstractWidget> controls = new LinkedHashMap<>();

    /** The controls one quest's row can carry. */
    private enum Control {
        ABANDON, TRACK, COPY, WAYPOINT
    }

    /** One control on one row, stable across a rebuild. */
    private record ControlKey(UUID villagerUuid, ResourceLocation questId, Control control) {
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

    /**
     * The title line stops short of this row's controls rather than running under them.
     *
     * <p>Abandon and the follow pin are on every row; the copy-coordinates and add-waypoint buttons
     * are only on a row that has somewhere to go, and they were not counted — so exactly the quests
     * with a destination were the ones whose title ran under two more buttons.
     */
    private int titleWidth(QuestLogEntry entry) {
        int side = sideButtonCount(entry) * (SIDE_W + TRACK_GAP);
        return Math.max(1, wrapWidth() - ABANDON_W - TRACK_W - TRACK_GAP - side - 6);
    }

    /** How many optional coordinate buttons this row carries: none, copy, or copy and waypoint. */
    private static int sideButtonCount(QuestLogEntry entry) {
        if (destination(entry).isEmpty()) {
            return 0;
        }
        return ClientMapWaypointRegistry.bestPinSupport() != PinSupport.NONE ? 2 : 1;
    }

    @Override
    protected void init() {
        super.init();
        rendered = ClientQuestData.active();
        renderedProjects = ClientProjectData.projects();
        renderedLayout = layoutSignature();
        controls.clear();

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
            addControl(entry, Control.ABANDON, abandon, buttonY);

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
                    b -> PacketDistributor.sendToServer(tracked
                            ? QuestTrackC2SPacket.none()
                            : QuestTrackC2SPacket.of(entry.villagerUuid(), entry.questId())));
            track.setTooltip(Tooltip.create(Component.translatable(
                    tracked ? "mcaquests.tooltip.untrack" : "mcaquests.tooltip.track")));
            addControl(entry, Control.TRACK, track, buttonY);

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
                addControl(entry, Control.COPY, copy, buttonY);

                // Only where there is a map to add one to. A button that silently does nothing is
                // worse than no button.
                if (ClientMapWaypointRegistry.bestPinSupport() == PinSupport.NONE) {
                    continue;
                }
                sideX -= TRACK_GAP + SIDE_W;
                // Xaero's third-party store is rebuilt on every world load, so a pin dropped there is
                // honestly a pin for this session. The button says which one it is offering rather
                // than promising something that quietly disappears at the next login.
                Component pinTooltip = Component.translatable(
                        ClientMapWaypointRegistry.bestPinSupport() == PinSupport.PERSISTENT
                                ? "mcaquests.tooltip.add_waypoint"
                                : "mcaquests.tooltip.add_session_waypoint");
                IconButton waypoint = new IconButton(sideX, view.screenY(buttonY), SIDE_W, ABANDON_H,
                        pinTooltip, GuiTextures.ICON_STAR, IconButton.Look.BUTTON,
                        b -> addWaypoint(guidance));
                waypoint.setTooltip(Tooltip.create(pinTooltip));
                addControl(entry, Control.WAYPOINT, waypoint, buttonY);
            }
            y += entryHeight(entry) + CARD_GAP;
        }
        view.setContentHeight(y + projectsHeight(renderedProjects));

        addBookTabs(BookTab.LOG);
        addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX() - 50, footerButtonY(), 100, 20)
                .build());
    }

    /** Adds a row control and remembers what it is, so a rebuild can give focus back to it. */
    private void addControl(QuestLogEntry entry, Control control, AbstractWidget widget, int contentY) {
        addScrolledWidget(widget, contentY, ABANDON_H);
        controls.put(new ControlKey(entry.villagerUuid(), entry.questId(), control), widget);
    }

    @Override
    public void tick() {
        // The server pushes a fresh log on every quest change (including the abandon we just sent), and
        // both quest caches swap their list reference on update, so an identity check is a cheap,
        // sufficient "the list changed" signal. Guidance is compared by what the layout actually
        // depends on instead -- see layoutSignature.
        List<String> layout = layoutSignature();
        if (rendered != ClientQuestData.active() || renderedProjects != ClientProjectData.projects()
                || !renderedLayout.equals(layout)) {
            ControlKey focused = focusedControl();
            rebuildWidgets();
            restoreFocus(focused);
        }
    }

    /**
     * What the layout depends on outside the quest list: whether each quest has a destination — which
     * adds a line and two buttons — and whether there is a map to pin one to.
     *
     * <p>The guidance snapshot itself changes about once a second, because the distance in it is
     * different every time the player takes a step. Rebuilding on that took keyboard focus away from
     * whatever the player had just tabbed to, at that same rate.
     */
    private List<String> layoutSignature() {
        List<String> signature = new ArrayList<>(rendered.size() + 1);
        signature.add("map=" + ClientMapWaypointRegistry.bestPinSupport());
        for (QuestLogEntry entry : rendered) {
            signature.add(entry.villagerUuid() + "/" + entry.questId() + "="
                    + destination(entry).isPresent());
        }
        return signature;
    }

    /** Which row control has focus right now, or null when focus is elsewhere. */
    @Nullable
    private ControlKey focusedControl() {
        GuiEventListener focused = getFocused();
        for (Map.Entry<ControlKey, AbstractWidget> control : controls.entrySet()) {
            if (control.getValue() == focused) {
                return control.getKey();
            }
        }
        return null;
    }

    /** Hands focus back to the same control on the same quest, if it still exists. */
    private void restoreFocus(@Nullable ControlKey key) {
        if (key == null) {
            return;
        }
        AbstractWidget widget = controls.get(key);
        if (widget != null) {
            setFocused(widget);
        }
    }

    private void confirmAbandon(QuestLogEntry entry) {
        // Abandoning destroys progress irreversibly, so make the player name the quest they mean.
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(
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
     *
     * <p>The pin goes only to the backends that offer the <em>best</em> durability installed: with
     * JourneyMap present it is saved, and sending it to Xaero as well would put a second marker on the
     * same spot that disappears when the player logs out.
     */
    private void addWaypoint(ActiveGuidance guidance) {
        PinSupport wanted = ClientMapWaypointRegistry.bestPinSupport();
        if (wanted == PinSupport.NONE) {
            return;
        }
        WaypointSpec spec = new WaypointSpec(
                guidance.questId() + "/" + guidance.villagerUuid() + "/pin",
                guidance.target().pos(), guidance.target().dimension(),
                guidance.target().label().getString(), guidance.target().kind(),
                WaypointSpec.Ownership.PIN);
        boolean added = false;
        for (MapWaypointBackend backend : ClientMapWaypointRegistry.backends()) {
            if (!backend.isUsable() || backend.capabilities().pins() != wanted) {
                continue;
            }
            added |= backend.pin(spec) == MapMutationResult.APPLIED;
        }
        if (this.minecraft.player == null) {
            return;
        }
        // A map that declined the pin used to say nothing at all, which reads exactly like a button
        // that does nothing. Every outcome is now reported, and the success says how long it lasts.
        Component message = added
                ? Component.translatable(wanted == PinSupport.PERSISTENT
                        ? "mcaquests.message.waypoint_added"
                        : "mcaquests.message.session_waypoint_added",
                        GuidanceText.coordinates(guidance.target().pos()))
                : Component.translatable("mcaquests.message.waypoint_failed");
        this.minecraft.player.displayClientMessage(message, true);
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
        height += CardText.height(this.font, titleLine(entry), titleWidth(entry)) + 1;
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
            // title + scope/phase + gap
            height += CardText.height(this.font, projectTitleLine(project), wrapWidth()) + 1 + 10 + 6;
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
    private static Component projectTitleLine(ProjectLogEntry project) {
        return project.title().copy()
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(project.sponsorLabel().copy().withStyle(ChatFormatting.GRAY));
    }

    /** Built once so the height calculation and the draw wrap identical text. */
    private static Component projectObjectiveLabel(ProjectObjectiveLine line) {
        return Component.empty().append(line.label()).append(Component.literal("  "))
                .append(Component.translatable("mcaquests.label.project.shared",
                        line.sharedCurrent(), line.required()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render draws the menu background -- the full-screen blur pass and the tint -- itself
        // since 1.20.2, and then the widgets, so it goes first and this screen's own chrome and
        // content go on top of it. The 1.20.1 order (draw, then super.render) left everything drawn
        // here to be blurred and dimmed a second time by the background pass.
        applyScrolledVisibility();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderPanel(graphics);

        List<QuestLogEntry> entries = rendered;
        List<ProjectLogEntry> projects = renderedProjects;
        if (entries.isEmpty() && projects.isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.status.no_active_quests"));
            return;
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
        lineY = CardText.draw(graphics, this.font, titleLine(entry), left, lineY, titleWidth(entry),
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
            y = CardText.draw(graphics, this.font, projectTitleLine(project), left, y, wrapWidth(),
                    Palette.TITLE) + 1;
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
