package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.ReputationTierCondition;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.quest.reward.GrantTitleReward;
import dev.otectus.mcaquests.quest.reward.ItemPoolReward;
import dev.otectus.mcaquests.quest.reward.ItemReward;
import dev.otectus.mcaquests.quest.reward.RewardStacks;
import dev.otectus.mcaquests.quest.title.Titles;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Best-effort cross-reference checks for the 0.7.0 progression features. Run on demand by
 * {@code /mcaquests validate} (after all datapacks have loaded, so the tier/title registries are
 * populated), since runtime evaluation already fails safe. Produces warnings only.
 */
public final class ProgressionValidator {

    private ProgressionValidator() {
    }

    public static List<String> collectWarnings(Collection<QuestDefinition> quests) {
        List<String> warnings = new ArrayList<>();
        for (QuestDefinition def : quests) {
            // grant_title rewards referencing an undefined title.
            def.rewards().stream()
                    .filter(r -> r instanceof GrantTitleReward)
                    .map(r -> ((GrantTitleReward) r).title())
                    .filter(title -> !Titles.isDefined(title))
                    .forEach(title -> warnings.add("Quest '" + def.id() + "' grants undefined title '" + title
                            + "' (it will display its id)."));

            // Item rewards whose enchantment does not belong on that item, or exceeds its own maximum.
            for (var reward : def.rewards()) {
                if (reward instanceof ItemReward item) {
                    checkEnchantments(def.id().toString(), new ItemStack(item.item()), item.enchantments(), warnings);
                } else if (reward instanceof ItemPoolReward pool) {
                    if (pool.resolvableIndices().isEmpty()) {
                        warnings.add("Quest '" + def.id() + "' has an item_pool reward with no resolvable entry"
                                + " (it will pay nothing on this install).");
                    }
                    for (ItemPoolReward.Entry entry : pool.entries()) {
                        BuiltInRegistries.ITEM.getOptional(entry.item()).ifPresent(item ->
                                checkEnchantments(def.id().toString(), new ItemStack(item),
                                        entry.enchantments(), warnings));
                    }
                }
            }

            // reputation_tier conditions referencing an unknown tier id on their ladder.
            def.conditions().ifPresent(c -> checkConditions(def.id().toString(), c, warnings));
        }
        return warnings;
    }

    /**
     * Warnings only: an odd enchantment still applies, it just may not be what the author meant.
     *
     * <p>PORT: this is also where an enchantment id that names nothing is reported. 1.20.1 rejected it
     * while the quest was parsed, which 1.21 cannot do — the enchantment registry is a datapack registry
     * and does not exist at that point — so the reward codec keeps the id raw and {@code /mcaquests
     * validate}, which runs with a server up, is the earliest place it can be resolved at all.
     */
    private static void checkEnchantments(String questId, ItemStack stack,
                                          Map<ResourceLocation, Integer> enchantments, List<String> warnings) {
        for (var entry : enchantments.entrySet()) {
            var held = RewardStacks.enchantment(entry.getKey());
            if (held.isEmpty()) {
                warnings.add("Quest '" + questId + "' names unknown enchantment '" + entry.getKey()
                        + "' (it will be skipped when the reward is paid).");
                continue;
            }
            var enchantment = held.get().value();
            String name = entry.getKey().toString();
            // An enchanted book legitimately stores any enchantment, so it is never a mismatch.
            // PORT: ItemStack#supportsEnchantment is NeoForge's replacement for the now-deprecated
            // Enchantment#canEnchant, and additionally respects a modded item's own opinion.
            if (!stack.is(Items.ENCHANTED_BOOK) && !stack.supportsEnchantment(held.get())) {
                warnings.add("Quest '" + questId + "' enchants " + stack.getItem() + " with '" + name
                        + "', which does not normally go on that item.");
            }
            if (entry.getValue() > enchantment.getMaxLevel()) {
                warnings.add("Quest '" + questId + "' asks for '" + name + "' level " + entry.getValue()
                        + ", above its maximum of " + enchantment.getMaxLevel() + ".");
            }
        }
    }

    private static void checkConditions(String questId, QuestCondition condition, List<String> warnings) {
        if (condition instanceof AllOfCondition all) {
            all.conditions().forEach(c -> checkConditions(questId, c, warnings));
        } else if (condition instanceof AnyOfCondition any) {
            any.conditions().forEach(c -> checkConditions(questId, c, warnings));
        } else if (condition instanceof NotCondition not) {
            checkConditions(questId, not.condition(), warnings);
        } else if (condition instanceof ReputationTierCondition tier) {
            ReputationTierSet ladder = tier.ladder().map(ReputationTiers::getOrDefault).orElseGet(ReputationTiers::getDefault);
            if (ladder.indexOf(tier.minTier()) < 0) {
                warnings.add("Quest '" + questId + "' uses reputation_tier min_tier '" + tier.minTier()
                        + "' which is not in its ladder (condition will never pass).");
            }
            tier.maxTier().filter(max -> ladder.indexOf(max) < 0).ifPresent(max ->
                    warnings.add("Quest '" + questId + "' uses reputation_tier max_tier '" + max
                            + "' which is not in its ladder."));
        }
    }
}
