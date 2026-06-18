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

    private final QuestMenuDataS2CPacket data;
    private final List<Integer> cardTops = new ArrayList<>();

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
        int centerX = this.width / 2;
        int y = 52;
        for (QuestCard card : data.cards()) {
            cardTops.add(y);
            int height = cardHeight(card);
            addCardButtons(card, centerX, y + height - 22);
            y += height + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 26, 100, 20)
                .build());
    }

    private int cardHeight(QuestCard card) {
        int height = 12; // title
        height += dialogueLineCount(card) * 10;
        height += card.objectives().size() * 10;
        height += 12; // joined rewards line
        height += 24; // buttons + padding
        return height;
    }

    private int dialogueLineCount(QuestCard card) {
        return this.font.split(card.dialogue(), wrapWidth()).size();
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

    private void addRow(int centerX, int y, Button.Builder... builders) {
        int width = 90;
        int gap = 6;
        int total = builders.length * width + (builders.length - 1) * gap;
        int x = centerX - total / 2;
        for (Button.Builder builder : builders) {
            addRenderableWidget(builder.bounds(x, y, width, 20).build());
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
            boolean ready = data.status() == QuestMenuStatus.READY;
            for (int i = 0; i < data.cards().size(); i++) {
                renderCard(graphics, data.cards().get(i), left, cardTops.get(i), ready);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, QuestCard card, int left, int top, boolean ready) {
        int y = top;
        graphics.drawString(this.font, card.title(), left, y, ready ? 0x5CFF5C : 0xFFE08A);
        y += 12;
        for (FormattedCharSequence line : this.font.split(card.dialogue(), wrapWidth())) {
            graphics.drawString(this.font, line, left, y, 0xCFCFCF);
            y += 10;
        }
        for (Component objective : card.objectives()) {
            graphics.drawString(this.font, Component.literal(" - ").append(objective), left, y, 0xBFBFBF);
            y += 10;
        }
        graphics.drawString(this.font, joinRewards(card.rewards()), left, y, 0x88CC88);
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
