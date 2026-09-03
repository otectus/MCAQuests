package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.McaButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.QuestAbandonC2SPacket;
import dev.otectus.mcaquests.network.QuestCard;
import dev.otectus.mcaquests.network.QuestDecisionC2SPacket;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestTurnInC2SPacket;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation / offer screen (spec sections 8, 9, 21). Renders the villager header plus the quest
 * cards: up to {@code offersPerVillager} offers (each Accept/Decline), or the single active quest
 * (Complete/Abandon). Every button just sends a C2S packet; the server replies with fresh data,
 * reopening this screen in the new state.
 *
 * <p>This is the screen the texture pass was built against, and it stays a modal of its own rather
 * than joining the log and journal's tabbed window: it is a conversation with one villager, not a
 * page of a book you leaf through.
 *
 * <p>The header shows the villager themselves, rendered live and turning to follow the cursor. The
 * entity is found by UUID among the loaded entities rather than being sent in the packet — the
 * villager you are talking to is by definition standing in front of you, so the client already has
 * them, and the menu did not need to grow a field to say so.
 */
public class QuestMenuScreen extends McaQuestsScreen {

    /** The objective marker, shared by the height calculation and the draw so the indents match. */
    /**
     * The gutter every objective line is indented by. Blank space with a glyph drawn into it — either
     * the item the objective is about, when it has one, or its state. On an offer every objective is
     * pending, so the item is the more useful of the two; the state glyph takes over once the quest is
     * in progress and the card is showing what is left to do.
     */
    private static final String BULLET = ObjectiveGlyphs.GUTTER;
    /** Inset from a card's frame to its text. Matches the card sprite's nine-slice border. */
    private static final int CARD_PAD = 5;
    /** Gap between cards. */
    private static final int CARD_GAP = 4;
    /** The button strip under a card: a 20px row plus the padding above it. */
    private static final int BUTTON_STRIP = 24;
    /** The greeting line under the villager header, when there is one. */
    private static final int GREETING_H = 11;
    /** An 18x18 slot plus the gap after it, for the reward icon row. */
    private static final int SLOT_PITCH = 20;
    /** Portrait box. Tall enough for a villager at {@link #PORTRAIT_SCALE} without cropping the hat. */
    private static final int PORTRAIT_W = 34;
    private static final int PORTRAIT_H = 42;
    private static final int PORTRAIT_SCALE = 20;

    private final QuestMenuDataS2CPacket data;
    /** Card tops in content space (0 = first card), turned into screen y through {@link #view}. */
    private final List<Integer> cardTops = new ArrayList<>();

    /** Resolved once on open; null when the villager is not a loaded living entity on this client. */
    @Nullable
    private LivingEntity portrait;
    private boolean portraitResolved;

    public QuestMenuScreen(QuestMenuDataS2CPacket data) {
        super(Component.translatable("mcaquests.screen.quests.title"));
        this.data = data;
    }

    @Override
    protected int extraHeaderHeight() {
        return PORTRAIT_H + 6 + (hasGreeting() ? GREETING_H : 0);
    }

    /** A villager with nothing to say costs the header nothing. */
    private boolean hasGreeting() {
        return !data.greeting().getString().isEmpty();
    }

    /** Text wraps to the card's interior, inside its frame. */
    private int wrapWidth() {
        return Math.max(1, contentWidth() - CARD_PAD * 2);
    }

    /**
     * The title's own width, which stops short of the difficulty pips rather than running under them.
     *
     * <p>The pips are drawn in the card's top-right corner, over the first line of the title; a quest
     * with a long name and a difficulty had the two overlapping. Quests that declare no difficulty get
     * the whole width, so nothing is narrowed for a badge that is not there.
     */
    private int titleWidth(QuestCard card) {
        return Math.max(1, wrapWidth() - (difficultyPips(card.difficulty()) != null ? 20 : 0));
    }

    @Override
    protected void init() {
        super.init();
        cardTops.clear();

        // Cards are laid out in their own space starting at 0 and clipped into the well, so any
        // number of offers (offersPerVillager allows up to 10) stays inside the panel instead of
        // running past the bottom and over the footer buttons.
        int y = 0;
        for (QuestCard card : data.cards()) {
            cardTops.add(y);
            int height = cardHeight(card);
            addCardButtons(card, y + height - CARD_PAD - 20);
            y += height + CARD_GAP;
        }
        view.setContentHeight(Math.max(0, y - CARD_GAP));

        int centerX = centerX();
        boolean hasProject = ClientProjectData.hasMenuFor(data.villagerUuid());
        if (hasProject) {
            addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.project.view"),
                            b -> QuestClientHandlers.openProjectMenu(data.villagerUuid()))
                    .bounds(centerX - 128, footerButtonY(), 120, 20)
                    .tooltip(Component.translatable("mcaquests.tooltip.project.view"))
                    .build());
            addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"),
                            b -> onClose())
                    .bounds(centerX + 8, footerButtonY(), 120, 20)
                    .build());
        } else {
            addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"),
                            b -> onClose())
                    .bounds(centerX - 50, footerButtonY(), 100, 20)
                    .build());
        }
    }

    /**
     * Must agree exactly with {@link #renderCard}: the buttons are positioned from this, so a line
     * that wraps to three rows and is counted as one puts the Accept button on top of the text.
     */
    private int cardHeight(QuestCard card) {
        int height = CARD_PAD * 2;
        height += CardText.height(this.font, card.title(), titleWidth(card)) + 2;
        if (hasChainLabel(card)) {
            height += 10; // arc / "Part 2 of 4" line
        }
        height += dialogueLineCount(card) * 10;
        for (CardObjective objective : card.objectives()) {
            height += CardText.heightBulleted(this.font, BULLET, objectiveText(objective), wrapWidth());
            height += showsBar(objective) ? Panel.barHeight() + 2 : 0;
        }
        height += CardText.height(this.font, joinRewards(card.rewards()), wrapWidth()) + 2;
        if (!card.rewardIcons().isEmpty()) {
            height += SLOT_PITCH;
        }
        if (hasButtons()) {
            height += BUTTON_STRIP;
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

    /**
     * An objective's line with its counts appended.
     *
     * <p>The server used to append these itself; sending them as numbers is what lets the bar exist,
     * and the text puts them back for players who would rather read "3/24" than estimate it.
     */
    /**
     * The item this objective is about, or the state it is in, in the line's gutter.
     *
     * <p>{@code CardObjective} has carried an {@code ItemStack} since 1.5.0 and no screen ever drew it.
     * Eight pixels is small for an item, but a stack of wheat is recognisable at that size and it
     * answers "what is this asking for" before the sentence is read.
     */
    private void objectiveGlyph(GuiGraphics graphics, CardObjective objective, int left, int y) {
        int x = left + ObjectiveGlyphs.GLYPH_X;
        int top = y + ObjectiveGlyphs.GLYPH_Y;
        if (objective.state() == CardObjective.State.PENDING && !objective.icon().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, top, 0.0F);
            graphics.pose().scale(ObjectiveGlyphs.GLYPH_SCALE, ObjectiveGlyphs.GLYPH_SCALE, 1.0F);
            graphics.renderItem(objective.icon(), 0, 0);
            graphics.pose().popPose();
            return;
        }
        Panel.iconScaled(graphics, ObjectiveGlyphs.of(objective.state()), x, top,
                ObjectiveGlyphs.GLYPH_SCALE);
    }

    private static Component objectiveText(CardObjective objective) {
        if (objective.unavailable() || objective.required() <= 0) {
            return objective.text();
        }
        return objective.text().copy()
                .append(Component.literal("  (" + objective.current() + "/" + objective.required() + ")"));
    }

    /** A bar is worth drawing only for an objective that is counted and can actually advance. */
    private static boolean showsBar(CardObjective objective) {
        return !objective.unavailable() && objective.required() > 1;
    }

    private int dialogueLineCount(QuestCard card) {
        return this.font.split(card.dialogue(), wrapWidth()).size();
    }

    private static boolean hasChainLabel(QuestCard card) {
        return !card.chainLabel().getString().isEmpty();
    }

    private void addCardButtons(QuestCard card, int buttonY) {
        ResourceLocation questId = card.questId();
        switch (data.status()) {
            case OFFER -> addRow(buttonY,
                    button("mcaquests.button.accept", "mcaquests.tooltip.accept",
                            () -> PacketDistributor.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, true))),
                    button("mcaquests.button.decline", "mcaquests.tooltip.decline",
                            () -> PacketDistributor.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, false))));
            case READY -> addRow(buttonY,
                    button("mcaquests.button.complete", "mcaquests.tooltip.complete",
                            () -> PacketDistributor.sendToServer(new QuestTurnInC2SPacket(data.villagerUuid(), questId))),
                    button("mcaquests.button.abandon", "mcaquests.tooltip.abandon",
                            () -> PacketDistributor.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))));
            case IN_PROGRESS -> addRow(buttonY,
                    button("mcaquests.button.abandon", "mcaquests.tooltip.abandon",
                            () -> PacketDistributor.sendToServer(new QuestAbandonC2SPacket(data.villagerUuid(), questId))));
            default -> {
            }
        }
    }

    private McaButton.Builder button(String key, String tooltipKey, Runnable action) {
        return McaButton.create(Component.translatable(key), b -> action.run())
                .tooltip(Component.translatable(tooltipKey));
    }

    /** {@code contentY} is a card-space y; {@link #render} maps it to the screen as the view scrolls. */
    private void addRow(int contentY, McaButton.Builder... builders) {
        int width = 90;
        int gap = 6;
        int total = builders.length * width + (builders.length - 1) * gap;
        int x = centerX() - total / 2;
        for (McaButton.Builder builder : builders) {
            McaButton built = builder.bounds(x, view.screenY(contentY), width, 20).build();
            addScrolledWidget(built, contentY, 20);
            x += width + gap;
        }
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
        renderVillagerHeader(graphics, mouseX, mouseY);

        if (data.cards().isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.status.no_quests"));
        } else {
            boolean ready = data.status() == QuestMenuStatus.READY;
            beginContentClip(graphics);
            for (int i = 0; i < data.cards().size(); i++) {
                renderCard(graphics, data.cards().get(i), view.screenY(cardTops.get(i)), ready,
                        mouseX, mouseY);
            }
            endContentClip(graphics);
            renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    /** Portrait, name, profession and hearts, between the title band and the cards. */
    private void renderVillagerHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = headerBandBottom() + 3;
        int boxX = contentLeft();
        Panel.well(graphics, boxX, top, PORTRAIT_W, PORTRAIT_H);
        renderPortrait(graphics, boxX, top, mouseX, mouseY);

        int textLeft = boxX + PORTRAIT_W + 8;
        int textWidth = Math.max(1, contentRight() - textLeft);
        // A long name or a long Portuguese profession label is truncated to the first wrapped line
        // rather than running out over the frame; the header is outside the well and so is not
        // clipped for us.
        drawFirstLine(graphics, data.villagerName(), textLeft, top + 4, textWidth, Palette.TITLE);
        drawFirstLine(graphics, Component.translatable("mcaquests.label.profession", data.profession()),
                textLeft, top + 16, textWidth, Palette.SUBTITLE);

        Component hearts = Component.translatable("mcaquests.label.hearts", data.hearts());
        Panel.icon(graphics, GuiTextures.ICON_HEART_FULL, textLeft - 2, top + 26);
        graphics.drawString(this.font, hearts, textLeft + 15, top + 30, Palette.SUBTITLE, false);
        if (inRect(textLeft - 2, top + 26, 17 + this.font.width(hearts), 16, mouseX, mouseY)) {
            tooltip(Component.translatable("mcaquests.tooltip.hearts"));
        }

        if (hasGreeting()) {
            // Spoken, so it is styled as speech: the same colour the offers below it use, italic to
            // separate what the villager says from what the mod reports about them.
            drawFirstLine(graphics, data.greeting().copy().withStyle(ChatFormatting.ITALIC),
                    contentLeft(), top + PORTRAIT_H + 2, contentWidth(), Palette.DIALOGUE);
        }
    }

    private void drawFirstLine(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        if (!lines.isEmpty()) {
            graphics.drawString(this.font, lines.get(0), x, y, colour, false);
        }
    }

    /**
     * The villager, live, turning to follow the cursor.
     *
     * <p>Scissored to its box: the renderer does not clip, and a villager's hat would otherwise draw
     * up over the title band. Silently draws nothing when the entity cannot be found — the menu is
     * still perfectly usable without a face, and a missing villager must never take the screen down.
     */
    private void renderPortrait(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        LivingEntity entity = resolvePortrait();
        if (entity == null) {
            Panel.icon(graphics, GuiTextures.ICON_PROF_VILLAGER, x + (PORTRAIT_W - 16) / 2,
                    y + (PORTRAIT_H - 16) / 2);
            return;
        }
        int anchorX = x + PORTRAIT_W / 2;
        int anchorY = y + PORTRAIT_H - 3;
        graphics.enableScissor(x + 1, y + 1, x + PORTRAIT_W - 1, y + PORTRAIT_H - 1);
        // 1.21 takes a box rather than an anchor and centres the entity in it, and it takes the look
        // angles rather than the mouse offsets that used to be turned into them. The box is centred
        // on the old anchor and is deliberately larger than the portrait, because the method scissors
        // to it too and the scissor stack intersects -- so the clip above is still the one that bites.
        float angleX = (float) Math.atan(((float) anchorX - mouseX) / 40.0F);
        float angleY = (float) Math.atan(((float) (anchorY - PORTRAIT_H / 2) - mouseY) / 40.0F);
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                anchorX - PORTRAIT_W, anchorY - PORTRAIT_H, anchorX + PORTRAIT_W, anchorY + PORTRAIT_H,
                PORTRAIT_SCALE, 0.0625F, angleX, angleY, entity);
        graphics.disableScissor();
    }

    @Nullable
    private LivingEntity resolvePortrait() {
        if (portraitResolved) {
            return portrait;
        }
        portraitResolved = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(data.villagerUuid())) {
                portrait = living;
                break;
            }
        }
        return portrait;
    }

    private void renderCard(GuiGraphics graphics, QuestCard card, int top, boolean ready,
                            int mouseX, int mouseY) {
        int width = contentWidth();
        int height = cardHeight(card);
        boolean hovered = hoveringInWell(contentLeft(), top, width, height, mouseX, mouseY);
        Panel.CardStyle style = ready ? Panel.CardStyle.READY
                : hovered ? Panel.CardStyle.HOVERED : Panel.CardStyle.RESTING;
        Panel.card(graphics, contentLeft(), top, width, height, style);

        int left = contentLeft() + CARD_PAD;
        int y = top + CARD_PAD;
        y = CardText.draw(graphics, this.font, card.title(), left, y, titleWidth(card),
                ready ? Palette.READY : Palette.TITLE) + 2;
        renderDifficulty(graphics, card, top, mouseX, mouseY);
        if (hasChainLabel(card)) {
            Panel.icon(graphics, GuiTextures.ICON_CHAIN, left - 1, y - 4);
            graphics.drawString(this.font, card.chainLabel(), left + 14, y, Palette.SUBTITLE, false);
            y += 10;
        }
        for (FormattedCharSequence line : this.font.split(card.dialogue(), wrapWidth())) {
            graphics.drawString(this.font, line, left, y, Palette.DIALOGUE, false);
            y += 10;
        }
        for (CardObjective objective : card.objectives()) {
            objectiveGlyph(graphics, objective, left, y);
            y = CardText.drawBulleted(graphics, this.font, BULLET, objectiveText(objective), left, y,
                    wrapWidth(), ObjectiveGlyphs.colour(objective.state()));
            if (showsBar(objective)) {
                Panel.bar(graphics, left + 8, y, wrapWidth() - 16, objective.current(),
                        objective.required(), GuiTextures.BAR_GREEN);
                y += Panel.barHeight() + 2;
            }
        }
        y = CardText.draw(graphics, this.font, joinRewards(card.rewards()), left, y, wrapWidth(),
                Palette.REWARD);
        renderRewardIcons(graphics, card, left, y, mouseX, mouseY);
    }

    /**
     * The declared difficulty, as pips in the card's top-right corner.
     *
     * <p>Every quest has been able to declare {@code easy}/{@code medium}/{@code hard} since
     * difficulty existed; it set the currency reward and was shown to nobody. Quests that declare
     * none get no badge, rather than being presented as easy.
     */
    private void renderDifficulty(GuiGraphics graphics, QuestCard card, int top, int mouseX, int mouseY) {
        GuiTextures.Sprite pips = difficultyPips(card.difficulty());
        if (pips == null) {
            return;
        }
        int x = contentLeft() + contentWidth() - CARD_PAD - 16;
        Panel.icon(graphics, pips, x, top + CARD_PAD - 4);
        if (hoveringInWell(x, top + CARD_PAD - 4, 16, 16, mouseX, mouseY)) {
            tooltip(Component.translatable("mcaquests.tooltip.difficulty." + card.difficulty()));
        }
    }

    /**
     * The rewards as real item icons, in vanilla slots, with vanilla's own tooltips.
     *
     * <p>The reward text was always there; a row of slots is what turns "is that an emerald or a
     * diamond, and how many" into a glance. Rewards with nothing to show -- hearts, reputation, a
     * title -- contribute no slot and are read from the line above, which every reward still has.
     */
    private void renderRewardIcons(GuiGraphics graphics, QuestCard card, int left, int y,
                                   int mouseX, int mouseY) {
        List<ItemStack> icons = card.rewardIcons();
        if (icons.isEmpty()) {
            return;
        }
        int available = Math.max(1, wrapWidth() / SLOT_PITCH);
        int shown = Math.min(icons.size(), available);
        for (int i = 0; i < shown; i++) {
            ItemStack stack = icons.get(i);
            int x = left + i * SLOT_PITCH;
            Panel.slot(graphics, x, y);
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            if (hoveringInWell(x, y, 18, 18, mouseX, mouseY)) {
                itemTooltip(stack);
            }
        }
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
