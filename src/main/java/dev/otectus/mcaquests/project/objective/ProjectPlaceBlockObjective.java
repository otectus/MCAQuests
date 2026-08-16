package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Place matching blocks near the project anchor (spec 0.4.0). Event-driven and credited by
 * {@code ProjectProgressEvents} only when the placement happens inside the project's scope.
 */
public record ProjectPlaceBlockObjective(BlockTarget target, int count) implements ProjectObjective {

    public static final Codec<ProjectPlaceBlockObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(ProjectPlaceBlockObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ProjectPlaceBlockObjective::count)
    ).apply(instance, ProjectPlaceBlockObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.PROJECT_PLACE_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.project_place_block", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    public boolean matches(BlockState placed) {
        return target.matches(placed);
    }
}
