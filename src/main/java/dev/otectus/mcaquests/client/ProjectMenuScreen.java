package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.ProjectContributeC2SPacket;
import dev.otectus.mcaquests.network.ProjectMenuStatus;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.QuestNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Community-project screen (spec 0.4.0). Reached from the villager menu's "View Project" button. Shows
 * the richer project-only display — scope, sponsor/village, phase, shared progress bars, per-player
 * contributions, and rewards — with a Contribute button per active project. Individual quest cards stay
 * simple on {@link QuestMenuScreen}.
 */
public class ProjectMenuScreen extends Screen {

    private static final int CARD_WIDTH = 280;
    private static final int BAR_HEIGHT = 2;
    /** Top of the card viewport — just below the title. */
    private static final int VIEWPORT_TOP = 26;

    private final UUID villagerUuid;
    private final List<ProjectCard> cards;
    /** Card tops in content space (0 = first card), turned into screen y through {@link #view}. */
    private final List<Integer> cardTops = new ArrayList<>();
    private final List<ScrolledButton> scrolledButtons = new ArrayList<>();
    private final ScrollView view = new ScrollView();

    /** A Contribute button, remembered with its content-space y so scrolling can reposition it. */
    private record ScrolledButton(Button button, int contentY) {
    }

    public ProjectMenuScreen(UUID villagerUuid, List<ProjectCard> cards) {
        super(Component.translatable("mcaquests.screen.projects.title"));
        this.villagerUuid = villagerUuid;
        this.cards = cards;
    }

    public UUID villagerUuid() {
        return villagerUuid;
    }

    private int wrapWidth() {
        return Math.min(CARD_WIDTH, this.width - 40);
    }

    @Override
    protected void init() {
        cardTops.clear();
        scrolledButtons.clear();
        int centerX = this.width / 2;

        // Cards live in their own space starting at 0, clipped between the title and the Back button,
        // so a village with several projects doesn't run off the bottom (see QuestMenuScreen).
        view.setViewport(VIEWPORT_TOP, Math.max(VIEWPORT_TOP, this.height - 32));

        int y = 0;
        for (ProjectCard card : cards) {
            cardTops.add(y);
            int height = cardHeight(card);
            if (card.status() != ProjectMenuStatus.COMPLETE) {
                int contentY = y + height - 22;
                Button contribute = Button.builder(Component.translatable("mcaquests.button.project.contribute"),
                                b -> contribute(card.projectId()))
                        .bounds(centerX - 60, view.screenY(contentY), 120, 20)
                        .build();
                addRenderableWidget(contribute);
                scrolledButtons.add(new ScrolledButton(contribute, contentY));
            }
            y += height + 4;
        }
        view.setContentHeight(y);

        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 26, 100, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        view.scrollBy(-(int) (delta * 12));
        return true;
    }

    private void contribute(ResourceLocation projectId) {
        QuestNetwork.CHANNEL.sendToServer(new ProjectContributeC2SPacket(villagerUuid, projectId));
    }

    private int cardHeight(ProjectCard card) {
        int height = 12; // title
        height += 10; // scope + sponsor
        height += 10; // phase
        height += this.font.split(card.dialogue(), wrapWidth()).size() * 10;
        for (ProjectObjectiveLine line : card.objectives()) {
            height += CardText.heightBulleted(this.font, BULLET, objectiveLabel(line), wrapWidth());
            height += BAR_HEIGHT;
            if (line.yourContribution() > 0) {
                height += 9;
            }
        }
        height += CardText.height(this.font, joinRewards(card.rewards()), wrapWidth()) + 2;
        height += 26; // button + padding
        return height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int left = centerX - wrapWidth() / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 12, 0xFFFFFF);
        if (cards.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("mcaquests.status.no_quests"),
                    centerX, this.height / 2, 0xFFFFFF);
        } else {
            // Hidden rather than clipped: super.render draws widgets outside our scissor, and an
            // invisible widget is also unclickable (AbstractWidget.clicked tests visible).
            for (ScrolledButton scrolled : scrolledButtons) {
                scrolled.button().setY(view.screenY(scrolled.contentY()));
                scrolled.button().visible = view.isFullyVisible(scrolled.contentY(), 20);
            }
            int right = left + wrapWidth();
            graphics.enableScissor(left, view.top(), right, view.bottom());
            for (int i = 0; i < cards.size(); i++) {
                renderCard(graphics, cards.get(i), left, view.screenY(cardTops.get(i)));
            }
            graphics.disableScissor();
            renderScrollbar(graphics, right + 4);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Thin track + thumb, drawn just right of the cards, only when there is something to scroll. */
    private void renderScrollbar(GuiGraphics graphics, int x) {
        if (!view.overflows()) {
            return;
        }
        graphics.fill(x, view.top(), x + 3, view.bottom(), 0x40FFFFFF);
        graphics.fill(x, view.thumbTop(), x + 3, view.thumbTop() + view.thumbHeight(), 0xC0FFFFFF);
    }

    private void renderCard(GuiGraphics graphics, ProjectCard card, int left, int top) {
        int y = top;
        boolean done = card.status() == ProjectMenuStatus.COMPLETE;
        graphics.drawString(this.font, card.title(), left, y, done ? 0x5CFF5C : 0xFFE08A);
        y += 12;
        graphics.drawString(this.font, Component.empty().append(card.scopeLabel())
                .append(Component.literal("  ")).append(card.sponsorLabel()), left, y, 0x9A9A9A);
        y += 10;
        graphics.drawString(this.font, card.phaseLabel(), left, y, 0x9A9A9A);
        y += 10;
        for (FormattedCharSequence line : this.font.split(card.dialogue(), wrapWidth())) {
            graphics.drawString(this.font, line, left, y, 0xCFCFCF);
            y += 10;
        }
        for (ProjectObjectiveLine line : card.objectives()) {
            y = CardText.drawBulleted(graphics, this.font, BULLET, objectiveLabel(line), left, y,
                    wrapWidth(), 0xBFBFBF);
            renderBar(graphics, left, y, line.sharedCurrent(), line.required());
            y += BAR_HEIGHT;
            if (line.yourContribution() > 0) {
                graphics.drawString(this.font,
                        Component.translatable("mcaquests.label.project.you", line.yourContribution()), left + 8, y, 0x6FA8DC);
                y += 9;
            }
        }
        CardText.draw(graphics, this.font, joinRewards(card.rewards()), left, y, wrapWidth(), 0x88CC88);
    }

    /** Built once so the height calculation and the draw wrap identical text. */
    private static Component objectiveLabel(ProjectObjectiveLine line) {
        return Component.empty().append(line.label()).append(Component.literal("  "))
                .append(Component.translatable("mcaquests.label.project.shared",
                        line.sharedCurrent(), line.required()));
    }

    /** The objective marker, shared by the height calculation and the draw so the indents match. */
    private static final String BULLET = " - ";

    private void renderBar(GuiGraphics graphics, int left, int y, int current, int required) {
        int width = wrapWidth() - 8;
        graphics.fill(left + 4, y, left + 4 + width, y + BAR_HEIGHT, 0xFF333333);
        int filled = required <= 0 ? width : Mth.clamp((int) ((long) width * current / required), 0, width);
        graphics.fill(left + 4, y, left + 4 + filled, y + BAR_HEIGHT, 0xFF5CFF5C);
    }

    private static Component joinRewards(List<Component> rewards) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < rewards.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(", "));
            }
            joined.append(rewards.get(i));
        }
        return joined;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
