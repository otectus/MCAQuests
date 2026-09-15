package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.state.ServerRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the stack an item-bearing reward previews <em>and</em> the one it pays out.
 *
 * <p>Every reward that shows an icon must obtain it here, because a preview built one way and a payout
 * built another way is exactly the divergence {@code QuestCard} warns about: the card would promise
 * something the player never receives.
 *
 * <p>PORT: enchantments are a datapack registry in 1.21, so an authored id cannot be resolved while the
 * quest JSON is parsed (there is no registry access at that point) and is therefore kept raw and looked
 * up here, against the running server's registries. An id that resolves to nothing is skipped with one
 * warning, the same leniency {@code ItemPoolReward} gives an item from an absent mod; {@code
 * ProgressionValidator} is where a typo is reported, since only there is a registry guaranteed present.
 */
public final class RewardStacks {

    /** Ids already reported as unresolvable, so a typo or an absent mod warns once, not once per card. */
    private static final Set<ResourceLocation> WARNED = Collections.synchronizedSet(new HashSet<>());

    private RewardStacks() {
    }

    /**
     * The reward stack, with each declared enchantment applied at its literal level, clamped by
     * {@code maxRewardEnchantmentLevel} (0 strips enchantments entirely).
     */
    public static ItemStack build(Item item, int count, Map<ResourceLocation, Integer> enchantments) {
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
            // ItemStack.enchant routes an enchanted book to StoredEnchantments by itself in 1.21
            // (EnchantmentHelper.getComponentType), so a book stores the enchantment rather than wearing it.
            enchantment(entry.getKey()).ifPresent(held -> stack.enchant(held, level));
        }
        return stack;
    }

    /** The enchantment an authored id names, or empty (warned once) when this install has no such id. */
    public static Optional<Holder<Enchantment>> enchantment(ResourceLocation id) {
        Optional<Holder<Enchantment>> held = ServerRegistries.provider()
                .flatMap(provider -> provider.lookup(Registries.ENCHANTMENT))
                .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)))
                .map(reference -> (Holder<Enchantment>) reference);
        if (held.isEmpty() && WARNED.add(id)) {
            McaQuests.LOGGER.warn("[MCA: Quests] item reward names unknown enchantment '{}'; skipping it.", id);
        }
        return held;
    }

    /**
     * The same stack as an icon, with the count clamped to a stack. 1.20.1 needed this because
     * {@code FriendlyByteBuf.writeItem} sent the count as a byte; 1.21's {@code
     * ItemStack.OPTIONAL_STREAM_CODEC} writes a varint and would survive a larger number, but the clamp
     * stays so the card looks the same on both versions. The card's text line carries the true number.
     */
    public static ItemStack previewIcon(ItemStack built) {
        ItemStack icon = built.copy();
        icon.setCount(Math.min(built.getCount(), 64));
        return icon;
    }
}
