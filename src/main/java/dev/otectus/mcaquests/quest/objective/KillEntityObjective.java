package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import net.minecraft.network.chat.Component;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

/** Kill a number of matching entities (spec section 14). Credited by {@code QuestProgressEvents}. */
public record KillEntityObjective(EntityTarget target, int count,
                                  Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape this objective had before {@code source} existed, kept so an add-on that builds
     * one in code still compiles. Adding a record component is a source break for every caller of
     * the canonical constructor, and no source hint means what it has always meant: no marker.
     */
    public KillEntityObjective(EntityTarget target, int count) {
        this(target, count, Optional.empty());
    }

    public static final Codec<KillEntityObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityTarget.MAP_CODEC.forGetter(KillEntityObjective::target),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(KillEntityObjective::count),
            SourceHint.FIELD.forGetter(KillEntityObjective::source)
    ).apply(instance, KillEntityObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.KILL_ENTITY;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.kill_entity", count, target.describe());
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

    /**
     * A creature this world has never heard of cannot be hunted, so the quest is never offered.
     *
     * <p>Since 1.5.4 an unknown entity id survives the parse ({@link EntityTarget}), which is what
     * makes this check necessary and what makes it enough: the definition loads, the objective says
     * plainly which mod's content is missing, and the pair of answers below keeps such a quest out of
     * the offer pool while suspending — never failing — any copy already accepted.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return target.unresolved().map(id -> CompatRegistry.get().describeMissing(id));
    }

    @Override
    public Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                 ObjectiveProgress progress, ServerLevel level) {
        return target.unresolved().map(id -> CompatRegistry.get().describeMissing(id));
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
