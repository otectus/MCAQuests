package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Enter a specified dimension (spec section 14). Checked on a throttled player tick; sticky once visited. */
public record VisitDimensionObjective(ResourceLocation dimension) implements QuestObjective {

    public static final Codec<VisitDimensionObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(VisitDimensionObjective::dimension)
    ).apply(instance, VisitDimensionObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.VISIT_DIMENSION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.visit_dimension", DisplayNames.name(dimension));
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

    public boolean matches(ServerPlayer player) {
        return player.level().dimension().location().equals(dimension);
    }
}
