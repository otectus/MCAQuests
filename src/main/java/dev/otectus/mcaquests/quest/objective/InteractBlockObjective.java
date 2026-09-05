package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Right-click a block a number of times. Credited by {@code QuestProgressEvents} from a server-side
 * block use that was not denied.
 *
 * <p>Generic on purpose, even though the content that needs it today is Bountiful's bounty board:
 * "go and use that thing over there" is an ordinary shape for a quest and nothing about it is
 * specific to one mod. The interaction itself is only ever <em>observed</em> — the objective never
 * cancels it and never changes its result, so the board still opens and its screen is still
 * Bountiful's.
 *
 * <p>The block is held as a plain {@link ResourceLocation} rather than a {@code BlockTarget}, for the
 * reason {@code UseItemObjective} holds its item that way: a strict block codec would fail the whole
 * definition on an installation without the mod, and a quest a player already holds would lose its
 * title along with its progress. Kept as written, the objective reports itself unavailable instead
 * and comes back when the mod does. A {@code tag} is strict by contrast, because a tag that names
 * nothing still parses and simply matches nothing.
 *
 * @param block exactly one of this and {@code tag}
 * @param tag   exactly one of this and {@code block}
 */
public record InteractBlockObjective(Optional<ResourceLocation> block, Optional<TagKey<Block>> tag,
                                     int count, Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape an add-on is most likely to build in code: one block, one interaction, no marker.
     * Kept as a constructor for the reason every other objective keeps one — adding a record
     * component is a source break for every caller of the canonical constructor.
     */
    public InteractBlockObjective(ResourceLocation block, int count) {
        this(Optional.of(block), Optional.empty(), count, Optional.empty());
    }

    /**
     * Validated at parse time rather than at evaluation, because "neither" and "both" are pack bugs
     * with no sensible runtime behaviour: neither would match nothing forever, and both would leave
     * the pack author guessing which one won.
     */
    public static final MapCodec<InteractBlockObjective> CODEC =
            RecordCodecBuilder.<InteractBlockObjective>mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.lenientOptionalFieldOf("block").forGetter(InteractBlockObjective::block),
                    TagKey.codec(Registries.BLOCK).lenientOptionalFieldOf("tag").forGetter(InteractBlockObjective::tag),
                    ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("count", 1).forGetter(InteractBlockObjective::count),
                    SourceHint.FIELD.forGetter(InteractBlockObjective::source)
            ).apply(instance, InteractBlockObjective::new))
                    // A MapCodec all the way through: the objective dispatch codec inlines a map
                    // codec's fields into the objective object, and a Codec built with create() would
                    // instead expect them nested -- which optionalFieldOf swallows, so every quest
                    // using this type would silently fail to parse. DispatchedCodecInlinesTest.
                    .flatXmap(InteractBlockObjective::validateTarget, InteractBlockObjective::validateTarget);

    private static DataResult<InteractBlockObjective> validateTarget(InteractBlockObjective objective) {
        if (objective.block.isPresent() == objective.tag.isPresent()) {
            return DataResult.error(() -> "mcaquests:interact_block needs exactly one of \"block\" or "
                    + "\"tag\"");
        }
        return DataResult.success(objective);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.INTERACT_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.interact_block", blockName());
    }

    /**
     * With progress in hand a multi-use objective says how far along it is; "use the board" asked for
     * three times over is otherwise indistinguishable from having done nothing.
     */
    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        if (count <= 1) {
            return describe();
        }
        return Component.translatable("mcaquests.objective.interact_block.count", blockName(),
                current(player, progress), count);
    }

    /** Where the block can be found, when the pack said. Nothing is inferred; see {@link SourceHint}. */
    @Override
    public Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                             ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        return source.flatMap(hint -> hint.guidance(player, active, progress, level));
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        source.ifPresent(hint ->
                hint.validate("Quest '" + questId + "': objective[" + index + "]", errors));
    }

    /**
     * A block this world has never heard of cannot be used, so the quest is never offered — and a copy
     * already accepted suspends rather than fails, keeping its count for the day the mod that owns the
     * block comes back.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return missing();
    }

    @Override
    public Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                 ObjectiveProgress progress, ServerLevel level) {
        return missing();
    }

    private Optional<Component> missing() {
        // A tag objective is never "missing": an empty tag is a legitimate state that simply matches
        // nothing, and there is no mod to name as the reason.
        return block.filter(id -> resolved().isEmpty())
                .map(id -> CompatRegistry.get().describeMissing(id));
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

    @Override
    public ItemStack icon() {
        return resolved().map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** True when the block that was used is the one this asks for. */
    public boolean matches(BlockState state) {
        if (tag.isPresent()) {
            return state.is(tag.get());
        }
        return resolved().map(state::is).orElse(false);
    }

    /**
     * The registered block, if this world has one. Asked with {@code getOptional} rather than
     * {@code BuiltInRegistries.BLOCK.get}, because the block registry is defaulted: an unknown id
     * there answers with air, which would read as "registered" and quietly match every empty space in
     * the world.
     */
    private Optional<Block> resolved() {
        return block.flatMap(BuiltInRegistries.BLOCK::getOptional);
    }

    /** The block's own name when this world has it, and the "unavailable" line when it does not. */
    private Component blockName() {
        if (tag.isPresent()) {
            return DisplayNames.tagName(tag.get().location());
        }
        return resolved().<Component>map(Block::getName)
                .orElseGet(() -> Component.translatable("mcaquests.target.unavailable",
                        block.map(ResourceLocation::toString).orElse("")));
    }
}
