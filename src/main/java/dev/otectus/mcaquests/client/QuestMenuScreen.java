package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.QuestAbandonC2SPacket;
import dev.otectus.mcaquests.network.QuestDecisionC2SPacket;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.QuestTurnInC2SPacket;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Conversation-style quest screen (spec sections 8, 9, 21). Renders the villager header and, when a
 * quest is present, its dialogue / objectives / rewards, with state-appropriate response buttons.
 * Every button just sends a C2S packet; the server replies with fresh {@link QuestMenuDataS2CPacket}
 * data, which reopens this screen in the new state.
 */
public class QuestMenuScreen extends Screen {

    private static final int COLUMN_WIDTH = 260;

    private final QuestMenuDataS2CPacket data;

    public QuestMenuScreen(QuestMenuDataS2CPacket data) {
        super(Component.translatable("mcaquests.screen.quests.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = this.height - 36;

        if (data.hasQuest()) {
            ResourceLocation questId = new ResourceLocation(data.questId());
            switch (data.status()) {
                case OFFER -> {
                    addRow(centerX, buttonY,
                            actionButton("mcaquests.button.accept",
                                    () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, true))),
                            actionButton("mcaquests.button.decline",
                                    () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, false))),
                            backButton());
                }
                case READY -> addRow(centerX, buttonY,
                        actionButton("mcaquests.button.complete",
                                () -> QuestNetwork.CHANNEL.sendToServer(new QuestTurnInC2SPacket(data.villagerUuid(), questId))),
                        actionButton("mcaquests.button.abandon",
                                () -> QuestNetwork.CHANNEL.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))),
                        backButton());
                case IN_PROGRESS -> addRow(centerX, buttonY,
                        actionButton("mcaquests.button.abandon",
                                () -> QuestNetwork.CHANNEL.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))),
                        backButton());
                default -> addRow(centerX, buttonY, backButton());
            }
        } else {
            addRow(centerX, buttonY, backButton());
        }
    }

    private Button.Builder backButton() {
        return Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose());
    }

    private Button.Builder actionButton(String key, Runnable action) {
        return Button.builder(Component.translatable(key), b -> action.run());
    }

    /** Lays out a row of buttons centred under the card; each fires once then waits for the refresh. */
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
        int left = centerX - COLUMN_WIDTH / 2;

        graphics.drawCenteredString(this.font, data.villagerName(), centerX, 16, 0xFFFFFF);
        String professionLabel = data.professionId().isEmpty() ? "-" : data.professionId();
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.profession", professionLabel), centerX, 28, 0x9A9A9A);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.favor", data.favor()), centerX, 39, 0x9A9A9A);

        int y = 58;
        if (data.hasQuest()) {
            graphics.drawCenteredString(this.font, data.title(), centerX, y, 0xFFE08A);
            y += 16;
            for (FormattedCharSequence line : this.font.split(data.dialogue(), COLUMN_WIDTH)) {
                graphics.drawString(this.font, line, left, y, 0xCFCFCF);
                y += 11;
            }
            y += 6;
            y = drawSection(graphics, left, y, Component.translatable("mcaquests.status.objectives"), data.objectiveLines());
            y += 2;
            drawSection(graphics, left, y, Component.translatable("mcaquests.status.rewards"), data.rewardLines());
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcaquests.status.no_quests"), centerX, y + 10, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawSection(GuiGraphics graphics, int left, int y, Component header, List<Component> lines) {
        if (lines.isEmpty()) {
            return y;
        }
        graphics.drawString(this.font, header, left, y, 0xFFFFFF);
        y += 11;
        for (Component line : lines) {
            graphics.drawString(this.font, Component.literal(" - ").append(line), left, y, 0xBFBFBF);
            y += 11;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
