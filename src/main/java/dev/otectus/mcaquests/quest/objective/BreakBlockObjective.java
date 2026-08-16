package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

/** Break a number of matching blocks (spec sections 14, 19). Only player breaks are credited. */
public record BreakBlockObjective(BlockTarget target, int count) implements QuestObjective {

    public static final Codec<BreakBlockObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(BreakBlockObjective::target),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(BreakBlockObjective::count)
    ).apply(instance, BreakBlockObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.BREAK_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.break_block", count, target.describe());
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

    public boolean matches(BlockState state) {
        return target.matches(state);
    }
}
