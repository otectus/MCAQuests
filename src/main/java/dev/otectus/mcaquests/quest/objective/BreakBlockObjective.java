package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import net.minecraft.network.chat.Component;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

/** Break a number of matching blocks (spec sections 14, 19). Only player breaks are credited. */
public record BreakBlockObjective(BlockTarget target, int count,
                                  Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape this objective had before {@code source} existed, kept so an add-on that builds
     * one in code still compiles. Adding a record component is a source break for every caller of
     * the canonical constructor, and no source hint means what it has always meant: no marker.
     */
    public BreakBlockObjective(BlockTarget target, int count) {
        this(target, count, Optional.empty());
    }

    public static final MapCodec<BreakBlockObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(BreakBlockObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(BreakBlockObjective::count),
            SourceHint.FIELD.forGetter(BreakBlockObjective::source)
    ).apply(instance, BreakBlockObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.BREAK_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.break_block", count, target.describe());
    }

    /**
     * Where the thing this asks for can actually be got, when the pack said.
     *
     * <p>Nothing is inferred. There is no index of where eight prismarine crystals are, and a guess
     * would send the player somewhere confidently wrong — which is worse than sending them nowhere,
     * because they would go. So an objective with no {@code source} draws no marker and the quest
     * text carries the whole instruction, exactly as it always did. See {@link SourceHint}.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return source.flatMap(hint -> hint.guidance(player, active, progress, level));
    }

    @Override
    public void validate(ResourceLocation questId, int index, java.util.List<String> errors) {
        source.ifPresent(hint ->
                hint.validate("Quest '" + questId + "': objective[" + index + "]", errors));
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
