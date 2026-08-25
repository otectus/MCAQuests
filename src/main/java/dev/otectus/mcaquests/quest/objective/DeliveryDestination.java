package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Where the goods from an item delivery actually end up (Townstead spec §5.6).
 *
 * <pre>{@code
 * "destination": { "type": "townstead_villager_inventory", "target": "giver" }
 * }</pre>
 *
 * <p>By default they are simply consumed, which is what every existing quest does and what happens
 * when no destination is given. Putting them in the villager's own inventory instead is what turns
 * "bring me bread" from a token gesture into something with consequences: a Townstead villager eats
 * out of that inventory, so the food a player hands over is the food that keeps them alive.
 *
 * <p><b>No Townstead capability is required.</b> The inventory belongs to MCA and the whole transfer
 * is vanilla {@link Container} work — Townstead only supplies the reason to care. A delivery quest
 * therefore keeps working when Townstead is absent rather than suspending, because nothing about it
 * has stopped being possible.
 */
public record DeliveryDestination(Kind kind, TownsteadTarget target) {

    public enum Kind {
        /** Destroy the goods on hand-over. The historical behaviour, and still the default. */
        CONSUME("consume"),
        /** Put them in the villager's inventory, where Townstead will let them be used. */
        TOWNSTEAD_VILLAGER_INVENTORY("townstead_villager_inventory");

        private static final Map<String, Kind> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Kind::id, Function.identity()));

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static final Codec<Kind> CODEC = Codec.STRING.flatXmap(
                raw -> {
                    Kind kind = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                    return kind != null ? DataResult.success(kind) : DataResult.error(
                            () -> "Unknown delivery destination '" + raw + "'; expected one of "
                                    + BY_NAME.keySet()
                                    // Named explicitly because it is the one an author is most likely to
                                    // reach for, and it is deliberately not implemented yet.
                                    + ". townstead_village_storage is not available: Townstead exposes no "
                                    + "registered storage API that can be written to safely.");
                },
                kind -> DataResult.success(kind.id()));
    }

    public static final DeliveryDestination CONSUMED =
            new DeliveryDestination(Kind.CONSUME, TownsteadTarget.GIVER);

    public static final Codec<DeliveryDestination> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Kind.CODEC.fieldOf("type").forGetter(DeliveryDestination::kind),
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(DeliveryDestination::target)
            ).apply(instance, DeliveryDestination::new));

    /** True when the goods go somewhere rather than nowhere. */
    public boolean isTransfer() {
        return kind != Kind.CONSUME;
    }

    /** The container to fill, or empty when the destination cannot be resolved right now. */
    public Optional<Container> resolveContainer(ServerPlayer player, @Nullable Entity giver) {
        if (!isTransfer() || !(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Entity subject = TownsteadTargetResolver.resolveForOffer(target, player, giver, level).orElse(null);
        if (!McaCompat.isMcaVillager(subject)) {
            return Optional.empty();
        }
        // MCA villagers extend vanilla Villager, so their inventory is reachable without naming a single
        // MCA type -- which is why this needs no binding and no capability.
        return subject instanceof Villager villager ? Optional.of(villager.getInventory()) : Optional.empty();
    }

    /**
     * How many of {@code item} the container could still take. Counted rather than attempted, so the
     * caller can refuse an under-capacity transfer without having moved anything.
     */
    public static int roomFor(Container container, Item item, int wanted) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        int room = 0;
        for (int slot = 0; slot < container.getContainerSize() && room < wanted; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                room += maxStack;
            } else if (stack.is(item)) {
                room += Math.max(0, Math.min(maxStack, stack.getMaxStackSize()) - stack.getCount());
            }
        }
        return Math.min(room, wanted);
    }

    /**
     * Inserts up to {@code count}, returning however many would not fit. The caller checks
     * {@link #roomFor} first, so a non-zero return here means the container changed underneath us
     * between the check and the commit.
     */
    public static int insert(Container container, Item item, int count) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        int remaining = count;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                int give = Math.min(remaining, maxStack);
                container.setItem(slot, new ItemStack(item, give));
                remaining -= give;
            } else if (stack.is(item)) {
                int give = Math.min(remaining, Math.min(maxStack, stack.getMaxStackSize()) - stack.getCount());
                if (give > 0) {
                    stack.grow(give);
                    remaining -= give;
                }
            }
        }
        if (remaining != count) {
            // Wakes MCA's own inventory listener, so the change is saved and synced rather than sitting
            // in a container nobody knows has been touched.
            container.setChanged();
        }
        return remaining;
    }
}
