package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
 * Breed a number of a configured animal, optionally near a {@link LocationAnchor}. Credited on
 * {@code BabyEntitySpawnEvent} for the player who caused the breeding.
 */
public record BreedAnimalsObjective(EntityTarget animal, Optional<LocationAnchor> near,
                                    int radius, int count) implements QuestObjective {

    public static final MapCodec<BreedAnimalsObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.MAP_CODEC.fieldOf("animal").forGetter(BreedAnimalsObjective::animal),
            LocationAnchor.CODEC.optionalFieldOf("near").forGetter(BreedAnimalsObjective::near),
            Codec.intRange(1, 256).optionalFieldOf("radius", 32).forGetter(BreedAnimalsObjective::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(BreedAnimalsObjective::count)
    ).apply(instance, BreedAnimalsObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.BREED_ANIMALS;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.breed_animals", count, animal.describe());
    }

    /**
     * The place this has to happen, when the objective names one. Bred animals count
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

    /** Credit a breeding if the child matches and (when {@code near} is set) it is within range. */
    public void onBreed(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                        Entity child, ServerLevel level) {
        if (progress.count() >= count || !animal.matches(child)) {
            return;
        }
        if (near.isPresent()) {
            Optional<BlockPos> anchor = near.get().resolve(player, active, level);
            if (anchor.isEmpty() || !ObjectiveSupport.withinRadius(child, anchor.get(), radius)) {
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
