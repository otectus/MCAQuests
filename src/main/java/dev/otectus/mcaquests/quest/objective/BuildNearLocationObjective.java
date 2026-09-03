package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Place a number of matching blocks within {@code radius} of a {@link LocationAnchor} (a village
 * well, the giver's home, fixed coords, …). Credited on {@code BlockEvent.EntityPlaceEvent}. Each
 * unique position counts once (tracked in {@link ObjectiveProgress#addVisited}), so breaking and
 * re-placing a block never re-credits.
 */
public record BuildNearLocationObjective(BlockTarget block, LocationAnchor location,
                                         int radius, int count) implements QuestObjective {

    public static final MapCodec<BuildNearLocationObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(BuildNearLocationObjective::block),
            LocationAnchor.MAP_CODEC.fieldOf("location").forGetter(BuildNearLocationObjective::location),
            Codec.intRange(1, 64).lenientOptionalFieldOf("radius", 8).forGetter(BuildNearLocationObjective::radius),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 8).forGetter(BuildNearLocationObjective::count)
    ).apply(instance, BuildNearLocationObjective::new));

    /** Never offered when this objective's destination is a place the giver does not have. */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return location.unofferableReason(context);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.BUILD_NEAR_LOCATION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.build_near_location", count, block.describe(), location.describe());
    }

    /**
     * Where the building has to happen. Blocks placed anywhere else do not count, so a marker here is
     * the difference between the objective being clear and being a guessing game.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return ObjectiveSupport.anchorGuidance(location, player, active, level, radius);
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

    /** Credit a placement if the block matches, it is in range of the anchor, and the pos is new. */
    public void onPlace(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                        BlockState placed, BlockPos pos, ServerLevel level) {
        if (progress.count() >= count || !block.matches(placed)) {
            return;
        }
        Optional<BlockPos> anchor = location.resolve(player, active, level);
        if (anchor.isEmpty()) {
            return; // site unresolvable this tick — do not credit
        }
        double dx = anchor.get().getX() - pos.getX();
        double dy = anchor.get().getY() - pos.getY();
        double dz = anchor.get().getZ() - pos.getZ();
        if ((dx * dx + dy * dy + dz * dz) > radius * (double) radius) {
            return;
        }
        if (progress.addVisited(pos)) {
            progress.setCount(progress.visitedCount());
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        location.validate("Quest '" + questId + "': objective[" + index + "] location", errors);
    }
}
