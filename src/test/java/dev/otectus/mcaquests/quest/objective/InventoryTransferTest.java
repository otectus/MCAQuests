package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTransferTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void sharedSourceCannotPayTwoObjectivesWithOneStack() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 8));
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BREAD, 6, null));
        assertFalse(plan.reserve(Items.BREAD, 6, null));
        assertFalse(plan.commit());
        assertEquals(8, source.getItem(0).getCount());
    }

    @Test
    void destinationsShareCapacityAcrossTheWholeQuest() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 8),
                new ItemStack(Items.CARROT, 8));
        SimpleContainer destination = new SimpleContainer(1);
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BREAD, 8, destination));
        assertFalse(plan.reserve(Items.CARROT, 8, destination));
        assertFalse(plan.commit());
        assertTrue(destination.isEmpty());
        assertEquals(8, source.getItem(0).getCount());
        assertEquals(8, source.getItem(1).getCount());
    }

    @Test
    void handoverPreservesNamesAndNeverMergesDifferentStackData() {
        ItemStack named = new ItemStack(Items.BREAD, 3);
        named.setHoverName(Component.literal("Family recipe"));
        SimpleContainer source = new SimpleContainer(named);
        SimpleContainer destination = new SimpleContainer(new ItemStack(Items.BREAD, 60), ItemStack.EMPTY);
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BREAD, 3, destination));
        assertTrue(plan.commit());
        assertTrue(source.isEmpty());
        assertEquals(60, destination.getItem(0).getCount());
        assertEquals(3, destination.getItem(1).getCount());
        assertEquals("Family recipe", destination.getItem(1).getHoverName().getString());
        assertFalse(plan.commit(), "a transaction can only commit once");
    }

    @Test
    void tagStylePayloadCanTransferMultipleMatchingItems() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.OAK_LOG, 3),
                new ItemStack(Items.BIRCH_LOG, 4));
        SimpleContainer destination = new SimpleContainer(2);
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(stack -> stack.is(Items.OAK_LOG) || stack.is(Items.BIRCH_LOG), 6, destination));
        assertTrue(plan.commit());
        assertEquals(1, source.getItem(1).getCount());
        assertEquals(3, destination.getItem(0).getCount());
        assertEquals(3, destination.getItem(1).getCount());
    }

    @Test
    void stalePlanDoesNotOverwriteAnInventoryChange() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 8));
        SimpleContainer destination = new SimpleContainer(1);
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BREAD, 8, destination));
        destination.setItem(0, new ItemStack(Items.DIAMOND));
        assertFalse(plan.commit());
        assertEquals(8, source.getItem(0).getCount());
        assertTrue(destination.getItem(0).is(Items.DIAMOND));
    }

    @Test
    void throwingContainerListenerRestoresBothInventories() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 8));
        SimpleContainer destination = new SimpleContainer(1);
        destination.addListener(ignored -> { throw new IllegalStateException("broken inventory listener"); });
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(Items.BREAD, 8, destination));
        assertFalse(plan.commit());
        assertEquals(8, source.getItem(0).getCount());
        assertTrue(destination.isEmpty());
    }

    @Test
    void customSlotRestrictionsAndStackLimitsAreRespected() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 12));
        SimpleContainer destination = new SimpleContainer(2) {
            @Override public int getMaxStackSize() { return 4; }
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 1; }
        };
        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertFalse(plan.reserve(Items.BREAD, 5, destination));
        assertFalse(plan.commit());
        assertEquals(12, source.getItem(0).getCount());
        InventoryTransfer.Plan valid = new InventoryTransfer.Plan(source);
        assertTrue(valid.reserve(Items.BREAD, 4, destination));
        assertTrue(valid.commit());
        assertTrue(destination.getItem(0).isEmpty());
        assertEquals(4, destination.getItem(1).getCount());
    }
}
