package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

/** Kill a number of matching entities (spec section 14). Credited by {@code QuestProgressEvents}. */
public record KillEntityObjective(EntityTarget target, int count) implements QuestObjective {

    public static final Codec<KillEntityObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityTarget.MAP_CODEC.forGetter(KillEntityObjective::target),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(KillEntityObjective::count)
    ).apply(instance, KillEntityObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.KILL_ENTITY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.kill_entity", count, target.describe());
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

    public boolean matches(Entity killed) {
        return target.matches(killed);
    }
}
