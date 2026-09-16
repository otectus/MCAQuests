package dev.otectus.mcaquests.quest.objective;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Plans an entire hand-over before moving anything, preserving each stack's data. */
public final class InventoryTransfer {
    private InventoryTransfer() { }

    /**
     * True while a {@link Plan} is writing inventories on this thread.
     *
     * <p>Every write here happens on the server thread, and a {@link Container} write can run a
     * listener — MCA's villager inventory does — which is arbitrary third-party code that may reach
     * back into quest state. A second commit started from inside the first would plan against
     * half-written inventories and could debit the same physical stack twice, so it is refused.
     * Deliberately a plain field rather than a thread local: this is a server-thread invariant, and a
     * value that silently became per-thread would hide a threading bug rather than surface it.
     */
    private static boolean committing;

    /** True while an inventory hand-over is committing; a nested commit is refused rather than queued. */
    public static boolean isCommitting() {
        return committing;
    }

    /**
     * The slots a bulk delivery may take goods from: the hotbar and the main inventory, never worn
     * armour and never the offhand.
     *
     * <p>The offhand and the armour slots are <em>held</em> positions — a player wearing an enchanted
     * chestplate or keeping a shield up has expressed an intention about those items that "bring me six
     * iron" must not quietly override. They can still be delivered, but only by naming their slot
     * explicitly (which is what the Gift gesture does with the main hand).
     */
    public static IntSet defaultSourceSlots(Inventory inventory) {
        return sourceSlots(inventory, false, false);
    }

    /** {@link #defaultSourceSlots} plus whichever held positions the caller explicitly opted into. */
    public static IntSet sourceSlots(Inventory inventory, boolean includeOffhand, boolean includeArmour) {
        IntSet slots = new IntOpenHashSet();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < Math.min(Inventory.INVENTORY_SIZE, size); slot++) {
            slots.add(slot);
        }
        if (includeArmour) {
            for (int slot = Inventory.INVENTORY_SIZE; slot < Math.min(Inventory.SLOT_OFFHAND, size); slot++) {
                slots.add(slot);
            }
        }
        if (includeOffhand && Inventory.SLOT_OFFHAND < size) {
            slots.add(Inventory.SLOT_OFFHAND);
        }
        return slots;
    }

    /** The one-slot allowlist a held-item gesture authorizes. */
    public static IntSet singleSlot(int slot) {
        return slot < 0 ? IntSets.EMPTY_SET : IntSets.singleton(slot);
    }

    /** True for a slot the default policy would take goods from (hotbar or main inventory). */
    public static boolean isDefaultSourceSlot(int slot) {
        return slot >= 0 && slot < Inventory.INVENTORY_SIZE;
    }

    /**
     * How many matching items {@code slots} holds. The count the player is shown must come from the
     * same slot policy the hand-over will debit, or the screen promises stock the transaction refuses
     * to touch.
     */
    public static int countIn(Container source, @Nullable IntSet slots, Predicate<ItemStack> matches) {
        int found = 0;
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            if (slots != null && !slots.contains(slot)) {
                continue;
            }
            ItemStack stack = source.getItem(slot);
            if (!stack.isEmpty() && matches.test(stack)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /**
     * True for a stack with nothing about it a player would mind losing by accident: no custom name and
     * no enchantments. Damage deliberately does not count — a worn pillager crossbow is ordinary
     * stock, and refusing it is exactly the bug that made a two-crossbow delivery impossible to finish.
     *
     * <p>PORT: all three of those facts are data components in 1.21 rather than NBT tags, so the name
     * is {@link DataComponents#CUSTOM_NAME} and the enchantments are a component that is present but
     * empty on ordinary stock. Damage ({@link DataComponents#DAMAGE}) is deliberately not read at all.
     */
    public static boolean isPlain(ItemStack stack) {
        return !stack.has(DataComponents.CUSTOM_NAME)
                && stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static final class Plan {
        private final Container source;
        private final Map<Container, Snapshot> inventories = new IdentityHashMap<>();
        private boolean valid = true;
        private boolean committed;

        public Plan(Container source) {
            this.source = source;
            snapshot(source);
        }

        public boolean reserve(Item item, int count, @Nullable Container destination) {
            return reserve(stack -> stack.is(item), count, destination);
        }

        /** A null destination consumes the selected goods; otherwise they must all fit. */
        public boolean reserve(Predicate<ItemStack> matches, int count, @Nullable Container destination) {
            return reserve(null, matches, count, destination);
        }

        /**
         * As {@link #reserve(Predicate, int, Container)}, but only {@code slots} of the source may be
         * debited — the slots the player actually authorized.
         *
         * <p>This is what makes a Gift spend the stack in the player's hand and a menu delivery spend
         * the stacks the player picked. Reserving "any stack with this item id" instead would let a
         * player holding an ordinary crossbow pay with the named one two rows down, which is not what
         * they asked for and cannot be undone.
         *
         * <p>A {@code null} allowlist means the whole source, which is the historical behaviour of the
         * other two overloads. Slots are visited in ascending order either way, so a reservation is
         * deterministic and two overlapping requirements cannot both claim the same stack.
         */
        public boolean reserve(@Nullable IntSet slots, Predicate<ItemStack> matches, int count,
                               @Nullable Container destination) {
            if (!valid || committed || count < 0 || destination == source) {
                return valid = false;
            }
            Snapshot from = snapshot(source);
            Snapshot to = destination == null ? null : snapshot(destination);
            int remaining = count;
            for (int slot = 0; slot < from.planned.size(); slot++) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack stack = from.planned.get(slot);
                if (stack.isEmpty() || (slots != null && !slots.contains(slot)) || !matches.test(stack)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                ItemStack payload = stack.copy();
                payload.setCount(taken);
                if (to != null && insert(destination, to.planned, payload) != 0) {
                    return valid = false;
                }
                stack.shrink(taken);
                remaining -= taken;
            }
            return valid = remaining == 0;
        }

        /** Fails without mutation if any inventory changed after planning. Runs on the server thread. */
        public boolean commit() {
            if (!valid || committed || committing) {
                // committing: a container listener has reentered from inside another hand-over. Its
                // snapshots were taken before that write and are no longer what is on the ground.
                return false;
            }
            for (Map.Entry<Container, Snapshot> entry : inventories.entrySet()) {
                Container container = entry.getKey();
                List<ItemStack> before = entry.getValue().before;
                if (container.getContainerSize() != before.size()) {
                    return false;
                }
                for (int slot = 0; slot < before.size(); slot++) {
                    if (!ItemStack.matches(container.getItem(slot), before.get(slot))) {
                        return false;
                    }
                }
            }
            committed = true;
            committing = true;
            try {
                // Remove the player's goods before inserting them, including during inventory callbacks.
                write(source, inventories.get(source));
                inventories.forEach((container, snapshot) -> {
                    if (container != source) {
                        write(container, snapshot);
                    }
                });
                inventories.keySet().forEach(Container::setChanged);
                return true;
            } catch (RuntimeException | LinkageError failure) {
                valid = false;
                boolean restored = true;
                for (Map.Entry<Container, Snapshot> entry : inventories.entrySet()) {
                    Container container = entry.getKey();
                    List<ItemStack> before = entry.getValue().before;
                    for (int slot = 0; slot < before.size(); slot++) {
                        try {
                            if (!ItemStack.matches(container.getItem(slot), before.get(slot))) {
                                container.setItem(slot, before.get(slot).copy());
                            }
                        } catch (RuntimeException | LinkageError rollbackFailure) {
                            if (rollbackFailure != failure) { failure.addSuppressed(rollbackFailure); }
                        }
                        restored &= ItemStack.matches(container.getItem(slot), before.get(slot));
                    }
                }
                dev.otectus.mcaquests.McaQuests.LOGGER.error(
                        "[MCA: Quests] inventory hand-over failed; source and destination restored: {}", restored, failure);
                if (!restored) {
                    throw new IllegalStateException("Inventory refused rollback after failed quest delivery", failure);
                }
                return false;
            } finally {
                // Cleared even when rollback rethrows: a failed hand-over must not lock out every later
                // delivery for the rest of the session.
                committing = false;
            }
        }

        private Snapshot snapshot(Container container) {
            return inventories.computeIfAbsent(container, Snapshot::new);
        }
    }

    private static final class Snapshot {
        private final List<ItemStack> before = new ArrayList<>();
        private final List<ItemStack> planned = new ArrayList<>();

        private Snapshot(Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                before.add(container.getItem(slot).copy());
                planned.add(container.getItem(slot).copy());
            }
        }
    }

    private static void write(Container container, Snapshot snapshot) {
        for (int slot = 0; slot < snapshot.planned.size(); slot++) {
            if (!ItemStack.matches(snapshot.before.get(slot), snapshot.planned.get(slot))) {
                container.setItem(slot, snapshot.planned.get(slot).copy());
            }
        }
    }

    private static int insert(Container container, List<ItemStack> contents, ItemStack payload) {
        int remaining = payload.getCount();
        // Fill matching stacks first, so multiple payload variants share capacity predictably.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < contents.size() && remaining > 0; slot++) {
                ItemStack stack = contents.get(slot);
                if ((pass == 0) == stack.isEmpty() || !container.canPlaceItem(slot, payload)) {
                    continue;
                }
                if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, payload)) {
                    continue;
                }
                int capacity = Math.min(container.getMaxStackSize(), payload.getMaxStackSize());
                int inserted = Math.min(remaining, Math.max(0, capacity - stack.getCount()));
                if (inserted <= 0) {
                    continue;
                }
                if (stack.isEmpty()) {
                    ItemStack placed = payload.copy();
                    placed.setCount(inserted);
                    contents.set(slot, placed);
                } else {
                    stack.grow(inserted);
                }
                remaining -= inserted;
            }
        }
        return remaining;
    }
}
