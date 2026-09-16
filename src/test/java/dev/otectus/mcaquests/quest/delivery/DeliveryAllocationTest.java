package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.InventoryTransfer;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Allocation: no physical unit pays two obligations, and nothing the player did not owe is taken.
 *
 * <p>These are the two properties that make a partial delivery safe. Without the first, two objectives
 * that want the same item both report themselves payable and the turn-in discovers otherwise halfway
 * through; without the second, bringing eight crossbows to a delivery of two costs six crossbows.
 */
class DeliveryAllocationTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static boolean anyLog(ItemStack stack) {
        return stack.is(Items.OAK_LOG) || stack.is(Items.BIRCH_LOG);
    }

    @Test
    @DisplayName("one stack cannot be reserved for two overlapping requirements")
    void noUnitIsReservedTwice() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.OAK_LOG, 6));
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);

        // An item requirement and a broader "any log" requirement describing the same six logs. The
        // predicate is spelled out rather than taken from a tag: item tags are not bound in a unit test,
        // and a matcher that silently matched nothing would prove the opposite of what this asserts.
        assertTrue(plan.reserve(Items.OAK_LOG, 6, null));
        assertFalse(plan.reserve(DeliveryAllocationTest::anyLog, 6, null),
                "the second requirement must see what the first already claimed");
        assertFalse(plan.commit());
        assertEquals(6, source.getItem(0).getCount(), "a refused plan takes nothing");
    }

    @Test
    @DisplayName("overlapping requirements are paid together when the stock really covers both")
    void overlappingRequirementsShareOneTransaction() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.OAK_LOG, 6),
                new ItemStack(Items.BIRCH_LOG, 4));
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);

        assertTrue(plan.reserve(Items.OAK_LOG, 6, null));
        assertTrue(plan.reserve(DeliveryAllocationTest::anyLog, 4, null));
        assertTrue(plan.commit());
        assertTrue(source.isEmpty());
    }

    @Test
    @DisplayName("surplus stays with the player: only the outstanding units are taken")
    void surplusIsRetained() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BLAZE_ROD, 9));
        ObjectiveProgress progress = new ObjectiveProgress();
        DeliverToVillagerObjective objective = new DeliverToVillagerObjective(VillagerTarget.SELF,
                new ItemTarget(Optional.of(Items.BLAZE_ROD), Optional.empty()), 6, true);
        DeliveryLedger.credit(progress, DeliveryLedger.fingerprintOf(objective), 2, 6);

        int outstanding = DeliveryLedger.outstanding(objective, progress);
        assertEquals(4, outstanding);

        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BLAZE_ROD, outstanding, null));
        assertTrue(plan.commit());
        assertEquals(5, source.getItem(0).getCount(), "the nine rods pay four, not nine");
        assertEquals(6, DeliveryLedger.credit(progress, DeliveryLedger.fingerprintOf(objective),
                outstanding, 6));
    }

    @Test
    @DisplayName("a second hand-in pays only what is still owed, and the total never exceeds it")
    void partialThenFinalNeverOvercharges() {
        ItemDeliveryObjective objective = new ItemDeliveryObjective(Items.CROSSBOW, 2, true);
        ObjectiveProgress progress = new ObjectiveProgress();
        String fingerprint = DeliveryLedger.fingerprintOf(objective);

        assertEquals(2, objective.outstandingUnits(progress));
        DeliveryLedger.credit(progress, fingerprint, 1, 2);
        assertEquals(1, objective.outstandingUnits(progress));
        DeliveryLedger.credit(progress, fingerprint, 1, 2);
        assertEquals(0, objective.outstandingUnits(progress));

        // A replayed request finds nothing outstanding rather than charging a third crossbow.
        DeliveryLedger.credit(progress, fingerprint, 1, 2);
        assertEquals(2, objective.deliveredUnits(progress));
    }
}
