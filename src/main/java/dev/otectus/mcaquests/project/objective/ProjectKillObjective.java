package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.data.StrictCodecs;


import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

/**
 * Kill matching entities within the project's village (spec 0.4.0). Event-driven and credited by
 * {@code ProjectProgressEvents} only when the kill happens inside the project's scope (village
 * border / anchor radius). {@code border_margin} widens a village border exactly as it does for
 * {@link ProjectPlaceBlockObjective}: raiders are fought at the edge of a village, not among its beds.
 */
public record ProjectKillObjective(EntityTarget target, int count, int borderMargin) implements ProjectObjective {

    /** The widest allowance a definition may ask for: MCA's own villager margin is 48. */
    public static final int MAX_BORDER_MARGIN = 64;

    /** The pre-1.7.0 shape: no allowance beyond the village's building box. */
    public ProjectKillObjective(EntityTarget target, int count) {
        this(target, count, 0);
    }

    public static final MapCodec<ProjectKillObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityTarget.MAP_CODEC.forGetter(ProjectKillObjective::target),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(ProjectKillObjective::count),
            // How far outside the village's registered buildings this work still counts (1.7.0).
            StrictCodecs.strictOptional(Codec.intRange(0, MAX_BORDER_MARGIN), "border_margin", 0)
                    .forGetter(ProjectKillObjective::borderMargin)
    ).apply(instance, ProjectKillObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.PROJECT_KILL_ENTITY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.project_kill_entity", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    public boolean matches(Entity killed) {
        return target.matches(killed);
    }

    @Override
    public java.util.List<Component> explain(ProjectObjectiveContext context) {
        return java.util.List.of(
                Component.translatable("mcaquests.project.help.kill.counts", target.describe()),
                context.villageBound()
                        ? Component.translatable("mcaquests.project.help.area.village", context.villageName(), borderMargin)
                        : Component.translatable("mcaquests.project.help.area.anchor"));
    }
}
