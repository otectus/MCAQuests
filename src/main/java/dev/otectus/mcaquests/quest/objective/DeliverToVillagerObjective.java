package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Hand a payload to a specific other villager (a family member, profession, named/UUID target).
 * Credited when the player right-clicks the resolved recipient (main hand) while carrying enough of
 * the item; the item is consumed at the hand-off (when {@code consume}) — unlike {@code item_delivery}
 * which consumes at turn-in. Once delivered it is a sticky flag (the item is already gone).
 */
public record DeliverToVillagerObjective(VillagerTarget recipient, ItemTarget item,
                                         int itemCount, boolean consume,
                                         Optional<DeliveryDestination> destination)
        implements QuestObjective, VillagerTargeted {

    /** {@code progress.extra()}: the transfer committed. Written before anything downstream fires. */
    private static final String K_TRANSFERRED = "delivered_to_inventory";

    public static final MapCodec<DeliverToVillagerObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VillagerTarget.MAP_CODEC.fieldOf("recipient").forGetter(DeliverToVillagerObjective::recipient),
            ItemTarget.MAP_CODEC.forGetter(DeliverToVillagerObjective::item),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(DeliverToVillagerObjective::itemCount),
            Codec.BOOL.lenientOptionalFieldOf("consume", true).forGetter(DeliverToVillagerObjective::consume),
            DeliveryDestination.CODEC.lenientOptionalFieldOf("destination").forGetter(DeliverToVillagerObjective::destination)
    ).apply(instance, DeliverToVillagerObjective::new));

    /** The pre-1.4.1 shape, for callers and tests that predate {@code destination}. */
    public DeliverToVillagerObjective(VillagerTarget recipient, ItemTarget item, int itemCount,
                                      boolean consume) {
        this(recipient, item, itemCount, consume, Optional.empty());
    }

    /** True when the goods go into the recipient's inventory rather than being consumed. */
    private boolean transfers() {
        return destination.map(DeliveryDestination::isTransfer).orElse(false);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.DELIVER_TO_VILLAGER;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.deliver_to_villager", itemCount, item.describe(), recipient.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return Component.translatable("mcaquests.objective.deliver_to_villager",
                itemCount, item.describe(), recipient.describeResolved(player, active, level));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return Component.translatable("mcaquests.objective.deliver_to_villager", itemCount, item.describe(),
                ObjectiveSupport.describeLocked(recipient, player, active, progress, level));
    }

    @Override
    public VillagerTarget targetSelector() {
        return recipient;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.count() >= 1
                ? Optional.empty()
                : ObjectiveSupport.resolveLocked(recipient, player, active, progress, level);
    }

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * Credit the delivery if the interacted villager is the recipient and the player has the payload.
     *
     * <p>With a {@code destination} the goods move into that villager's inventory instead of vanishing,
     * and the move is <b>all or nothing</b>: capacity is simulated before a single item leaves the
     * player, so a recipient whose inventory is full refuses the hand-over rather than swallowing half
     * a stack. A completion marker is written before {@link ObjectiveProgress#setCount} so a replayed
     * interact packet cannot pay twice, and any remainder — which can only happen if the container
     * changed between the check and the commit — is returned to the player rather than destroyed.
     */
    public void onInteract(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           LivingEntity target, ServerLevel level) {
        if (progress.count() >= 1 || progress.extra().getBoolean(K_TRANSFERRED)
                || !ObjectiveSupport.matchesLocked(recipient, target, player, active, progress, level)) {
            return;
        }
        if (ObjectiveSupport.countMatching(player, item) < itemCount) {
            return;
        }
        if (transfers() && !handOver(player, target)) {
            return; // refused: nothing was taken, and the player is told why by the interact handler
        }
        if (consume && !transfers()) {
            ObjectiveSupport.consumeMatching(player, item, itemCount);
        }
        if (transfers()) {
            // Written before the count so a replayed packet finds the marker even if the tick that set
            // the count never finished; the two are read together at the top of this method.
            progress.extra().putBoolean(K_TRANSFERRED, true);
        }
        progress.setCount(1);
    }

    /**
     * Moves the payload from the player into the recipient's own inventory in one server-side step.
     * Returns false without touching anything when it would not fit.
     *
     * <p>The recipient is taken from the already-matched interaction target rather than re-resolved,
     * so nothing a client sends can redirect the goods to another villager.
     */
    private boolean handOver(ServerPlayer player, LivingEntity target) {
        if (!(target instanceof Villager villager)) {
            return false;
        }
        Item single = item.item().orElse(null);
        if (single == null) {
            return false; // a tag-matched payload has no single item to insert; refuse rather than guess
        }
        Container inventory = villager.getInventory();
        if (DeliveryDestination.roomFor(inventory, single, itemCount) < itemCount) {
            return false;
        }
        int taken = ObjectiveSupport.consumeMatching(player, item, itemCount);
        if (taken < itemCount) {
            return false; // lost a race with another inventory change; nothing has been inserted yet
        }
        int leftover = DeliveryDestination.insert(inventory, single, itemCount);
        if (leftover > 0) {
            // The container changed under us after roomFor said yes. Give it back rather than delete it.
            player.getInventory().placeItemBackInInventory(new ItemStack(single, leftover));
            McaQuests.LOGGER.warn("[MCA: Quests] deliver_to_villager could not fit {} x{} into {} after "
                    + "capacity was confirmed; {} returned to the player.",
                    single, itemCount, villager.getName().getString(), leftover);
        }
        return true;
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        recipient.validate("Quest '" + questId + "': objective[" + index + "] recipient", errors);
    }
}
