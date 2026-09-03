package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Enter a configured structure (id or tag). Polled once per second; sticky once the player stands in
 * a generated piece of the structure. Structures are a dynamic registry, so validity is best-effort —
 * an unknown structure simply never matches (see {@link StructureTarget}).
 */
public record EnterStructureObjective(StructureTarget structure) implements QuestObjective {

    public static final MapCodec<EnterStructureObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StructureTarget.MAP_CODEC.forGetter(EnterStructureObjective::structure)
    ).apply(instance, EnterStructureObjective::new));

    /**
     * Refuses the offer when this world's registries have never heard of the structure named.
     *
     * <p>{@code StructureTarget.matches} deliberately swallows an unknown id and answers "not inside",
     * which is right at runtime and fatal at offer time: the quest can never be finished and says nothing
     * about it. See {@code VisitBiomeObjective} for the same reasoning.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return structure.isKnown(context.level()) ? Optional.empty()
                : Optional.of(Component.translatable("mcaquests.unofferable.unknown_place",
                        structure.describe()));
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.ENTER_STRUCTURE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.enter_structure", structure.describe());
    }

    /**
     * The nearest generated instance of the structure, found once and then remembered.
     *
     * <p>"Enter an ancient city" is an instruction a player cannot act on without a map and some
     * luck, and the mod used to give them the sentence and nothing else. The search is vanilla's own
     * {@code /locate}, which is far too expensive to run on a once-a-second guidance pass, so it
     * goes through {@code LocateCache}: once per objective, remembered across restarts, and retried
     * only after {@code guidanceSearchIntervalTicks} if the first attempt found nothing in range.
     *
     * <p>Marked approximate, because the answer is the structure's origin chunk rather than its door.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return dev.otectus.mcaquests.quest.guidance.LocateCache
                .resolve(progress, "structure", level,
                        () -> structure.locate(level, player.blockPosition(), SEARCH_CHUNKS))
                .map(pos -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(pos, level,
                        dev.otectus.mcaquests.quest.guidance.GuidanceKind.STRUCTURE, structure.describe(), ARRIVE_RADIUS, true));
    }

    /** Chunks the search may walk. Vanilla's own {@code /locate} reach. */
    private static final int SEARCH_CHUNKS = 100;
    /** How close counts as arrived, for fading the marker out. The poll decides completion. */
    private static final int ARRIVE_RADIUS = 32;

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

    /** Per-second poll: stick complete once the player is inside the structure. */
    public void poll(ServerPlayer player, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() == 0 && structure.matches(level, player.blockPosition())) {
            progress.setCount(1);
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        structure.validate("Quest '" + questId + "': objective[" + index + "]", errors);
    }
}
