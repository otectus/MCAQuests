package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import net.minecraft.network.chat.Component;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/** Fish up a number of matching items (spec section 14). */
public record FishItemObjective(ItemTarget target, int count,
                                Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape this objective had before {@code source} existed, kept so an add-on that builds
     * one in code still compiles. Adding a record component is a source break for every caller of
     * the canonical constructor, and no source hint means what it has always meant: no marker.
     */
    public FishItemObjective(ItemTarget target, int count) {
        this(target, count, Optional.empty());
    }

    public static final MapCodec<FishItemObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemTarget.MAP_CODEC.forGetter(FishItemObjective::target),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(FishItemObjective::count),
            SourceHint.FIELD.forGetter(FishItemObjective::source)
    ).apply(instance, FishItemObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.FISH_ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.fish_item", count, target.describe());
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

    public boolean matches(ItemStack caught) {
        return target.matches(caught);
    }

    @Override
    public ItemStack icon() {
        return target.icon();
    }

}
