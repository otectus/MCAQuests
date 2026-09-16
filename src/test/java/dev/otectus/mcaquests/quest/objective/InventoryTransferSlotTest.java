package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestBootstrap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slot-exact reservations: the goods that move are the goods the player authorized.
 *
 * <p>The old plan reserved "any stack matching this item" across the whole source, which is wrong for
 * a held-item gesture in the one way a player would actually notice: holding an ordinary crossbow and
 * choosing Gift could spend the named, enchanted one two rows down. It is also why the reported
 * two-crossbow delivery needs testing at all — both crossbows are damaged, in separate slots, and
 * neither may be disqualified for it.
 */
class InventoryTransferSlotTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static ItemStack damagedCrossbow(int damage) {
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        stack.setDamageValue(damage);
        return stack;
    }

    private static ItemStack chargedCrossbow() {
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        ListTag projectiles = new ListTag();
        projectiles.add(new ItemStack(Items.ARROW).save(new CompoundTag()));
        stack.getOrCreateTag().put("ChargedProjectiles", projectiles);
        stack.getOrCreateTag().putBoolean("Charged", true);
        return stack;
    }

    @Test
    @DisplayName("a held-slot reservation debits that slot and leaves an equivalent stack alone")
    void heldSlotIsDebited() {
        ItemStack named = new ItemStack(Items.CROSSBOW);
        named.setHoverName(Component.literal("Oathkeeper"));
        SimpleContainer source = new SimpleContainer(named, ItemStack.EMPTY, damagedCrossbow(120));

        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(InventoryTransfer.singleSlot(2), stack -> stack.is(Items.CROSSBOW), 1, null));
        assertTrue(plan.commit());

        assertTrue(source.getItem(2).isEmpty(), "the authorized slot paid");
        assertEquals("Oathkeeper", source.getItem(0).getHoverName().getString(),
                "the named crossbow was never authorized");
    }

    @Test
    @DisplayName("a slot allowlist cannot be satisfied from outside itself")
    void allowlistIsNotWidened() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 4),
                new ItemStack(Items.BREAD, 4));

        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertFalse(plan.reserve(InventoryTransfer.singleSlot(1), stack -> stack.is(Items.BREAD), 8, null));
        assertFalse(plan.commit());
        assertEquals(4, source.getItem(0).getCount());
        assertEquals(4, source.getItem(1).getCount());
    }

    @Test
    @DisplayName("two damaged crossbows in separate slots both count toward a delivery of two")
    void twoDamagedCrossbowsPayADeliveryOfTwo() {
        SimpleContainer source = new SimpleContainer(damagedCrossbow(64), ItemStack.EMPTY,
                damagedCrossbow(300));
        IntSet slots = new IntOpenHashSet(new int[]{0, 1, 2});

        assertEquals(2, InventoryTransfer.countIn(source, slots, stack -> stack.is(Items.CROSSBOW)));

        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(slots, stack -> stack.is(Items.CROSSBOW), 2, null));
        assertTrue(plan.commit());
        assertTrue(source.isEmpty(), "damage is not a reason to refuse a crossbow");
    }

    @Test
    @DisplayName("a named, enchanted, charged crossbow keeps every bit of its data when transferred")
    void valuableStackDataSurvivesTransfer() {
        ItemStack crossbow = chargedCrossbow();
        crossbow.setHoverName(Component.literal("Oathkeeper"));
        crossbow.enchant(Enchantments.UNBREAKING, 3);
        crossbow.setDamageValue(45);
        SimpleContainer source = new SimpleContainer(crossbow);
        SimpleContainer destination = new SimpleContainer(1);

        InventoryTransfer.Plan plan = new InventoryTransfer.Plan(source);
        assertTrue(plan.reserve(InventoryTransfer.singleSlot(0), stack -> stack.is(Items.CROSSBOW), 1,
                destination));
        assertTrue(plan.commit());

        ItemStack moved = destination.getItem(0);
        assertEquals("Oathkeeper", moved.getHoverName().getString());
        assertTrue(moved.isEnchanted());
        assertEquals(45, moved.getDamageValue());
        assertEquals(1, moved.getOrCreateTag().getList("ChargedProjectiles", 10).size(),
                "a loaded crossbow keeps its projectile rather than being rewritten");
    }

    @Test
    @DisplayName("only a plain stack is ordinary stock; damage alone does not make one precious")
    void plainStackDetection() {
        assertTrue(InventoryTransfer.isPlain(damagedCrossbow(200)));
        assertTrue(InventoryTransfer.isPlain(chargedCrossbow()));

        ItemStack named = new ItemStack(Items.CROSSBOW);
        named.setHoverName(Component.literal("Oathkeeper"));
        assertFalse(InventoryTransfer.isPlain(named));

        ItemStack enchanted = new ItemStack(Items.CROSSBOW);
        enchanted.enchant(Enchantments.UNBREAKING, 1);
        assertFalse(InventoryTransfer.isPlain(enchanted));
    }

    @Test
    @DisplayName("the default source policy is the hotbar and main inventory, never armour or offhand")
    void defaultSourcePolicyExcludesHeldPositions() {
        assertTrue(InventoryTransfer.isDefaultSourceSlot(0), "hotbar");
        assertTrue(InventoryTransfer.isDefaultSourceSlot(Inventory.INVENTORY_SIZE - 1), "main inventory");
        assertFalse(InventoryTransfer.isDefaultSourceSlot(Inventory.INVENTORY_SIZE), "worn armour");
        assertFalse(InventoryTransfer.isDefaultSourceSlot(Inventory.SLOT_OFFHAND), "offhand");
        assertFalse(InventoryTransfer.isDefaultSourceSlot(-1));
    }

    @Test
    @DisplayName("counting uses the same slot policy the debit will, so the screen cannot over-promise")
    void countHonoursTheAllowlist() {
        SimpleContainer source = new SimpleContainer(new ItemStack(Items.BREAD, 3),
                new ItemStack(Items.BREAD, 5));

        assertEquals(8, InventoryTransfer.countIn(source, null, stack -> stack.is(Items.BREAD)));
        assertEquals(5, InventoryTransfer.countIn(source, InventoryTransfer.singleSlot(1),
                stack -> stack.is(Items.BREAD)));
        assertEquals(0, InventoryTransfer.countIn(source, InventoryTransfer.singleSlot(4),
                stack -> stack.is(Items.BREAD)));
    }
}
