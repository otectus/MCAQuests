package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * "Bring me N of an item." Possession-based: completion is the player currently holding {@code count}
 * of {@code item}; items are consumed (if {@code consume}) only at turn-in (spec sections 14, 19).
 *
 * <p>An optional {@code destination} sends the goods somewhere instead of destroying them — see
 * {@link DeliveryDestination}. That transfer is <b>exact-once and atomic</b>: capacity is measured
 * before anything moves, the player is only charged once the goods are actually in, and a marker is
 * written so a second turn-in cannot repeat it.
 */
public record ItemDeliveryObjective(Item item, int count, boolean consume,
                                    DeliveryDestination destination,
                                    Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape before {@code source} existed. See the note on the two-argument form below: adding a
     * record component breaks every caller of the canonical constructor, and there is no reason to
     * make an add-on pay that for a field whose absence means what it always meant.
     */
    public ItemDeliveryObjective(Item item, int count, boolean consume,
                                 DeliveryDestination destination) {
        this(item, count, consume, destination, Optional.empty());
    }

    /**
     * The shape this objective had before destinations existed, kept so an add-on that constructs one
     * in code still compiles. Adding a record component is a source break for every caller of the
     * canonical constructor, and there was no reason to make anyone pay it: no destination means the
     * historical behaviour, which is exactly what {@link DeliveryDestination#CONSUMED} is.
     */
    public ItemDeliveryObjective(Item item, int count, boolean consume) {
        this(item, count, consume, DeliveryDestination.CONSUMED);
    }

    /** {@code progress.extra()} flag: the transfer has been committed and must never run again. */
    private static final String K_DELIVERED = "delivered";

    public static final MapCodec<ItemDeliveryObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemDeliveryObjective::item),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(ItemDeliveryObjective::count),
            Codec.BOOL.lenientOptionalFieldOf("consume", true).forGetter(ItemDeliveryObjective::consume),
            StrictCodecs.strictOptional(DeliveryDestination.CODEC, "destination",
                    DeliveryDestination.CONSUMED).forGetter(ItemDeliveryObjective::destination),
            SourceHint.FIELD.forGetter(ItemDeliveryObjective::source)
    ).apply(instance, ItemDeliveryObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.ITEM_DELIVERY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.item_delivery", count, item.getDescription());
    }

    /**
     * Where the goods can be got if the pack said, and otherwise where they are going.
     *
     * <p>A delivery has a place even when nobody has told it where the goods come from: the person
     * waiting for them. That was missed in the first cut of guidance, which treated "carry 24 wheat to
     * Gabrijel" as an objective with no location at all — and since deliveries are most of what this
     * mod asks for, the marker was usually absent, which read as a broken feature rather than an
     * honest silence.
     *
     * <p>It does not claim to say where wheat is. Nothing can: there is no index of wheat, and the
     * whole design of {@link SourceHint} is that a guess would send the player somewhere confidently
     * wrong. What it does say is where this quest wants them to end up, which is a true and useful
     * answer to "where next" — and for a village errand the fields are usually within sight of the
     * villager anyway.
     *
     * <p>This is a <em>marker</em>, not an outline. The giver is still not highlighted until the quest
     * is actually ready to hand in: a glowing villager means "go and interact with this person now",
     * and that is not yet true while the player is still gathering.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> hinted =
                source.flatMap(hint -> hint.guidance(player, active, progress, level));
        if (hinted.isPresent()) {
            return hinted;
        }
        return recipient(active, level).map(villager -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofEntity(
                villager, dev.otectus.mcaquests.quest.guidance.GuidanceKind.VILLAGER,
                dev.otectus.mcaquests.compat.McaCompat.getVillagerDisplayName(villager)));
    }

    /** The villager the goods are for: the giver, who is who an item delivery is always handed to. */
    private java.util.Optional<net.minecraft.world.entity.LivingEntity> recipient(ActiveQuest active,
                                                                                 ServerLevel level) {
        return level.getEntity(active.villagerUuid()) instanceof net.minecraft.world.entity.LivingEntity giver
                && dev.otectus.mcaquests.compat.McaCompat.isMcaVillager(giver)
                ? java.util.Optional.of(giver) : java.util.Optional.empty();
    }

    @Override
    public void validate(net.minecraft.resources.ResourceLocation questId, int index,
                         java.util.List<String> errors) {
        source.ifPresent(hint ->
                hint.validate("Quest '" + questId + "': objective[" + index + "]", errors));
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(countInInventory(player), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return countInInventory(player) >= count;
    }

    /**
     * True when the goods have somewhere to go. Checked before the turn-in is committed, so a villager
     * with a full inventory refuses the hand-over instead of swallowing the items into nowhere.
     */
    public boolean canDeliver(ServerPlayer player, @Nullable Entity giver) {
        if (!destination.isTransfer()) {
            return true;
        }
        Container container = destination.resolveContainer(player, giver).orElse(null);
        return container != null && DeliveryDestination.roomFor(container, item, count) >= count;
    }

    /** Why the hand-over was refused, for the player. */
    public Component refusalReason(ServerPlayer player, @Nullable Entity giver) {
        return destination.resolveContainer(player, giver).isEmpty()
                ? Component.translatable("mcaquests.message.delivery_no_target")
                : Component.translatable("mcaquests.message.delivery_full", item.getDescription());
    }

    /**
     * Destroys the goods, for the default {@code consume} destination. A transfer destination is
     * handled by {@link #deliver} instead, because it needs to know which villager is receiving and
     * this signature does not carry one.
     */
    @Override
    public void consumeOnTurnIn(ServerPlayer player, ObjectiveProgress progress) {
        if (consume && !destination.isTransfer()) {
            take(player, count);
        }
    }

    /**
     * Moves the goods from the player into the destination, exactly once.
     *
     * <p>Ordering is the whole of the safety here, and it is deliberately <em>take, then insert, then
     * refund the remainder</em>. Items only ever exist in one place at a time: they leave the player
     * before they arrive, so a container that filled up underneath us cannot duplicate them, and
     * anything that will not fit is handed straight back rather than evaporating. The marker is written
     * before the transfer, so even an exception midway cannot let a second turn-in run it again.
     *
     * <p>Called from {@code QuestManager.completeQuest}, which has already established through
     * {@link #canDeliver} that the whole amount will fit.
     */
    public void deliver(ServerPlayer player, @Nullable Entity giver, ObjectiveProgress progress) {
        if (!destination.isTransfer() || progress.extra().getBoolean(K_DELIVERED)) {
            return;
        }
        Container container = destination.resolveContainer(player, giver).orElse(null);
        if (container == null) {
            return;
        }
        progress.extra().putBoolean(K_DELIVERED, true);

        int taken = take(player, count);
        if (taken <= 0) {
            return;
        }
        int bounced = DeliveryDestination.insert(container, item, taken);
        if (bounced > 0) {
            // The container filled between canDeliver and here. The goods are already off the player,
            // so they must go back to them -- never dropped, and never left in limbo.
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, bounced));
            McaQuests.LOGGER.debug("[MCA: Quests] Delivery of {} {} bounced {}; returned to the player.",
                    taken, item, bounced);
        }
    }

    /** Removes up to {@code wanted} of the item from the player, returning how many were actually taken. */
    private int take(ServerPlayer player, int wanted) {
        int remaining = wanted;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return wanted - remaining;
    }

    private int countInInventory(ServerPlayer player) {
        int found = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(item);
    }

}
