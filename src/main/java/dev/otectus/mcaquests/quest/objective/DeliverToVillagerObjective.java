package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * Credited when the player right-clicks the resolved recipient (main hand) while carrying enough of
 * the item; the item is consumed at the hand-off (when {@code consume}) — unlike {@code item_delivery}
 * which consumes at turn-in. Once delivered it is a sticky flag (the item is already gone).
 */
public record DeliverToVillagerObjective(VillagerTarget recipient, ItemTarget item,
                                         int itemCount, boolean consume) implements QuestObjective, VillagerTargeted {

    public static final Codec<DeliverToVillagerObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.MAP_CODEC.fieldOf("recipient").forGetter(DeliverToVillagerObjective::recipient),
            ItemTarget.MAP_CODEC.forGetter(DeliverToVillagerObjective::item),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(DeliverToVillagerObjective::itemCount),
            Codec.BOOL.optionalFieldOf("consume", true).forGetter(DeliverToVillagerObjective::consume)
    ).apply(instance, DeliverToVillagerObjective::new));

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

    /** Credit the delivery if the interacted villager is the recipient and the player has the payload. */
    public void onInteract(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           LivingEntity target, ServerLevel level) {
        if (progress.count() >= 1
                || !ObjectiveSupport.matchesLocked(recipient, target, player, active, progress, level)) {
            return;
        }
        if (ObjectiveSupport.countMatching(player, item) < itemCount) {
            return;
        }
        if (consume) {
            ObjectiveSupport.consumeMatching(player, item, itemCount);
        }
        progress.setCount(1);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        recipient.validate("Quest '" + questId + "': objective[" + index + "] recipient", errors);
    }
}
