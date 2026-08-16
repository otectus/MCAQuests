package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

/** Place a number of matching blocks (spec section 14). */
public record PlaceBlockObjective(BlockTarget target, int count) implements QuestObjective {

    public static final Codec<PlaceBlockObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(PlaceBlockObjective::target),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(PlaceBlockObjective::count)
    ).apply(instance, PlaceBlockObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.PLACE_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.place_block", count, target.describe());
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

    public boolean matches(BlockState placed) {
        return target.matches(placed);
    }
}
