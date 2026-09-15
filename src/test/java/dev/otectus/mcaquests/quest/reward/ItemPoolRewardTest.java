package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pool's two promises: the choice is stable once frozen, and what the card previews is exactly
 * what the player is paid.
 */
class ItemPoolRewardTest {

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    private static ItemPoolReward pool(ItemPoolReward.Entry... entries) {
        return new ItemPoolReward(List.of(entries));
    }

    private static ItemPoolReward.Entry entry(String item, int count, int weight) {
        return new ItemPoolReward.Entry(new ResourceLocation(item), count, Map.of(), weight);
    }

    @Test
    @DisplayName("the same seed always picks the same entry")
    void selectionIsDeterministic() {
        ItemPoolReward reward = pool(entry("minecraft:iron_sword", 1, 2), entry("minecraft:iron_axe", 1, 1));
        int first = reward.pick(RandomSource.create(42));
        assertEquals(first, reward.pick(RandomSource.create(42)));
        assertTrue(first >= 0 && first < 2);
    }

    @Test
    @DisplayName("an entry whose item is absent is skipped, and the other raw indices do not shift")
    void unresolvableEntryIsSkipped() {
        ItemPoolReward reward = pool(entry("modx:missing", 1, 1), entry("minecraft:iron_axe", 1, 1));
        assertEquals(List.of(1), reward.resolvableIndices());
        assertTrue(reward.build(0).isEmpty(), "the absent entry builds nothing");
        assertEquals(Items.IRON_AXE, reward.build(1).orElseThrow().getItem());
        for (int seed = 0; seed < 20; seed++) {
            assertEquals(1, reward.pick(RandomSource.create(seed)));
        }
    }

    @Test
    @DisplayName("a frozen index from a longer pool clamps instead of throwing")
    void frozenIndexClamps() {
        ItemPoolReward reward = pool(entry("minecraft:iron_axe", 1, 1));
        assertEquals(0, reward.clamp(7));
        assertEquals(0, reward.clamp(-3));
        assertTrue(reward.build(reward.clamp(7)).isPresent());
    }

    @Test
    @DisplayName("the preview icon is the stack that is granted, level clamp included")
    void previewMatchesPayout() {
        ItemPoolReward reward = pool(new ItemPoolReward.Entry(new ResourceLocation("minecraft:iron_sword"), 1,
                Map.of(Enchantments.SHARPNESS, 3), 1));
        ItemStack built = reward.build(0).orElseThrow();
        ItemStack icon = reward.previewIconsFrozen(0).get(0);
        assertTrue(ItemStack.isSameItemSameTags(built, icon), "the card would promise a different item");
        // maxRewardEnchantmentLevel defaults to 1, so an authored level 3 is paid out as level 1.
        assertEquals(1, EnchantmentHelper.getEnchantments(built).get(Enchantments.SHARPNESS));
    }
}
