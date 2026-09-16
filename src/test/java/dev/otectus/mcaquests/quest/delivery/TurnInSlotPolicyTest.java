package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final turn-in obeys the same source-slot policy as every other delivery route.
 *
 * <p>It did not: turn-in planned its own transaction over the whole {@link Inventory} container, which
 * includes the four armour slots and the offhand. A quest for a diamond chestplate could therefore take
 * the one the player was wearing, and a quest for bread could take the stack they were holding up —
 * neither of which the Deliver button or MCA's Gift would ever do.
 */
class TurnInSlotPolicyTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST_ID = new ResourceLocation("testpack", "turn_in_slots");

    @Test
    @DisplayName("turn-in never reserves from a worn armour slot")
    void wornArmourIsNeverReserved() throws Exception {
        ServerPlayer player = inventoryOnlyPlayer();
        ItemDeliveryObjective delivery = new ItemDeliveryObjective(Items.DIAMOND_CHESTPLATE, 1, true);
        QuestDefinition def = questOf(delivery);
        ActiveQuest active = activeQuest();
        player.getInventory().setItem(Inventory.INVENTORY_SIZE, new ItemStack(Items.DIAMOND_CHESTPLATE));

        assertFalse(delivery.isSatisfied(player, active.progress(0)),
                "a chestplate the player is wearing is not stock this delivery may spend");
        DeliveryService.TurnInPlan refused = DeliveryService.planTurnIn(player, def, active, null);
        assertFalse(refused.isPlanned(), "turn-in must not reach into the armour slots to pay");
        assertFalse(refused.commit(), "a refused turn-in has nothing to commit");
        assertEquals(Items.DIAMOND_CHESTPLATE,
                player.getInventory().getItem(Inventory.INVENTORY_SIZE).getItem(),
                "the worn chestplate is still on the player");

        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
        DeliveryService.TurnInPlan planned = DeliveryService.planTurnIn(player, def, active, null);
        assertTrue(planned.isPlanned(), "the one in the pack pays");
        assertTrue(planned.commit());
        planned.creditLedger(active);

        assertTrue(player.getInventory().getItem(0).isEmpty(), "the pack paid");
        assertEquals(Items.DIAMOND_CHESTPLATE,
                player.getInventory().getItem(Inventory.INVENTORY_SIZE).getItem(),
                "the worn chestplate survived a turn-in it could have paid for");
        assertEquals(1, delivery.deliveredUnits(active.progress(0)));
        assertEquals(0, delivery.outstandingUnits(active.progress(0)));
    }

    @Test
    @DisplayName("turn-in leaves the offhand alone unless the item is in the pack")
    void offhandIsNeverReserved() throws Exception {
        ServerPlayer player = inventoryOnlyPlayer();
        ItemDeliveryObjective delivery = new ItemDeliveryObjective(Items.BREAD, 3, true);
        QuestDefinition def = questOf(delivery);
        ActiveQuest active = activeQuest();
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.BREAD, 3));

        assertFalse(DeliveryService.planTurnIn(player, def, active, null).isPlanned(),
                "the offhand is a held position, not stock: only an explicit selection may spend it");

        player.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        DeliveryService.TurnInPlan planned = DeliveryService.planTurnIn(player, def, active, null);
        assertTrue(planned.isPlanned());
        assertTrue(planned.commit());

        assertTrue(player.getInventory().getItem(0).isEmpty());
        assertEquals(3, player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount(),
                "what the player was holding up stayed there");
    }

    @Test
    @DisplayName("turn-in charges only the units still outstanding, in one transaction")
    void onlyOutstandingUnitsAreCharged() throws Exception {
        ServerPlayer player = inventoryOnlyPlayer();
        ItemDeliveryObjective delivery = new ItemDeliveryObjective(Items.BLAZE_ROD, 6, true);
        QuestDefinition def = questOf(delivery);
        ActiveQuest active = activeQuest();
        player.getInventory().setItem(0, new ItemStack(Items.BLAZE_ROD, 9));
        DeliveryLedger.credit(active.progress(0), DeliveryLedger.fingerprintOf(delivery), 2, 6);

        DeliveryService.TurnInPlan planned = DeliveryService.planTurnIn(player, def, active, null);
        assertTrue(planned.isPlanned());
        assertEquals(1, planned.charges().size());
        assertEquals(4, planned.charges().get(0).units(), "two were already handed over");
        assertTrue(planned.commit());
        planned.creditLedger(active);

        assertEquals(5, player.getInventory().getItem(0).getCount(), "the nine rods pay four, not nine");
        assertEquals(6, delivery.deliveredUnits(active.progress(0)));
        assertEquals(0, delivery.outstandingUnits(active.progress(0)));
    }

    @Test
    @DisplayName("the turn-in route is a request of its own method, on the default slot policy")
    void turnInRequestShape() {
        DeliveryRequest request = DeliveryRequest.turnIn(UUID.randomUUID(), QUEST_ID, 0, UUID.randomUUID());

        assertEquals(DeliveryRequest.Method.TURN_IN, request.method());
        assertFalse(request.hasExplicitSlots(), "turn-in never names slots, so it gets the default policy");
        assertFalse(request.hasRevision(), "the server drives turn-in; there is no card to be stale against");
        assertEquals(4, request.authorizedUnits(4), "everything still owed, and no more");
    }

    private static QuestDefinition questOf(ItemDeliveryObjective delivery) {
        return new QuestDefinition(QUEST_ID, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE), Map.of(),
                List.of(delivery), List.of(), TurnInSpec.DEFAULT, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), OfferShaping.NONE, QuestReputationBlock.NONE);
    }

    private static ActiveQuest activeQuest() {
        return ActiveQuest.create(QUEST_ID, UUID.randomUUID(), Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"), new ResourceLocation("minecraft", "overworld"),
                0, 2, null);
    }

    /** No world is reached by a consumed delivery; initialize just the player's real inventory. */
    private static ServerPlayer inventoryOnlyPlayer() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field access = unsafeType.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        ServerPlayer player = (ServerPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(access.get(null), ServerPlayer.class);
        Field inventory = Player.class.getDeclaredField("inventory");
        inventory.setAccessible(true);
        inventory.set(player, new Inventory(player));
        return player;
    }
}
