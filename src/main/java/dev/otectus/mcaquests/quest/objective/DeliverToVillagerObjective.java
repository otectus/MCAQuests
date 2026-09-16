package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.delivery.DeliveryLedger;
import dev.otectus.mcaquests.quest.delivery.DeliveryService;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Hand a payload to a specific other villager (a family member, profession, named/UUID target).
 *
 * <p>Credited by an explicit hand-in: the Deliver action on the quest card, MCA's own Gift gesture, or
 * — when a server opts back into it — the legacy right-click. The goods are consumed or transferred at
 * that hand-off, unlike {@code item_delivery}, which pays at turn-in; a committed unit is never
 * charged again.
 *
 * <p>Progress is <b>units, not a flag</b>. {@link #required()} is the item count, and
 * {@link DeliveryLedger} holds how many of them have actually been handed over, so two crossbows read
 * 0/2, 1/2, 2/2 instead of a single boolean-ish tick that a partial hand-in had nowhere to live in.
 * The old count is still written when the obligation is fully paid, for anything reading it, but it is
 * no longer the ledger.
 */
public record DeliverToVillagerObjective(VillagerTarget recipient, ItemTarget item,
                                         int itemCount, boolean consume,
                                         Optional<DeliveryDestination> destination)
        implements QuestObjective, VillagerTargeted {

    public static final Codec<DeliverToVillagerObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.MAP_CODEC.fieldOf("recipient").forGetter(DeliverToVillagerObjective::recipient),
            ItemTarget.MAP_CODEC.forGetter(DeliverToVillagerObjective::item),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(DeliverToVillagerObjective::itemCount),
            StrictCodecs.strictOptional(Codec.BOOL, "consume", true).forGetter(DeliverToVillagerObjective::consume),
            StrictCodecs.strictOptional(DeliveryDestination.CODEC, "destination").forGetter(DeliverToVillagerObjective::destination)
    ).apply(instance, DeliverToVillagerObjective::new));

    /** The pre-1.4.1 shape, for callers and tests that predate {@code destination}. */
    public DeliverToVillagerObjective(VillagerTarget recipient, ItemTarget item, int itemCount,
                                      boolean consume) {
        this(recipient, item, itemCount, consume, Optional.empty());
    }

    /** True when the goods go into the recipient's own inventory rather than being consumed. */
    public boolean transfers() {
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
        // A partly-paid delivery still wants the player to go back, so the highlight follows the
        // ledger rather than the old "any credit at all" flag.
        return isSatisfied(player, progress)
                ? Optional.empty()
                : ObjectiveSupport.resolveLocked(recipient, player, active, progress, level);
    }

    /**
     * The item count, not one.
     *
     * <p>This was {@code 1} for as long as the objective existed, which is why "deliver two crossbows"
     * showed as 0/1 and a single hand-over of both items read as one unit of progress. Datapack- and
     * API-visible: anything that displayed {@code current/required} now shows items.
     */
    @Override
    public int required() {
        return itemCount;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        if (isProof()) {
            // Nothing is taken, so there is nothing to count but what the player is carrying -- until
            // they have shown it, which is sticky.
            return DeliveryLedger.proofAcknowledged(progress) || progress.count() >= 1
                    ? itemCount
                    : Math.min(ObjectiveSupport.countMatching(player, item), itemCount);
        }
        return Math.min(DeliveryLedger.units(this, progress), itemCount);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        if (isProof()) {
            return DeliveryLedger.migrateProof(progress, DeliveryLedger.fingerprintOf(this),
                    DeliveryLedger.legacySatisfied(this, progress));
        }
        return DeliveryLedger.units(this, progress) >= itemCount;
    }

    /** True when this objective asks to be shown the goods rather than given them (spec section 5). */
    private boolean isProof() {
        return DeliveryLedger.isProofOnly(this);
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * The legacy right-click hand-off, kept for the servers that want it and for add-ons that call it.
     *
     * <p>Now a thin call into {@link DeliveryService}, which owns recipient rules, the slot policy, the
     * transaction and the ledger — so this path can no longer take goods a different way from the
     * Deliver button or from Gift, and can no longer refuse in silence: the player is told why.
     *
     * <p>Reached only when {@code legacyInteractDelivery} is on. It is off by default because the
     * interaction it listens to is also the click that opens a conversation, so it could take a
     * delivery payload from a player who meant to talk.
     */
    public void onInteract(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           LivingEntity target, ServerLevel level) {
        DeliveryService.legacyInteract(player, active, this, progress, target);
    }

    /** How many units of this delivery the player has actually handed over. */
    public int deliveredUnits(ObjectiveProgress progress) {
        return DeliveryLedger.units(this, progress);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        recipient.validate("Quest '" + questId + "': objective[" + index + "] recipient", errors);
    }
}
