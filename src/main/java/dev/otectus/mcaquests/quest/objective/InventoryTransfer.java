package dev.otectus.mcaquests.quest.objective;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Plans an entire hand-over before moving anything, preserving each stack's data. */
public final class InventoryTransfer {
    private InventoryTransfer() { }

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
            if (!valid || committed || count < 0 || destination == source) {
                return valid = false;
            }
            Snapshot from = snapshot(source);
            Snapshot to = destination == null ? null : snapshot(destination);
            int remaining = count;
            for (ItemStack stack : from.planned) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !matches.test(stack)) {
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
            if (!valid || committed) {
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
                if (!stack.isEmpty() && !ItemStack.isSameItemSameTags(stack, payload)) {
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
