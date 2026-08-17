package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.QuestAbandonFromLogC2SPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keybind-accessible list of the player's active MCA quests (spec section 21), with a per-quest
 * Abandon button.
 *
 * <p>Abandoning here needs no villager, unlike the button in the villager menu — that is the point:
 * it is the only way to drop a quest whose giver is dead, unloaded, or in another dimension.
 */
public class QuestLogScreen extends Screen {

    /** Top of the scrolled region — just below the title. */
    private static final int VIEWPORT_TOP = 32;

    /**
     * The entry list the current buttons were built from. Held so {@link #render} draws exactly the
     * rows {@link #init} positioned buttons against, and so {@link #tick} can notice a server push.
     */
    private List<QuestLogEntry> rendered = List.of();
    private List<ProjectLogEntry> renderedProjects = List.of();
    private final List<ScrolledButton> scrolledButtons = new ArrayList<>();
    private final ScrollView view = new ScrollView();

    /** An Abandon button, remembered with its content-space y so scrolling can reposition it. */
    private record ScrolledButton(Button button, int contentY) {
    }

    public QuestLogScreen() {
        super(Component.translatable("mcaquests.screen.log.title"));
    }

    @Override
    protected void init() {
        rendered = ClientQuestData.active();
        renderedProjects = ClientProjectData.projects();
        scrolledButtons.clear();
        int centerX = this.width / 2;

        // Entries are laid out in their own space starting at 0 and clipped between the title and the
        // footer buttons, so a long list scrolls instead of running off the bottom.
        view.setViewport(VIEWPORT_TOP, Math.max(VIEWPORT_TOP, this.height - 42));

        int y = 0;
        for (QuestLogEntry entry : rendered) {
            Button abandon = Button.builder(Component.translatable("mcaquests.button.abandon"),
                            b -> confirmAbandon(entry))
                    .bounds(centerX + 96, view.screenY(y - 2), 60, 12)
                    .build();
            addRenderableWidget(abandon);
            scrolledButtons.add(new ScrolledButton(abandon, y - 2));
            y += entryHeight(entry);
        }
        view.setContentHeight(y + projectsHeight(renderedProjects));
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.journal"),
                        b -> this.minecraft.setScreen(new JournalScreen()))
                .bounds(centerX - 154, this.height - 36, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 36, 100, 20)
                .build());
    }

    @Override
    public void tick() {
        // The server pushes a fresh log on every quest change (including the abandon we just sent), but
        // our layout and buttons are fixed at init() time. Both caches swap the list reference on
        // update, so an identity check is a cheap, sufficient "the log changed" signal.
        if (rendered != ClientQuestData.active() || renderedProjects != ClientProjectData.projects()) {
            rebuildWidgets();
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
     * Total vertical space one quest occupies. Shared by {@link #init} (button placement) and
     * {@link #render} (drawing) so the two can never drift apart.
     */
    private static int entryHeight(QuestLogEntry entry) {
        return 11
                + (entry.chainLabel().getString().isEmpty() ? 0 : 10)
                + entry.objectives().size() * 10
                + (entry.ready() ? 10 : 0)
                + 6;
    }

    /** Height of the whole "Village Projects" section, including its heading; 0 when there are none. */
    private static int projectsHeight(List<ProjectLogEntry> projects) {
        if (projects.isEmpty()) {
            return 0;
        }
        int height = 12; // section heading
        for (ProjectLogEntry project : projects) {
            height += 11 + 10 + 6; // title + scope/phase + gap
            for (ProjectObjectiveLine line : project.objectives()) {
                height += 10 + (line.yourContribution() > 0 ? 9 : 0);
            }
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        view.scrollBy(-(int) (delta * 12));
        return true;
    }

    /** Thin track + thumb, drawn right of the entries, only when there is something to scroll. */
    private void renderScrollbar(GuiGraphics graphics, int x) {
        if (!view.overflows()) {
            return;
        }
        graphics.fill(x, view.top(), x + 3, view.bottom(), 0x40FFFFFF);
        graphics.fill(x, view.thumbTop(), x + 3, view.thumbTop() + view.thumbHeight(), 0xC0FFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        List<QuestLogEntry> entries = rendered;
        List<ProjectLogEntry> projects = renderedProjects;

        // Hidden rather than clipped: super.render draws widgets outside our scissor, and an
        // invisible widget is also unclickable (AbstractWidget.clicked tests visible), so a
        // scrolled-away Abandon can't be hit or sit over the footer. This has to run before
        // super.render, which is what actually draws the buttons.
        for (ScrolledButton scrolled : scrolledButtons) {
            scrolled.button().setY(view.screenY(scrolled.contentY()));
            scrolled.button().visible = view.isFullyVisible(scrolled.contentY(), 12);
        }

        // Screen.render draws the menu background (blur pass + tint) itself since 1.20.2 and then
        // the widgets, so it goes first and our content after it. Drawing content before it — the
        // 1.20.1 order — leaves the text to be blurred and dimmed by the background pass.
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, getTitle(), centerX, 16, 0xFFFFFF);
        if (entries.isEmpty() && projects.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcaquests.status.no_active_quests"), centerX, this.height / 2, 0xA0A0A0);
        } else {
            int left = centerX - 150;
            graphics.enableScissor(left, view.top(), centerX + 160, view.bottom());
            int y = view.screenY(0);
            for (QuestLogEntry entry : entries) {
                int lineY = y;
                graphics.drawString(this.font, entry.title().copy()
                                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                .append(entry.giverName().copy().withStyle(ChatFormatting.GRAY)),
                        left, lineY, entry.ready() ? 0x5CFF5C : 0xFFE08A);
                lineY += 11;
                if (!entry.chainLabel().getString().isEmpty()) {
                    graphics.drawString(this.font, entry.chainLabel(), left + 2, lineY, 0x9A9A9A);
                    lineY += 10;
                }
                for (Component objective : entry.objectives()) {
                    graphics.drawString(this.font, Component.literal("  - ").append(objective), left, lineY, 0xBFBFBF);
                    lineY += 10;
                }
                if (entry.ready()) {
                    graphics.drawString(this.font,
                            Component.translatable("mcaquests.status.ready"), left + 6, lineY, 0x5CFF5C);
                }
                y += entryHeight(entry);
            }
            if (!projects.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("mcaquests.screen.log.projects"), left, y, 0x5CC8FF);
                y += 12;
                for (ProjectLogEntry project : projects) {
                    graphics.drawString(this.font, project.title().copy()
                                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                    .append(project.sponsorLabel().copy().withStyle(ChatFormatting.GRAY)),
                            left, y, 0xFFE08A);
                    y += 11;
                    graphics.drawString(this.font, Component.empty().append(project.scopeLabel())
                            .append(Component.literal("  ")).append(project.phaseLabel()), left + 2, y, 0x9A9A9A);
                    y += 10;
                    for (ProjectObjectiveLine line : project.objectives()) {
                        Component text = Component.literal("  - ").append(line.label())
                                .append(Component.literal("  "))
                                .append(Component.translatable("mcaquests.label.project.shared",
                                        line.sharedCurrent(), line.required()));
                        graphics.drawString(this.font, text, left, y, 0xBFBFBF);
                        y += 10;
                        if (line.yourContribution() > 0) {
                            graphics.drawString(this.font,
                                    Component.translatable("mcaquests.label.project.you", line.yourContribution()),
                                    left + 10, y, 0x6FA8DC);
                            y += 9;
                        }
                    }
                    y += 6;
                }
            }
            graphics.disableScissor();
            renderScrollbar(graphics, centerX + 162);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
