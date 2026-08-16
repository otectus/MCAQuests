package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

/**
 * Kill matching entities within the project's village (spec 0.4.0). Event-driven and credited by
 * {@code ProjectProgressEvents} only when the kill happens inside the project's scope (village
 * border / anchor radius).
 */
public record ProjectKillObjective(EntityTarget target, int count) implements ProjectObjective {

    public static final Codec<ProjectKillObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityTarget.MAP_CODEC.forGetter(ProjectKillObjective::target),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(ProjectKillObjective::count)
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
}
