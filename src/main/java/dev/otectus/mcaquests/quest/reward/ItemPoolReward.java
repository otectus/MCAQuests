package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One item chosen from a weighted list, rolled once when the quest is accepted and never again.
 *
 * <pre>
 * { "type": "mcaquests:item_pool", "entries": [
 *     { "item": "minecraft:iron_sword", "weight": 2, "enchantments": { "minecraft:sharpness": 1 } },
 *     { "item": "minecraft:iron_axe" } ] }
 * </pre>
 *
 * <p>An entry's {@code item} is kept as a raw id and resolved at use time, so a pool may name an item
 * from an optional mod: when that mod is absent the entry is skipped (with one warning per id) and the
 * quest still loads and still pays. PORT: enchantment ids are kept raw for the same reason but a
 * different cause — 1.21 makes enchantments a datapack registry, which does not exist while quest JSON
 * is parsed, so an unknown one can only be reported later (by {@code ProgressionValidator}) instead of
 * failing the load as it did on 1.20.1.
 *
 * <p>The chosen entry is stored as its <em>raw declared index</em> on the {@code ActiveQuest}, the same
 * freeze-once contract {@link CurrencyReward} uses, so re-opening the menu cannot reroll it.
 */
public record ItemPoolReward(List<Entry> entries) implements QuestReward {

    /** Ids already reported as unresolvable, so an absent mod warns once and not once per menu open. */
    private static final Set<ResourceLocation> WARNED = Collections.synchronizedSet(new HashSet<>());

    public static final MapCodec<ItemPoolReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.nonEmptyList(Entry.CODEC.listOf()).fieldOf("entries").forGetter(ItemPoolReward::entries)
    ).apply(instance, ItemPoolReward::new));

    /** One candidate: what to give, how many, how it is enchanted, and how likely it is to be picked. */
    public record Entry(ResourceLocation item, int count, Map<ResourceLocation, Integer> enchantments, int weight) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Entry::item),
                StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(Entry::count),
                StrictCodecs.strictOptional(Codec.unboundedMap(ResourceLocation.CODEC, ExtraCodecs.POSITIVE_INT),
                        "enchantments", Map.of()).forGetter(Entry::enchantments),
                StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "weight", 1).forGetter(Entry::weight)
        ).apply(instance, Entry::new));
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.ITEM_POOL;
    }

    /** Raw declared indices whose item exists in this install; the frozen value is one of these. */
    public List<Integer> resolvableIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ResourceLocation id = entries.get(i).item();
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                indices.add(i);
            } else if (WARNED.add(id)) {
                McaQuests.LOGGER.warn("[MCA: Quests] item_pool reward names unknown item '{}'; skipping that entry.", id);
            }
        }
        return indices;
    }

    /** The stack for one raw declared index, or empty when that entry's item is absent. */
    public Optional<ItemStack> build(int rawIndex) {
        if (rawIndex < 0 || rawIndex >= entries.size()) {
            return Optional.empty();
        }
        Entry entry = entries.get(rawIndex);
        return BuiltInRegistries.ITEM.getOptional(entry.item())
                .map(item -> RewardStacks.build(item, entry.count(), entry.enchantments()));
    }

    /** Weighted choice over the resolvable entries; {@code -1} when this install can pay none of them. */
    public int pick(RandomSource random) {
        List<Integer> candidates = resolvableIndices();
        if (candidates.isEmpty()) {
            return -1;
        }
        int total = 0;
        for (int index : candidates) {
            total += Math.max(1, entries.get(index).weight());
        }
        int roll = random.nextInt(total);
        for (int index : candidates) {
            roll -= Math.max(1, entries.get(index).weight());
            if (roll < 0) {
                return index;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** The offer summary: how many things this pool could give, since none has been chosen yet. */
    @Override
    public Component describe() {
        return Component.translatable("mcaquests.reward.item_pool", resolvableIndices().size());
    }

    /** The accepted summary: the one entry this player will actually be paid. */
    public Component describeFrozen(int rawIndex) {
        return build(rawIndex)
                .map(stack -> new ItemReward(stack.getItem(), stack.getCount(),
                        entries.get(clamp(rawIndex)).enchantments()).describe())
                .orElseGet(this::describe);
    }

    @Override
    public List<ItemStack> previewIcons() {
        List<ItemStack> icons = new ArrayList<>();
        for (int index : resolvableIndices()) {
            build(index).map(RewardStacks::previewIcon).ifPresent(icons::add);
        }
        return icons;
    }

    /** The accepted card shows only the chosen entry, so the icon row matches the payout. */
    public List<ItemStack> previewIconsFrozen(int rawIndex) {
        return build(rawIndex).map(stack -> List.of(RewardStacks.previewIcon(stack))).orElseGet(this::previewIcons);
    }

    /** Clamps a stored index into the declared range, so a shortened pool cannot throw on an old save. */
    public int clamp(int rawIndex) {
        return Math.max(0, Math.min(rawIndex, entries.size() - 1));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every accept path freezes a choice first, so this rolls one only as a last-resort safety net
     * (a pool reward added to a quest a player had already accepted).
     */
    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        grantChoice(player, pick(player.getRandom()));
    }

    /** Pays exactly the entry at {@code rawIndex}; a pool nobody can resolve pays nothing and logs. */
    public void grantChoice(ServerPlayer player, int rawIndex) {
        Optional<ItemStack> stack = build(rawIndex);
        if (stack.isEmpty()) {
            McaQuests.LOGGER.warn("[MCA: Quests] item_pool reward had no resolvable entry to grant; skipping it.");
            return;
        }
        ItemStack chosen = stack.get();
        Item item = chosen.getItem();
        // PORT: 1.21 has no ItemStack#hasTag; anything the build step added shows up as a component patch.
        if (!chosen.getComponentsPatch().isEmpty()) {
            ItemRewardDelivery.grant(player, chosen);
        } else {
            ItemRewardDelivery.grant(player, item, chosen.getCount());
        }
    }
}
