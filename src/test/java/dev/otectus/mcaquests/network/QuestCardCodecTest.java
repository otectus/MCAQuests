package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.support.TestRegistries;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trips for the offer card, over a real {@link RegistryFriendlyByteBuf} and no Minecraft server.
 *
 * <p>Protocol 11 widened {@link QuestCard} three ways at once — structured {@link CardObjective}s in
 * place of sentences with the counts written into them, reward preview stacks, and the difficulty
 * band. A packet that encodes more than it decodes does not throw where the mistake was made: it
 * leaves the buffer misaligned and the <em>next</em> card in the collection comes back as nonsense,
 * which is why every case here asserts the buffer was drained exactly.
 */
class QuestCardCodecTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return TestRegistries.buffer();
    }

    private static QuestCard roundTrip(QuestCard card) {
        RegistryFriendlyByteBuf buf = buffer();
        QuestCard.encode(buf, card);
        QuestCard decoded = QuestCard.decode(buf);
        assertEquals(0, buf.readableBytes(), "decode must consume exactly what encode wrote");
        return decoded;
    }

    private static QuestCard card(List<CardObjective> objectives, List<ItemStack> icons, String difficulty) {
        return new QuestCard(ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest"),
                Component.literal("A Title"), Component.empty(), Component.literal("Bring me wheat."),
                objectives, List.of(Component.literal("3x Emerald")), icons, difficulty);
    }

    @Test
    @DisplayName("a full card round-trips every new field")
    void fullCardRoundTrips() {
        QuestCard decoded = roundTrip(card(
                List.of(new CardObjective(Component.literal("Deliver Wheat"), 3, 24,
                        CardObjective.State.PENDING, new ItemStack(Items.WHEAT))),
                List.of(new ItemStack(Items.EMERALD, 3)),
                "medium"));

        assertEquals("medium", decoded.difficulty());
        assertEquals(1, decoded.objectives().size());
        CardObjective objective = decoded.objectives().get(0);
        assertEquals(3, objective.current());
        assertEquals(24, objective.required());
        assertFalse(objective.unavailable());
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.WHEAT), objective.icon()));
        assertEquals(1, decoded.rewardIcons().size());
        assertEquals(3, decoded.rewardIcons().get(0).getCount());
    }

    @Test
    @DisplayName("the informational no-quests card carries nothing and still round-trips")
    void emptyCardRoundTrips() {
        QuestCard decoded = roundTrip(card(List.of(), List.of(), ""));

        assertTrue(decoded.objectives().isEmpty());
        assertTrue(decoded.rewardIcons().isEmpty());
        assertEquals("", decoded.difficulty(), "no declared band must stay absent, not become 'easy'");
    }

    @Test
    @DisplayName("an objective with no icon round-trips as empty rather than as air")
    void iconlessObjectiveRoundTrips() {
        QuestCard decoded = roundTrip(card(
                List.of(CardObjective.offered(Component.literal("Visit the Nether"), 1, ItemStack.EMPTY)),
                List.of(), "hard"));

        assertTrue(decoded.objectives().get(0).icon().isEmpty());
    }

    @Test
    @DisplayName("a suspended objective keeps its state and reports itself unsatisfied")
    void suspendedObjectiveRoundTrips() {
        // Suspended is neither done nor failed: the quest is waiting on a mod that is not installed,
        // and a client that read it as satisfied would offer a turn-in the server would refuse.
        QuestCard decoded = roundTrip(card(
                List.of(new CardObjective(Component.literal("Raise the docks  (on hold)"), 0, 0,
                        CardObjective.State.UNAVAILABLE, ItemStack.EMPTY)),
                List.of(), ""));

        CardObjective objective = decoded.objectives().get(0);
        assertEquals(CardObjective.State.UNAVAILABLE, objective.state());
        assertTrue(objective.unavailable());
        assertFalse(objective.satisfied());
    }

    @Test
    @DisplayName("a lost target is a state of its own, distinct from merely suspended")
    void lostObjectiveRoundTrips() {
        // Both stop an objective advancing, and a single boolean could not tell them apart, so a quest
        // whose villager had died looked exactly like one waiting on an uninstalled mod. The screens
        // draw a different glyph for each, which they can only do if the wire carries the difference.
        QuestCard decoded = roundTrip(card(
                List.of(new CardObjective(Component.literal("Deliver to Otto  (Otto has died)"), 0, 1,
                        CardObjective.State.LOST, ItemStack.EMPTY)),
                List.of(), ""));

        CardObjective objective = decoded.objectives().get(0);
        assertEquals(CardObjective.State.LOST, objective.state());
        assertTrue(objective.unavailable(), "a lost objective's counter is meaningless too");
        assertFalse(objective.satisfied());
    }

    @Test
    @DisplayName("a delivery card round-trips its copy identity, state and every delivery field")
    void deliveryCardRoundTrips() {
        CardObjective crossbows = new CardObjective(Component.literal("Deliver crossbows to Rowan"), 1, 2,
                CardObjective.State.PENDING, new ItemStack(Items.CROSSBOW), 1, 0,
                CardObjective.Delivery.DELIVER_HERE, true, Component.empty());
        java.util.UUID copy = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        QuestCard card = new QuestCard(ResourceLocation.fromNamespaceAndPath("mcaquests", "last_banner_home"),
                Component.literal("Bring the Last Banner Home"), Component.empty(),
                Component.literal("Take these to Rowan."), List.of(crossbows),
                List.of(Component.literal("3x Emerald")), List.of(new ItemStack(Items.EMERALD, 3)), "easy",
                java.util.Optional.of(copy), dev.otectus.mcaquests.quest.QuestMenuStatus.IN_PROGRESS, true);

        QuestCard decoded = roundTrip(card);
        assertEquals(java.util.Optional.of(copy), decoded.instance(),
                "the copy's identity is what stops a click landing on a re-accepted quest");
        assertEquals(dev.otectus.mcaquests.quest.QuestMenuStatus.IN_PROGRESS, decoded.state(),
                "per-card state is what stopped an in-progress card drawing Complete");
        assertTrue(decoded.deliverCompletes());

        CardObjective line = decoded.objectives().get(0);
        assertEquals(1, line.delivered());
        assertEquals(0, line.available());
        assertEquals(CardObjective.Delivery.DELIVER_HERE, line.delivery());
        assertTrue(line.giftCapable());
        assertEquals(1, line.remaining());
        assertEquals(0, line.deliverableNow(), "nothing in the pack means nothing to hand over");
    }

    @Test
    @DisplayName("a blocked delivery carries the sentence explaining it, so no button is left bare")
    void blockedDeliveryCarriesItsReason() {
        CardObjective blocked = new CardObjective(Component.literal("Deliver crossbows to Rowan"), 0, 2,
                CardObjective.State.PENDING, new ItemStack(Items.CROSSBOW), 0, 2,
                CardObjective.Delivery.DELIVER_BLOCKED, false,
                Component.translatable("mcaquests.delivery.visit_recipient", "Rowan"));

        CardObjective decoded = roundTrip(card(List.of(blocked), List.of(), "")).objectives().get(0);
        assertTrue(decoded.hasReason());
        assertEquals(CardObjective.Delivery.DELIVER_BLOCKED, decoded.delivery());
        assertFalse(decoded.delivery().actionable());
        assertTrue(decoded.delivery().hasAction(), "a blocked hand-in still draws a control to explain");
        assertFalse(decoded.giftCapable(), "the Gift hint is hidden where Gift cannot pay");
    }

    @Test
    @DisplayName("an ordinary objective keeps the pre-1.6.5 shape and claims no delivery")
    void ordinaryObjectiveIsNotADelivery() {
        CardObjective decoded = roundTrip(card(
                List.of(new CardObjective(Component.literal("Visit the Nether"), 0, 1,
                        CardObjective.State.PENDING, ItemStack.EMPTY)),
                List.of(), "")).objectives().get(0);

        assertEquals(CardObjective.Delivery.NONE, decoded.delivery());
        assertFalse(decoded.delivery().isDelivery());
        assertEquals(0, decoded.delivered());
        assertFalse(decoded.hasReason());
    }

    @Test
    @DisplayName("a finished delivery keeps its numbers and loses its action")
    void settledDeliveryStillCounts() {
        CardObjective settled = new CardObjective(Component.literal("Deliver crossbows to Rowan"), 2, 2,
                CardObjective.State.DONE, new ItemStack(Items.CROSSBOW), 2, 0,
                CardObjective.Delivery.SETTLED, false, Component.empty());

        assertTrue(settled.delivery().isDelivery(), "\"Delivered: 2 / 2\" is still worth drawing");
        assertFalse(settled.delivery().hasAction());
        assertEquals(0, settled.remaining());
    }

    @Test
    @DisplayName("a finished objective reports itself satisfied")
    void satisfiedObjective() {
        CardObjective done = new CardObjective(Component.literal("Deliver Wheat"), 24, 24,
                CardObjective.State.DONE, ItemStack.EMPTY);
        assertTrue(done.satisfied());
        assertFalse(CardObjective.offered(Component.literal("Deliver Wheat"), 24, ItemStack.EMPTY)
                .satisfied(), "an untouched offer is not satisfied");
    }
}
