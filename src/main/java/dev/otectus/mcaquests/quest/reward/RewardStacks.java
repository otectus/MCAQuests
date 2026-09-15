package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.McaQuestsConfig;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.Map;

/**
 * Builds the stack an item-bearing reward previews <em>and</em> the one it pays out.
 *
 * <p>Every reward that shows an icon must obtain it here, because a preview built one way and a payout
 * built another way is exactly the divergence {@code QuestCard} warns about: the card would promise
 * something the player never receives.
 */
public final class RewardStacks {

    private RewardStacks() {
    }

    /**
     * The reward stack, with each declared enchantment applied at its literal level, clamped by
     * {@code maxRewardEnchantmentLevel} (0 strips enchantments entirely).
     */
    public static ItemStack build(Item item, int count, Map<Enchantment, Integer> enchantments) {
        ItemStack stack = new ItemStack(item, count);
        if (enchantments.isEmpty()) {
            return stack;
        }
        int cap = McaQuestsConfig.COMMON.maxRewardEnchantmentLevel.get();
        for (var entry : enchantments.entrySet()) {
            int level = Math.min(entry.getValue(), cap);
            if (level <= 0) {
                continue;
            }
            // Levels are the authored numbers applied verbatim; never EnchantmentHelper.enchantItem,
            // whose "table power" rolls a different (and unpredictable) set of enchantments.
            if (item == Items.ENCHANTED_BOOK) {
                // A book stores its enchantments under StoredEnchantments; ItemStack.enchant would write
                // the wrong tag and produce a book that looks enchanted but grinds to nothing.
                EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(entry.getKey(), level));
            } else {
                stack.enchant(entry.getKey(), level);
            }
        }
        return stack;
    }

    /**
     * The same stack as an icon. {@code FriendlyByteBuf.writeItem} sends the count as a byte, so a
     * preview of more than 127 wraps on the wire; the card's text line carries the true number.
     */
    public static ItemStack previewIcon(ItemStack built) {
        ItemStack icon = built.copy();
        icon.setCount(Math.min(built.getCount(), 64));
        return icon;
    }
}
