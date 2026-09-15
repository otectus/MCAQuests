package dev.otectus.mcaquests.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Excess plain item/currency payouts, retained by item id even while that item's mod is absent. */
public final class PendingItemRewards {
    /** Reserved save key for the stack ledger; never a valid item id, so the two cannot collide. */
    private static final String STACKS_KEY = "stacks";

    private final Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
    /** Stacks that carry components (enchanted rewards, loot rolls) and so are not an id+count pair. */
    private final List<ItemStack> stacks = new ArrayList<>();
    private long lastDeliveryTick = Long.MIN_VALUE;
    private int deliveredStacks;

    public void add(ResourceLocation item, long amount) {
        if (amount <= 0) { return; }
        amounts.merge(item, amount, (before, delta) -> Math.addExact(before, delta));
    }

    public void addStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { return; }
        stacks.add(stack.copy());
    }

    public Map<ResourceLocation, Long> snapshot() { return new LinkedHashMap<>(amounts); }
    public boolean isEmpty() { return amounts.isEmpty() && stacks.isEmpty(); }
    public boolean hasStacks() { return !stacks.isEmpty(); }

    /** Removes and returns the retained stacks; the caller re-queues whatever it could not deliver. */
    public List<ItemStack> drainStacks() {
        List<ItemStack> drained = new ArrayList<>(stacks);
        stacks.clear();
        return drained;
    }

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

    /** The ledger, written with the running server's registries; see {@link #save(HolderLookup.Provider)}. */
    public CompoundTag save() {
        return save(ServerRegistries.provider().orElse(RegistryAccess.EMPTY));
    }

    /**
     * PORT: a stack's NBT is registry-bound in 1.21 ({@code ItemStack.save(Provider)}), where 1.20.1
     * wrote it standalone, so the ledger needs a lookup the enclosing {@code PlayerQuestData.save()}
     * does not carry. The id/count half is unaffected and round-trips unchanged either way.
     */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        amounts.forEach((item, amount) -> tag.putLong(item.toString(), amount));
        if (!stacks.isEmpty()) {
            ListTag list = new ListTag();
            stacks.forEach(stack -> list.add(stack.save(registries)));
            tag.put(STACKS_KEY, list);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        load(ServerRegistries.provider().orElse(RegistryAccess.EMPTY), tag);
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        amounts.clear();
        stacks.clear();
        if (tag.contains(STACKS_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(STACKS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
                if (!stack.isEmpty()) { stacks.add(stack); }
            }
        }
        for (String key : tag.getAllKeys()) {
            if (STACKS_KEY.equals(key)) { continue; }
            ResourceLocation item = ResourceLocation.tryParse(key);
            if (item != null && tag.getLong(key) > 0) { amounts.put(item, tag.getLong(key)); }
        }
    }
}
