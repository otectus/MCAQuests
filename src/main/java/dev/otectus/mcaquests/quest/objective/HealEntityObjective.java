package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Tend to a hurt villager by using a remedy item on them. Credited when the player right-clicks the
 * resolved villager (main hand) holding an item matching {@code item} while the villager's health is
 * below {@code below_health_fraction}. The item is consumed when {@code consume} is set.
 */
public record HealEntityObjective(VillagerTarget villager, ItemTarget item,
                                  double belowHealthFraction, int count, boolean consume) implements QuestObjective, VillagerTargeted {

    public static final Codec<HealEntityObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.CODEC.optionalFieldOf("villager", VillagerTarget.SELF).forGetter(HealEntityObjective::villager),
            ItemTarget.MAP_CODEC.forGetter(HealEntityObjective::item),
            Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("below_health_fraction", 1.0D)
                    .forGetter(HealEntityObjective::belowHealthFraction),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(HealEntityObjective::count),
            Codec.BOOL.optionalFieldOf("consume", false).forGetter(HealEntityObjective::consume)
    ).apply(instance, HealEntityObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.HEAL_ENTITY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.heal_entity", villager.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return Component.translatable("mcaquests.objective.heal_entity", villager.describeResolved(player, active, level));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return Component.translatable("mcaquests.objective.heal_entity",
                ObjectiveSupport.describeLocked(villager, player, active, progress, level));
    }

    @Override
    public VillagerTarget targetSelector() {
        return villager;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.count() >= count
                ? Optional.empty()
                : ObjectiveSupport.resolveLocked(villager, player, active, progress, level);
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /** Credit a heal if the interacted {@code target} is the villager, hurt, and the held item matches. */
    public void onInteract(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           LivingEntity target, ItemStack held, ServerLevel level) {
        if (progress.count() >= count || !item.matches(held)) {
            return;
        }
        if (!ObjectiveSupport.matchesLocked(villager, target, player, active, progress, level)) {
            return;
        }
        double health = McaCompat.getHealthFraction(target).orElse(1.0D);
        if (health > belowHealthFraction) {
            return;
        }
        if (consume) {
            ObjectiveSupport.consumeMatching(player, item, 1);
        }
        progress.add(1);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        villager.validate("Quest '" + questId + "': objective[" + index + "] villager", errors);
    }
}
