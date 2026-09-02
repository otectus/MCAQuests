package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Tame a number of a configured animal, optionally near a {@link LocationAnchor}. Credited on
 * {@code AnimalTameEvent} for the taming player.
 */
public record TameAnimalObjective(EntityTarget animal, Optional<LocationAnchor> near,
                                  int radius, int count) implements QuestObjective {

    public static final Codec<TameAnimalObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityTarget.MAP_CODEC.fieldOf("animal").forGetter(TameAnimalObjective::animal),
            LocationAnchor.CODEC.optionalFieldOf("near").forGetter(TameAnimalObjective::near),
            Codec.intRange(1, 256).optionalFieldOf("radius", 48).forGetter(TameAnimalObjective::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(TameAnimalObjective::count)
    ).apply(instance, TameAnimalObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.TAME_ANIMAL;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.tame_animal", count, animal.describe());
    }

    /**
     * The place this has to happen, when the objective names one. Tamed animals count
     * anywhere if {@code near} is absent, and in that case there is nothing to mark and nothing is
     * marked — a pin on the village square would imply a restriction the objective does not have.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return near.flatMap(anchor ->
                ObjectiveSupport.anchorGuidance(anchor, player, active, level, radius));
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

    /** Credit a taming if the animal matches and (when {@code near} is set) it is within range. */
    public void onTame(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                       Entity animal, ServerLevel level) {
        if (progress.count() >= count || !this.animal.matches(animal)) {
            return;
        }
        if (near.isPresent()) {
            Optional<BlockPos> anchor = near.get().resolve(player, active, level);
            if (anchor.isEmpty() || !ObjectiveSupport.withinRadius(animal, anchor.get(), radius)) {
                return;
            }
        }
        progress.add(1);
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        near.ifPresent(a -> a.validate("Quest '" + questId + "': objective[" + index + "] near", errors));
    }
}
