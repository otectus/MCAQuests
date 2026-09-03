package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delivery transaction, which is the one piece of this integration that can destroy or duplicate a
 * player's items if it is wrong.
 *
 * <p>Every assertion here is really the same assertion: <b>items are conserved</b>. Whatever leaves the
 * player arrives somewhere, and whatever will not fit comes back.
 */
class DeliveryDestinationTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final Item BREAD = Items.BREAD;

    private static int countIn(SimpleContainer container, Item item) {
        int found = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    @Test
    @DisplayName("an empty container has room for a whole delivery")
    void emptyContainerHasRoom() {
        SimpleContainer container = new SimpleContainer(4);

        assertEquals(32, DeliveryDestination.roomFor(container, BREAD, 32));
        assertEquals(0, DeliveryDestination.insert(container, BREAD, 32), "all 32 should fit");
        assertEquals(32, countIn(container, BREAD));
    }

    @Test
    @DisplayName("room accounts for partly-filled stacks of the same item")
    void roomAccountsForPartialStacks() {
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(BREAD, 60)); // bread stacks to 64, so 4 left here
        container.setItem(1, new ItemStack(Items.STONE, 1)); // occupied by something else entirely

        assertEquals(4, DeliveryDestination.roomFor(container, BREAD, 32),
                "only the tail of the matching stack is available");
    }

    @Test
    @DisplayName("a full container reports no room and accepts nothing")
    void fullContainerRefuses() {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.STONE, 64));

        assertEquals(0, DeliveryDestination.roomFor(container, BREAD, 16));
        assertEquals(16, DeliveryDestination.insert(container, BREAD, 16),
                "every item must bounce, so the caller can hand them back");
        assertEquals(0, countIn(container, BREAD), "nothing may be created in a container with no room");
    }

    /**
     * The failure mode that would matter most: a partial insert must report exactly what did not fit, so
     * the caller returns precisely that much and no more. An off-by-one here either eats a loaf or mints
     * one.
     */
    @Test
    @DisplayName("a partial insert reports exactly what bounced")
    void partialInsertIsConserved() {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(BREAD, 60));

        int offered = 10;
        int bounced = DeliveryDestination.insert(container, BREAD, offered);

        assertEquals(6, bounced, "only 4 of the 10 could fit");
        assertEquals(64, countIn(container, BREAD));
        assertEquals(offered, (64 - 60) + bounced, "what went in plus what came back is what was offered");
    }

    @Test
    @DisplayName("inserting nothing leaves the container untouched")
    void insertingNothingIsANoOp() {
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, new ItemStack(BREAD, 5));

        assertEquals(0, DeliveryDestination.insert(container, BREAD, 0));
        assertEquals(5, countIn(container, BREAD));
    }

    @Test
    @DisplayName("room never over-reports beyond what was asked for")
    void roomIsCappedByTheRequest() {
        SimpleContainer container = new SimpleContainer(27);

        assertEquals(3, DeliveryDestination.roomFor(container, BREAD, 3),
                "a huge container still only offers room for the amount in question");
    }

    // ------------------------------------------------------------------------------------- parsing

    private static DataResult<ItemDeliveryObjective> parse(String json) {
        return ItemDeliveryObjective.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    @DisplayName("delivery defaults to consuming, exactly as every existing quest expects")
    void defaultsToConsume() {
        ItemDeliveryObjective objective = parse("""
                {"item":"minecraft:wheat","count":24}""").result().orElseThrow();

        assertEquals(DeliveryDestination.CONSUMED, objective.destination());
        assertFalse(objective.destination().isTransfer());
        assertTrue(objective.consume());
    }

    @Test
    @DisplayName("a villager-inventory destination parses and targets the giver by default")
    void parsesVillagerInventoryDestination() {
        ItemDeliveryObjective objective = parse("""
                {"item":"minecraft:bread","count":32,
                 "destination":{"type":"townstead_villager_inventory"}}""").result().orElseThrow();

        assertTrue(objective.destination().isTransfer());
        assertEquals(TownsteadTarget.GIVER, objective.destination().target());
    }

    /**
     * Village storage is named in the spec but deliberately not implemented, because Townstead exposes
     * no registered storage API that could be written to safely and guessing at a nearby chest would be
     * worse than refusing. The error says so rather than leaving an author to wonder.
     */
    @Test
    @DisplayName("village storage is rejected with an explanation, not silently ignored")
    void villageStorageIsRejectedClearly() {
        DataResult<ItemDeliveryObjective> result = parse("""
                {"item":"minecraft:bread","count":32,
                 "destination":{"type":"townstead_village_storage"}}""");

        assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().message().contains("townstead_village_storage"),
                "the message must name what was asked for: " + result.error().orElseThrow().message());
    }
}
