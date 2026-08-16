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
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Defeat a number of hostile threats near a fixed {@link LocationAnchor} (a gate, well, village center, …)
 * — the place-anchored sibling of {@code defend_villager}. Credited on {@code LivingDeathEvent} when the
 * player kills a hostile matching {@code threat} within {@code radius} of the resolved anchor. An
 * unresolved anchor (unloaded giver/village) pauses crediting rather than failing.
 */
public record DefendLocationObjective(LocationAnchor location, EntityTarget threat,
                                      int radius, int count) implements QuestObjective {

    public static final Codec<DefendLocationObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LocationAnchor.MAP_CODEC.fieldOf("location").forGetter(DefendLocationObjective::location),
            EntityTarget.MAP_CODEC.fieldOf("threat").forGetter(DefendLocationObjective::threat),
            Codec.intRange(1, 64).optionalFieldOf("radius", 16).forGetter(DefendLocationObjective::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 5).forGetter(DefendLocationObjective::count)
    ).apply(instance, DefendLocationObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.DEFEND_LOCATION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.defend_location", count, threat.describe(), location.describe());
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

    /** Credit a kill if {@code dead} is a matching hostile within range of the resolved anchor. */
    public void onKill(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                       LivingEntity dead, ServerLevel level) {
        if (progress.count() >= count || !ObjectiveSupport.isHostile(dead) || !threat.matches(dead)) {
            return;
        }
        Optional<BlockPos> anchor = location.resolve(player, active, level);
        if (anchor.isPresent() && ObjectiveSupport.withinRadius(dead, anchor.get(), radius)) {
            progress.add(1);
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        location.validate("Quest '" + questId + "': objective[" + index + "] location", errors);
    }
}
