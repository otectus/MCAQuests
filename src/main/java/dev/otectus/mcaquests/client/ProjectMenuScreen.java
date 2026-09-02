package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.McaButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.ProjectContributeC2SPacket;
import dev.otectus.mcaquests.network.ProjectMenuStatus;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.QuestNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Community-project screen (spec 0.4.0). Reached from the villager menu's "View Project" button. Shows
 * the richer project-only display — scope, sponsor/village, phase, shared progress bars, per-player
 * contributions, and rewards — with a Contribute button per active project. Individual quest cards stay
 * simple on {@link QuestMenuScreen}.
 *
 * <p>The progress bars here were the only ones in the mod, and were drawn as two flat {@code fill()}
 * rectangles two pixels tall. They are now textured, and they show <em>two</em> quantities: the
 * village's shared progress, and your own share of it over the top. The numbers were always on the
 * line above; the bar is what makes "nearly there" legible without reading them.
 */
public class ProjectMenuScreen extends McaQuestsScreen {

    /** The objective marker, shared by the height calculation and the draw so the indents match. */
    /** The gutter every objective line is indented by, with a done/pending glyph drawn into it. */
    private static final String BULLET = ObjectiveGlyphs.GUTTER;
    /** Inset from a card's frame to its text. Matches the card sprite's nine-slice border. */
    private static final int CARD_PAD = 5;
    private static final int CARD_GAP = 4;
    /** The Contribute strip: a 20px button plus the padding above it. */
    private static final int BUTTON_STRIP = 24;

    private final UUID villagerUuid;
    private List<ProjectCard> cards;
    /** Card tops in content space (0 = first card), turned into screen y through {@link #view}. */
    private final List<Integer> cardTops = new ArrayList<>();
    private final List<ScrolledButton> scrolledButtons = new ArrayList<>();

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

    /**
     * Takes a fresh set of cards without reopening the screen.
     *
     * <p>The server pushes the whole menu again after every contribution, and the handler used to
     * answer that with a full {@code setScreen} — which threw the player's scroll position away mid
     * read, every time they gave something. Rebuilding the widgets in place keeps it: {@code
     * ScrollView} only ever clamps its offset, so a list that has grown or shrunk stays where the
     * player left it.
     */
    void refresh(List<ProjectCard> updated) {
        this.cards = updated;
        rebuildWidgets();
    }

    private int wrapWidth() {
        return Math.max(1, contentWidth() - CARD_PAD * 2);
    }

    @Override
    protected void init() {
        super.init();
        cardTops.clear();
        scrolledButtons.clear();

        // Cards live in their own space starting at 0 and are clipped into the well, so a village with
        // several projects doesn't run off the bottom (see QuestMenuScreen).
        int y = 0;
        for (ProjectCard card : cards) {
            cardTops.add(y);
            int height = cardHeight(card);
            if (card.status() != ProjectMenuStatus.COMPLETE) {
                int contentY = y + height - CARD_PAD - 20;
                McaButton contribute = McaButton.create(
                                Component.translatable("mcaquests.button.project.contribute"),
                                b -> contribute(card.projectId()))
                        .bounds(centerX() - 60, view.screenY(contentY), 120, 20)
                        .tooltip(Component.translatable("mcaquests.tooltip.project.contribute"))
                        .build();
                addRenderableWidget(contribute);
                scrolledButtons.add(new ScrolledButton(contribute, contentY));
            }
            y += height + CARD_GAP;
        }
        view.setContentHeight(Math.max(0, y - CARD_GAP));

        addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX() - 50, footerButtonY(), 100, 20)
                .build());
    }

    private void contribute(ResourceLocation projectId) {
        QuestNetwork.CHANNEL.sendToServer(new ProjectContributeC2SPacket(villagerUuid, projectId));
    }

    /** Must agree exactly with {@link #renderCard}, or the Contribute buttons drift off their cards. */
    private int cardHeight(ProjectCard card) {
        int height = CARD_PAD * 2;
        height += 12; // title
        height += 10; // scope + sponsor
        height += 10; // phase
        height += this.font.split(card.dialogue(), wrapWidth()).size() * 10;
        for (ProjectObjectiveLine line : card.objectives()) {
            height += CardText.heightBulleted(this.font, BULLET, objectiveLabel(line), wrapWidth());
            height += Panel.barHeight() + 2;
            if (line.yourContribution() > 0) {
                height += 9;
            }
        }
        height += CardText.height(this.font, joinRewards(card.rewards()), wrapWidth()) + 2;
        if (card.status() != ProjectMenuStatus.COMPLETE) {
            height += BUTTON_STRIP;
        }
        return height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderPanel(graphics);

        if (cards.isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.status.no_quests"));
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Hidden rather than clipped: super.render draws widgets outside our scissor, and an
        // invisible widget is also unclickable (AbstractWidget.clicked tests visible).
        for (ScrolledButton scrolled : scrolledButtons) {
            scrolled.button().setY(view.screenY(scrolled.contentY()));
            scrolled.button().visible = view.isFullyVisible(scrolled.contentY(), 20);
        }
        beginContentClip(graphics);
        for (int i = 0; i < cards.size(); i++) {
            renderCard(graphics, cards.get(i), view.screenY(cardTops.get(i)), mouseX, mouseY);
        }
        endContentClip(graphics);
        renderScrollbar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, ProjectCard card, int top, int mouseX, int mouseY) {
        boolean done = card.status() == ProjectMenuStatus.COMPLETE;
        int width = contentWidth();
        int height = cardHeight(card);
        boolean hovered = hoveringInWell(contentLeft(), top, width, height, mouseX, mouseY);
        Panel.card(graphics, contentLeft(), top, width, height,
                done ? Panel.CardStyle.READY
                        : hovered ? Panel.CardStyle.HOVERED : Panel.CardStyle.RESTING);

        int left = contentLeft() + CARD_PAD;
        int y = top + CARD_PAD;
        Panel.icon(graphics, done ? GuiTextures.ICON_OBJ_DONE : GuiTextures.ICON_PROJECT, left - 1, y - 4);
        graphics.drawString(this.font, card.title(), left + 16, y, done ? Palette.READY : Palette.TITLE, false);
        y += 12;
        graphics.drawString(this.font, Component.empty().append(card.scopeLabel())
                .append(Component.literal("  ")).append(card.sponsorLabel()), left, y, Palette.SUBTITLE, false);
        y += 10;
        graphics.drawString(this.font, card.phaseLabel(), left, y, Palette.SUBTITLE, false);
        y += 10;
        for (FormattedCharSequence line : this.font.split(card.dialogue(), wrapWidth())) {
            graphics.drawString(this.font, line, left, y, Palette.DIALOGUE, false);
            y += 10;
        }
        for (ProjectObjectiveLine line : card.objectives()) {
            Panel.iconScaled(graphics,
                    line.sharedCurrent() >= line.required()
                            ? GuiTextures.ICON_OBJ_DONE : GuiTextures.ICON_OBJ_PENDING,
                    left + ObjectiveGlyphs.GLYPH_X, y + ObjectiveGlyphs.GLYPH_Y,
                    ObjectiveGlyphs.GLYPH_SCALE);
            y = CardText.drawBulleted(graphics, this.font, BULLET, objectiveLabel(line), left, y,
                    wrapWidth(), Palette.OBJECTIVE);
            Panel.contributionBar(graphics, left + 4, y, wrapWidth() - 8,
                    line.sharedCurrent(), line.yourContribution(), line.required());
            y += Panel.barHeight() + 2;
            if (line.yourContribution() > 0) {
                graphics.drawString(this.font,
                        Component.translatable("mcaquests.label.project.you", line.yourContribution()),
                        left + 8, y, Palette.CONTRIBUTION, false);
                y += 9;
            }
        }
        CardText.draw(graphics, this.font, joinRewards(card.rewards()), left, y, wrapWidth(),
                Palette.REWARD);
    }

    /** Built once so the height calculation and the draw wrap identical text. */
    private static Component objectiveLabel(ProjectObjectiveLine line) {
        return Component.empty().append(line.label()).append(Component.literal("  "))
                .append(Component.translatable("mcaquests.label.project.shared",
                        line.sharedCurrent(), line.required()));
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
}
