package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stack ledger added in 1.6.4 must not disturb how a 1.6.3 save reads back. */
class PendingItemRewardsStackTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("an enchanted stack survives a save/load round trip")
    void enchantedStackRoundTrips() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 1);
        PendingItemRewards pending = new PendingItemRewards();
        pending.addStack(sword);

        PendingItemRewards loaded = new PendingItemRewards();
        loaded.load(pending.save());
        assertTrue(loaded.hasStacks());
        ItemStack restored = loaded.drainStacks().get(0);
        assertEquals(Items.IRON_SWORD, restored.getItem());
        assertEquals(1, EnchantmentHelper.getEnchantments(restored).get(Enchantments.SHARPNESS));
        assertFalse(loaded.hasStacks(), "draining empties the ledger");
    }

    @Test
    @DisplayName("a 1.6.3-shaped tag of ids and counts loads unchanged")
    void legacyTagLoadsUnchanged() {
        CompoundTag legacy = new CompoundTag();
        legacy.putLong("minecraft:emerald", 12L);
        PendingItemRewards loaded = new PendingItemRewards();
        loaded.load(legacy);
        assertEquals(12L, loaded.snapshot().get(new ResourceLocation("minecraft:emerald")));
        assertFalse(loaded.hasStacks());
    }

    @Test
    @DisplayName("the reserved stacks key never becomes a phantom item id")
    void stacksKeyIsNotReadAsAnItemId() {
        PendingItemRewards pending = new PendingItemRewards();
        pending.add(new ResourceLocation("minecraft:emerald"), 3L);
        pending.addStack(new ItemStack(Items.IRON_AXE));

        PendingItemRewards loaded = new PendingItemRewards();
        loaded.load(pending.save());
        assertEquals(java.util.Set.of(new ResourceLocation("minecraft:emerald")), loaded.snapshot().keySet());
        assertTrue(loaded.hasStacks());
    }
}
