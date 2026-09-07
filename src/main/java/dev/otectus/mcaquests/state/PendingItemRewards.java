package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Excess plain item/currency payouts, retained by item id even while that item's mod is absent. */
public final class PendingItemRewards {
    private final Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
    private long lastDeliveryTick = Long.MIN_VALUE;
    private int deliveredStacks;

    public void add(ResourceLocation item, long amount) {
        if (amount <= 0) { return; }
        amounts.merge(item, amount, (before, delta) -> Math.addExact(before, delta));
    }

    public Map<ResourceLocation, Long> snapshot() { return new LinkedHashMap<>(amounts); }
    public boolean isEmpty() { return amounts.isEmpty(); }

    /** Shared per-player tick budget, including multiple project rewards paid in one login. */
    public int takeStackBudget(long tick, int requested, int limit) {
        if (lastDeliveryTick != tick) {
            lastDeliveryTick = tick;
            deliveredStacks = 0;
        }
        int granted = Math.min(Math.max(0, requested), Math.max(0, limit - deliveredStacks));
        deliveredStacks += granted;
        return granted;
    }

    /** A bounded round-robin batch so absent items cannot starve later restored items. */
    public Map<ResourceLocation, Long> nextBatch(int limit) {
        Map<ResourceLocation, Long> batch = new LinkedHashMap<>();
        for (var entry : amounts.entrySet()) {
            if (batch.size() >= limit) { break; }
            batch.put(entry.getKey(), entry.getValue());
        }
        batch.forEach((item, amount) -> { amounts.remove(item); amounts.put(item, amount); });
        return batch;
    }

    public void delivered(ResourceLocation item, long amount) {
        long remaining = amounts.getOrDefault(item, 0L) - Math.max(0, amount);
        if (remaining <= 0) { amounts.remove(item); }
        else { amounts.put(item, remaining); }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        amounts.forEach((item, amount) -> tag.putLong(item.toString(), amount));
        return tag;
    }

    public void load(CompoundTag tag) {
        amounts.clear();
        for (String key : tag.getAllKeys()) {
            ResourceLocation item = ResourceLocation.tryParse(key);
            if (item != null && tag.getLong(key) > 0) { amounts.put(item, tag.getLong(key)); }
        }
    }
}
