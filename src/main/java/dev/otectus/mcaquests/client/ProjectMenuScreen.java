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

    private final UUID villagerUuid;
    private final List<ProjectCard> cards;
    private final List<Integer> cardTops = new ArrayList<>();

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
        int centerX = this.width / 2;
        int y = 30;
        for (ProjectCard card : cards) {
            cardTops.add(y);
            int height = cardHeight(card);
            if (card.status() != ProjectMenuStatus.COMPLETE) {
                addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.project.contribute"),
                                b -> contribute(card.projectId()))
                        .bounds(centerX - 60, y + height - 22, 120, 20)
                        .build());
            }
            y += height + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 26, 100, 20)
                .build());
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
            height += 12; // label + count + bar
            if (line.yourContribution() > 0) {
                height += 9;
            }
        }
        height += 12; // rewards
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
            for (int i = 0; i < cards.size(); i++) {
                renderCard(graphics, cards.get(i), left, cardTops.get(i));
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
            Component label = Component.literal(" - ").append(line.label())
                    .append(Component.literal("  "))
                    .append(Component.translatable("mcaquests.label.project.shared", line.sharedCurrent(), line.required()));
            graphics.drawString(this.font, label, left, y, 0xBFBFBF);
            y += 10;
            renderBar(graphics, left, y, line.sharedCurrent(), line.required());
            y += BAR_HEIGHT;
            if (line.yourContribution() > 0) {
                graphics.drawString(this.font,
                        Component.translatable("mcaquests.label.project.you", line.yourContribution()), left + 8, y, 0x6FA8DC);
                y += 9;
            }
        }
        graphics.drawString(this.font, joinRewards(card.rewards()), left, y, 0x88CC88);
    }

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
