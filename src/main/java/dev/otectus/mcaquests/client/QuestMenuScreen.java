package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.McaButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.CardObjective;
import dev.otectus.mcaquests.network.QuestAbandonC2SPacket;
import dev.otectus.mcaquests.network.QuestCard;
import dev.otectus.mcaquests.network.QuestDecisionC2SPacket;
import dev.otectus.mcaquests.network.QuestDeliverC2SPacket;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private QuestMenuDataS2CPacket data;
    /** Card tops in content space (0 = first card), turned into screen y through {@link #view}. */
    private final List<Integer> cardTops = new ArrayList<>();
    /** The competing obligations of an ambiguous delivery, or empty when there is no question to ask. */
    private List<Choice> chooser = List.of();
    private int chooserTop;

    /** Resolved once on open; null when the villager is not a loaded living entity on this client. */
    @Nullable
    private LivingEntity portrait;
    private boolean portraitResolved;

    public QuestMenuScreen(QuestMenuDataS2CPacket data) {
        super(Component.translatable("mcaquests.screen.quests.title"));
        this.data = data;
    }

    java.util.UUID villagerUuid() {
        return data.villagerUuid();
    }

    /** Keep the current scroll position when a decision refreshes this conversation. */
    void refresh(QuestMenuDataS2CPacket updated) {
        this.data = updated;
        rebuildWidgets();
    }

    @Override
    protected int extraHeaderHeight() {
        return PORTRAIT_H + 6 + (hasGreeting() ? GREETING_H : 0) + noticeHeight();
    }

    /**
     * The result of whatever the player last did, in the header rather than in the scrolled content.
     *
     * <p>In the header because it must be readable wherever the cards happen to be scrolled to: a
     * refusal the player cannot see is the failure this whole area of the mod exists to fix, and the
     * chat stream is behind the screen.
     */
    private int noticeHeight() {
        return hasNotice() ? noticeLines().size() * 10 + 2 : 0;
    }

    private boolean hasNotice() {
        return !data.notice().getString().isEmpty();
    }

    private List<FormattedCharSequence> noticeLines() {
        return this.font.split(data.notice(), Math.max(1, contentWidth()));
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
        chooser = buildChooser();
        chooserTop = y;
        if (!chooser.isEmpty()) {
            addChooserButtons(y);
            y += chooserHeight() + CARD_GAP;
        }
        for (QuestCard card : data.cards()) {
            cardTops.add(y);
            int height = cardHeight(card);
            addCardButtons(card, y + height);
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
     * One obligation competing for the same item as another, as the chooser offers it.
     *
     * <p>{@code cardIndex}/{@code objectiveIndex} are how the choice is sent: the chooser is a
     * shortcut to a card's own Deliver action, never a second way of deciding what a click means.
     */
    private record Choice(int cardIndex, int objectiveIndex, QuestCard card, CardObjective objective) {
    }

    /**
     * The competing obligations when more than one quest wants the same item from this villager.
     *
     * <p>This is the menu's half of the ambiguity rule. A Gift gesture carries no quest context, so
     * when two obligations want the crossbow in the player's hand the server refuses to guess and says
     * so; the answer has to be given somewhere, and this is it — the same items, named quest by quest,
     * paid with one click and without the item having been taken first.
     *
     * <p>Empty in the ordinary case, which is one obligation per item: a chooser that appeared when
     * there was nothing to choose would be a permanent extra row on every delivery card.
     */
    private List<Choice> buildChooser() {
        Map<net.minecraft.world.item.Item, List<Choice>> byItem = new LinkedHashMap<>();
        for (int c = 0; c < data.cards().size(); c++) {
            QuestCard card = data.cards().get(c);
            for (int i = 0; i < card.objectives().size(); i++) {
                CardObjective objective = card.objectives().get(i);
                if (!objective.delivery().actionable() || objective.icon().isEmpty()
                        || objective.deliverableNow() <= 0) {
                    continue;
                }
                byItem.computeIfAbsent(objective.icon().getItem(), key -> new ArrayList<>())
                        .add(new Choice(c, i, card, objective));
            }
        }
        for (List<Choice> competing : byItem.values()) {
            if (competing.size() > 1) {
                return competing;
            }
        }
        return List.of();
    }

    /** Header line plus one row of buttons per pair of competing obligations. */
    private int chooserHeight() {
        if (chooser.isEmpty()) {
            return 0;
        }
        return CARD_PAD * 2 + this.font.split(chooserTitle(), wrapWidth()).size() * 10
                + (chooser.size() + 1) / 2 * BUTTON_STRIP;
    }

    private Component chooserTitle() {
        return Component.translatable("mcaquests.delivery.choose",
                chooser.get(0).objective().icon().getHoverName());
    }

    private void addChooserButtons(int top) {
        int firstRow = top + CARD_PAD + this.font.split(chooserTitle(), wrapWidth()).size() * 10;
        for (int i = 0; i < chooser.size(); i += 2) {
            List<McaButton.Builder> row = new ArrayList<>();
            row.add(choiceButton(chooser.get(i)));
            if (i + 1 < chooser.size()) {
                row.add(choiceButton(chooser.get(i + 1)));
            }
            addRow(firstRow + (i / 2) * BUTTON_STRIP, 110, row.toArray(new McaButton.Builder[0]));
        }
    }

    /** A chooser entry: the quest's own title, paying that quest's obligation and no other. */
    private McaButton.Builder choiceButton(Choice choice) {
        return McaButton.create(choice.card().title(), b -> sendDeliver(choice.card(),
                        choice.objectiveIndex(), false))
                .tooltip(Component.translatable("mcaquests.tooltip.delivery_choose",
                        choice.objective().deliverableNow(),
                        choice.objective().icon().getHoverName(), choice.card().title()));
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
            // "Delivered: 1 / 2   Available: 0", wrapped and measured like every other card line.
            height += objective.delivery().isDelivery()
                    ? CardText.height(this.font, deliveryLine(objective), deliveryWidth()) : 0;
        }
        height += giftHint(card).map(hint -> CardText.height(this.font, hint, wrapWidth())).orElse(0);
        height += CardText.height(this.font, joinRewards(card.rewards()), wrapWidth()) + 2;
        if (!card.rewardIcons().isEmpty()) {
            height += SLOT_PITCH;
        }
        height += buttonRowCount(card) * BUTTON_STRIP;
        return height;
    }

    /**
     * How many 20px rows of buttons this card carries.
     *
     * <p><b>Per card, not per screen.</b> The menu used to choose its buttons from one global status,
     * so a villager holding a finished quest and an unfinished one drew Complete on both — the second
     * card offering to hand in a quest that was not done. Each card now says what it is.
     *
     * <p>{@code NO_QUESTS} keeps its zero: an informational card — the villager explaining why they
     * have nothing — would otherwise reserve an empty band under a line of text.
     *
     * <p>Must agree exactly with {@link #addCardButtons}, which is why the two sit together: delivery
     * actions go two to a row, and an in-progress card always has one final row for Abandon (with
     * Deliver &amp; complete beside it when the server says that would finish the quest here).
     */
    private static int buttonRowCount(QuestCard card) {
        return switch (card.state()) {
            case OFFER, READY -> 1;
            case IN_PROGRESS -> (deliveryActions(card).size() + 1) / 2 + 1;
            case NO_QUESTS, BLOCKED -> 0;
        };
    }

    /** The objective indices on this card that carry a delivery action, enabled or explained. */
    private static List<Integer> deliveryActions(QuestCard card) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < card.objectives().size(); i++) {
            if (card.objectives().get(i).delivery().hasAction()) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * The "or gift it to them" line for this card, when Gift can actually pay one of its obligations.
     *
     * <p>One line per card rather than per objective: it is the same sentence about the same villager,
     * and a card with three deliveries does not need it three times. Absent entirely when the bridge is
     * unavailable — an installation whose MCA shape the hook could not attach to must not be told to
     * use a route that would hand the quest item over as an ordinary present.
     */
    private java.util.Optional<Component> giftHint(QuestCard card) {
        for (CardObjective objective : card.objectives()) {
            if (objective.giftCapable() && objective.delivery().actionable()) {
                return java.util.Optional.of(Component.translatable("mcaquests.delivery.gift_hint",
                        objective.icon().isEmpty() ? objective.text() : objective.icon().getHoverName(),
                        data.villagerName()));
            }
        }
        return java.util.Optional.empty();
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
        if (objective.unavailable() || objective.required() <= 0 || objective.delivery().isDelivery()) {
            // A delivery says it properly on its own line below: "(2/2)" beside a player who is merely
            // carrying two crossbows read as "handed over", which is the misreport this feature exists
            // to end.
            return objective.text();
        }
        return objective.text().copy()
                .append(Component.literal("  (" + objective.current() + "/" + objective.required() + ")"));
    }

    /** The delivery line is indented under its objective, so it wraps to a narrower column. */
    private int deliveryWidth() {
        return Math.max(1, wrapWidth() - 8);
    }

    /** What has changed hands and what the player is carrying, as two separate facts. */
    private static Component deliveryLine(CardObjective objective) {
        return Component.translatable("mcaquests.delivery.line", objective.delivered(),
                objective.required(), objective.available());
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

    /**
     * The buttons under one card, from that card's own state.
     *
     * <p>{@code cardBottom} is the content-space bottom of the card; rows are laid out upwards from it
     * so the count here and the space {@link #buttonRowCount} reserved cannot drift apart.
     */
    private void addCardButtons(QuestCard card, int cardBottom) {
        ResourceLocation questId = card.questId();
        int rows = buttonRowCount(card);
        switch (card.state()) {
            case OFFER -> addRow(rowY(cardBottom, rows, 0),
                    button("mcaquests.button.accept", "mcaquests.tooltip.accept",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, true))),
                    button("mcaquests.button.decline", "mcaquests.tooltip.decline",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestDecisionC2SPacket(data.villagerUuid(), questId, false))));
            case READY -> addRow(rowY(cardBottom, rows, 0),
                    button("mcaquests.button.complete", "mcaquests.tooltip.complete",
                            () -> QuestNetwork.CHANNEL.sendToServer(new QuestTurnInC2SPacket(data.villagerUuid(), questId))),
                    abandonButton(card));
            case IN_PROGRESS -> {
                List<Integer> deliveries = deliveryActions(card);
                for (int i = 0; i < deliveries.size(); i += 2) {
                    List<McaButton.Builder> row = new ArrayList<>();
                    row.add(deliverButton(card, deliveries.get(i)));
                    if (i + 1 < deliveries.size()) {
                        row.add(deliverButton(card, deliveries.get(i + 1)));
                    }
                    addRow(rowY(cardBottom, rows, i / 2), row.toArray(new McaButton.Builder[0]));
                }
                List<McaButton.Builder> last = new ArrayList<>();
                if (card.deliverCompletes() && !deliveries.isEmpty()) {
                    last.add(deliverAndCompleteButton(card, deliveries.get(0)));
                }
                last.add(abandonButton(card));
                addRow(rowY(cardBottom, rows, rows - 1),
                        card.deliverCompletes() && !deliveries.isEmpty() ? 110 : 90,
                        last.toArray(new McaButton.Builder[0]));
            }
            default -> {
            }
        }
    }

    /** The content-space y of row {@code index} in a strip of {@code rows} at the foot of a card. */
    private static int rowY(int cardBottom, int rows, int index) {
        return cardBottom - CARD_PAD - (rows - index) * BUTTON_STRIP + (BUTTON_STRIP - 20);
    }

    /**
     * Hand this obligation's goods over, or say why that cannot be done here.
     *
     * <p>A disabled control always carries its sentence: the server sends the reason with the card, so
     * "Visit Rowan to deliver these items" is a tooltip rather than an unexplained grey button. The
     * quantity on the label is the server's own {@code min(available, outstanding)} — the client never
     * decides how much a click is worth, it only shows what it was told and asks for that much.
     */
    private McaButton.Builder deliverButton(QuestCard card, int objectiveIndex) {
        CardObjective objective = card.objectives().get(objectiveIndex);
        boolean proof = objective.delivery().proof();
        int units = objective.deliverableNow();
        Component label = proof
                ? Component.translatable("mcaquests.button.show_items")
                : Component.translatable("mcaquests.button.deliver", units);
        Component tooltip;
        if (!objective.delivery().actionable() || units <= 0) {
            tooltip = objective.hasReason() ? objective.reason()
                    : Component.translatable("mcaquests.tooltip.deliver.nothing");
        } else if (proof) {
            tooltip = Component.translatable("mcaquests.tooltip.show_items", data.villagerName());
        } else {
            tooltip = Component.translatable("mcaquests.tooltip.deliver", units,
                    objective.icon().isEmpty() ? objective.text() : objective.icon().getHoverName(),
                    data.villagerName());
        }
        McaButton.Builder builder = McaButton.create(label,
                        b -> sendDeliver(card, objectiveIndex, false))
                .tooltip(tooltip);
        return objective.delivery().actionable() && units > 0 ? builder : disabled(builder);
    }

    /** Hand the last of it over and finish the quest, in one validated server-side step. */
    private McaButton.Builder deliverAndCompleteButton(QuestCard card, int objectiveIndex) {
        return McaButton.create(Component.translatable("mcaquests.button.deliver_complete"),
                        b -> sendDeliver(card, objectiveIndex, true))
                .tooltip(Component.translatable("mcaquests.tooltip.deliver_complete", data.villagerName()));
    }

    /**
     * The one place a delivery is asked for.
     *
     * <p>Carries the card's own copy identity and the obligation's delivered count as the revision it
     * was drawn at, so a click made against a screen that has since moved on is refused by the server
     * rather than re-interpreted against numbers the player never saw.
     */
    private void sendDeliver(QuestCard card, int objectiveIndex, boolean thenComplete) {
        CardObjective objective = card.objectives().get(objectiveIndex);
        QuestNetwork.CHANNEL.sendToServer(QuestDeliverC2SPacket.all(data.villagerUuid(), card.instance(),
                card.questId(), objectiveIndex, objective.delivered(), thenComplete));
    }

    /**
     * Abandon, with the warning that abandoning is not a refund.
     *
     * <p>Goods already handed over are in a villager's inventory or were consumed outright, and the
     * quest going away does not bring them back. The count comes from the card's own ledger figures, so
     * the tooltip says how much is at stake rather than warning vaguely on every quest; a proof
     * objective is excluded because nothing was ever taken for it.
     */
    private McaButton.Builder abandonButton(QuestCard card) {
        ResourceLocation questId = card.questId();
        int deposited = depositedUnits(card);
        Component tooltip = deposited > 0
                ? Component.translatable("mcaquests.tooltip.abandon.deposited", deposited)
                : Component.translatable("mcaquests.tooltip.abandon");
        return McaButton.create(Component.translatable("mcaquests.button.abandon"),
                        b -> QuestNetwork.CHANNEL.sendToServer(
                                new QuestAbandonC2SPacket(data.villagerUuid(), questId)))
                .tooltip(tooltip);
    }

    /** Units this quest has actually been paid, which is the only part abandoning cannot give back. */
    private static int depositedUnits(QuestCard card) {
        int total = 0;
        for (CardObjective objective : card.objectives()) {
            if (objective.delivery().isDelivery() && !objective.delivery().proof()) {
                total += objective.delivered();
            }
        }
        return total;
    }

    private McaButton.Builder button(String key, String tooltipKey, Runnable action) {
        return McaButton.create(Component.translatable(key), b -> action.run())
                .tooltip(Component.translatable(tooltipKey));
    }

    /** Marks a builder's button inactive once built. Its tooltip still shows, which is the point. */
    private static McaButton.Builder disabled(McaButton.Builder builder) {
        return builder.active(false);
    }

    /** {@code contentY} is a card-space y; {@link #render} maps it to the screen as the view scrolls. */
    private void addRow(int contentY, McaButton.Builder... builders) {
        addRow(contentY, 90, builders);
    }

    /** As above, with a wider button — "Deliver &amp; complete" does not fit the standard 90px. */
    private void addRow(int contentY, int width, McaButton.Builder... builders) {
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
        this.renderBackground(graphics);
        renderPanel(graphics);
        renderVillagerHeader(graphics, mouseX, mouseY);

        if (data.cards().isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.status.no_quests"));
        } else {
            applyScrolledVisibility();
            beginContentClip(graphics);
            renderChooser(graphics);
            for (int i = 0; i < data.cards().size(); i++) {
                QuestCard card = data.cards().get(i);
                // Per card, not per screen: a villager holding one finished quest and one unfinished
                // one used to draw both in the ready style, and both with a Complete button.
                renderCard(graphics, card, view.screenY(cardTops.get(i)),
                        card.state() == QuestMenuStatus.READY, mouseX, mouseY);
            }
            endContentClip(graphics);
            renderScrollbar(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The chooser's own frame and question; its buttons are ordinary scrolled widgets. */
    private void renderChooser(GuiGraphics graphics) {
        if (chooser.isEmpty()) {
            return;
        }
        int top = view.screenY(chooserTop);
        Panel.card(graphics, contentLeft(), top, contentWidth(), chooserHeight(), Panel.CardStyle.READY);
        CardText.draw(graphics, this.font, chooserTitle(), contentLeft() + CARD_PAD, top + CARD_PAD,
                wrapWidth(), Palette.READY);
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

        int below = top + PORTRAIT_H + 2;
        if (hasGreeting()) {
            // Spoken, so it is styled as speech: the same colour the offers below it use, italic to
            // separate what the villager says from what the mod reports about them.
            drawFirstLine(graphics, data.greeting().copy().withStyle(ChatFormatting.ITALIC),
                    contentLeft(), below, contentWidth(), Palette.DIALOGUE);
            below += GREETING_H;
        }
        if (hasNotice()) {
            // What just happened, above the fold and outside the scrolled content, so it is readable
            // wherever the cards have been scrolled to.
            for (FormattedCharSequence line : noticeLines()) {
                graphics.drawString(this.font, line, contentLeft(), below, Palette.HEADING, false);
                below += 10;
            }
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
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, anchorX, anchorY, PORTRAIT_SCALE,
                (float) anchorX - mouseX, (float) (anchorY - PORTRAIT_H / 2) - mouseY, entity);
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
            if (objective.delivery().isDelivery()) {
                // Three facts on one line, and none of them inferred from the others: what has changed
                // hands, what the obligation asks for, and what the player is actually carrying.
                y = CardText.draw(graphics, this.font, deliveryLine(objective), left + 8, y,
                        deliveryWidth(),
                        objective.state() == CardObjective.State.DONE ? Palette.REWARD : Palette.SUBTITLE);
            }
        }
        java.util.Optional<Component> hint = giftHint(card);
        if (hint.isPresent()) {
            y = CardText.draw(graphics, this.font, hint.get(), left, y, wrapWidth(), Palette.CONTEXT);
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
