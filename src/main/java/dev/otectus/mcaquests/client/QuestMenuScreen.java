package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.QuestAbandonC2SPacket;
import dev.otectus.mcaquests.network.QuestCard;
import dev.otectus.mcaquests.network.QuestDecisionC2SPacket;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.QuestTurnInC2SPacket;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation / offer screen (spec sections 8, 9, 21). Renders the villager header plus the quest
 * cards: up to {@code offersPerVillager} offers (each Accept/Decline), or the single active quest
 * (Complete/Abandon). Every button just sends a C2S packet; the server replies with fresh data,
 * reopening this screen in the new state.
 */
public class QuestMenuScreen extends Screen {

    private static final int CARD_WIDTH = 280;
    /** Top of the card viewport — just below the name / profession / hearts header. */
    private static final int VIEWPORT_TOP = 48;

    private final QuestMenuDataS2CPacket data;
    /** Card tops in content space (0 = first card), turned into screen y through {@link #view}. */
    private final List<Integer> cardTops = new ArrayList<>();
    private final List<ScrolledButton> scrolledButtons = new ArrayList<>();
    private final ScrollView view = new ScrollView();

    /** A card button, remembered with its content-space y so scrolling can reposition it. */
    private record ScrolledButton(Button button, int contentY) {
    }

    public QuestMenuScreen(QuestMenuDataS2CPacket data) {
        super(Component.translatable("mcaquests.screen.quests.title"));
        this.data = data;
    }

    /** Dialogue wraps to the card, but never wider than the screen allows. */
    private int wrapWidth() {
        return Math.min(CARD_WIDTH, this.width - 40);
    }

    @Override
    protected void init() {
        cardTops.clear();
        scrolledButtons.clear();
        int centerX = this.width / 2;

        // Cards are laid out in their own space starting at 0 and clipped into the gap between the
        // header and the footer, so any number of offers (offersPerVillager allows up to 10) stays on
        // screen instead of running past the bottom and over the footer buttons.
        boolean hasProject = ClientProjectData.hasMenuFor(data.villagerUuid());
        int footerTop = hasProject ? this.height - 50 : this.height - 26;
        view.setViewport(VIEWPORT_TOP, Math.max(VIEWPORT_TOP, footerTop - 6));

        int y = 0;
        for (QuestCard card : data.cards()) {
            cardTops.add(y);
            int height = cardHeight(card);
            addCardButtons(card, centerX, y + height - 22);
            y += height + 4;
        }
        view.setContentHeight(y);

        if (hasProject) {
            addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.project.view"),
                            b -> QuestClientHandlers.openProjectMenu(data.villagerUuid()))
                    .bounds(centerX - 75, this.height - 50, 150, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 26, 100, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        view.scrollBy(-(int) (delta * 12));
        return true;
    }

    /**
     * Must agree exactly with {@link #renderCard}: the buttons are positioned from this, so a line
     * that wraps to three rows and is counted as one puts the Accept button on top of the text.
     */
    private int cardHeight(QuestCard card) {
        int height = CardText.height(this.font, card.title(), wrapWidth()) + 2;
        if (hasChainLabel(card)) {
            height += 10; // arc / "Part 2 of 4" line
        }
        height += dialogueLineCount(card) * 10;
        for (Component objective : card.objectives()) {
            height += CardText.heightBulleted(this.font, BULLET, objective, wrapWidth());
        }
        height += CardText.height(this.font, joinRewards(card.rewards()), wrapWidth()) + 2;
        if (hasButtons()) {
            height += 24; // buttons + padding
        }
        return height;
    }

    /**
     * Whether this status puts buttons on its cards.
     *
     * <p>{@code NO_QUESTS} can now carry one informational card — the villager saying why they have
     * nothing, from a quest's own {@code cooldown} or {@code locked} line — and reserving the button strip
     * under it would leave an empty band of nothing.
     */
    private boolean hasButtons() {
        return switch (data.status()) {
            case OFFER, READY, IN_PROGRESS -> true;
            case NO_QUESTS, BLOCKED -> false;
        };
    }

    /** The objective marker, shared by the height calculation and the draw so the indents match. */
    private static final String BULLET = " - ";

    private int dialogueLineCount(QuestCard card) {
        return this.font.split(card.dialogue(), wrapWidth()).size();
    }

    private static boolean hasChainLabel(QuestCard card) {
        return !card.chainLabel().getString().isEmpty();
    }

    private void addCardButtons(QuestCard card, int centerX, int buttonY) {
        ResourceLocation questId = card.questId();
        switch (data.status()) {
            case OFFER -> addRow(centerX, buttonY,
                    button("mcaquests.button.accept",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, true))),
                    button("mcaquests.button.decline",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, false))));
            case READY -> addRow(centerX, buttonY,
                    button("mcaquests.button.complete",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestTurnInC2SPacket(data.villagerUuid(), questId))),
                    button("mcaquests.button.abandon",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))));
            case IN_PROGRESS -> addRow(centerX, buttonY,
                    button("mcaquests.button.abandon",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))));
            default -> {
            }
        }
    }

    private Button.Builder button(String key, Runnable action) {
        return Button.builder(Component.translatable(key), b -> action.run());
    }

    /** {@code contentY} is a card-space y; {@link #render} maps it to the screen as the view scrolls. */
    private void addRow(int centerX, int contentY, Button.Builder... builders) {
        int width = 90;
        int gap = 6;
        int total = builders.length * width + (builders.length - 1) * gap;
        int x = centerX - total / 2;
        for (Button.Builder builder : builders) {
            Button built = builder.bounds(x, view.screenY(contentY), width, 20).build();
            addRenderableWidget(built);
            scrolledButtons.add(new ScrolledButton(built, contentY));
            x += width + gap;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int left = centerX - wrapWidth() / 2;

        graphics.drawCenteredString(this.font, data.villagerName(), centerX, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.profession", data.profession()), centerX, 24, 0x9A9A9A);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.hearts", data.hearts()), centerX, 35, 0x9A9A9A);

        if (data.cards().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcaquests.status.no_quests"), centerX, this.height / 2, 0xFFFFFF);
        } else {
            // super.render draws widgets outside our scissor and cannot clip them, so a button that is
            // not wholly inside the viewport is hidden instead — which also makes it unclickable
            // (AbstractWidget.clicked tests visible), so it can no longer swallow a footer click.
            for (ScrolledButton scrolled : scrolledButtons) {
                scrolled.button().setY(view.screenY(scrolled.contentY()));
                scrolled.button().visible = view.isFullyVisible(scrolled.contentY(), 20);
            }
            boolean ready = data.status() == QuestMenuStatus.READY;
            int right = left + wrapWidth();
            graphics.enableScissor(left, view.top(), right, view.bottom());
            for (int i = 0; i < data.cards().size(); i++) {
                renderCard(graphics, data.cards().get(i), left, view.screenY(cardTops.get(i)), ready);
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

    private void renderCard(GuiGraphics graphics, QuestCard card, int left, int top, boolean ready) {
        int y = top;
        y = CardText.draw(graphics, this.font, card.title(), left, y, wrapWidth(),
                ready ? 0x5CFF5C : 0xFFE08A) + 2;
        if (hasChainLabel(card)) {
            graphics.drawString(this.font, card.chainLabel(), left, y, 0x9A9A9A);
            y += 10;
        }
        for (FormattedCharSequence line : this.font.split(card.dialogue(), wrapWidth())) {
            graphics.drawString(this.font, line, left, y, 0xCFCFCF);
            y += 10;
        }
        for (Component objective : card.objectives()) {
            y = CardText.drawBulleted(graphics, this.font, BULLET, objective, left, y,
                    wrapWidth(), 0xBFBFBF);
        }
        CardText.draw(graphics, this.font, joinRewards(card.rewards()), left, y, wrapWidth(), 0x88CC88);
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
