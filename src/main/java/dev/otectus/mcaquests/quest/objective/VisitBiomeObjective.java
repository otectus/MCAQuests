package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BiomeTarget;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Enter a matching biome (spec section 14). Checked on a throttled player tick; sticky once visited. */
public record VisitBiomeObjective(BiomeTarget target) implements QuestObjective {

    public static final Codec<VisitBiomeObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeTarget.MAP_CODEC.forGetter(VisitBiomeObjective::target)
    ).apply(instance, VisitBiomeObjective::new));

    /**
     * Refuses the offer when nothing in this world is the biome named.
     *
     * <p>A mistyped biome id parses perfectly — biomes live in a datapack-driven dynamic registry that does
     * not exist at load time — and then {@code matches} answers "no" for the rest of the save. The quest
     * was offered, accepted, and never completable, with nothing anywhere saying why. Now it is simply not
     * offered, and {@code /mcaquests validate} names it.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return target.isKnown(context.level()) ? Optional.empty()
                : Optional.of(Component.translatable("mcaquests.unofferable.unknown_place", target.describe()));
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        target.validate("Quest '" + questId + "': objective[" + index + "] biome", errors);
    }

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.VISIT_BIOME;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.visit_biome", target.describe());
    }

    /**
     * The nearest position in the biome, found once and then remembered.
     *
     * <p>Same reasoning and the same throttle as {@code enter_structure}: this is
     * {@code /locatebiome}, it costs real time on the server thread, and {@code LocateCache} runs
     * it once per objective rather than once a second. Approximate, because the search samples on a
     * grid and answers with a nearby column rather than the biome's edge.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        return dev.otectus.mcaquests.quest.guidance.LocateCache
                .resolve(progress, "biome", level,
                        () -> target.locate(level, player.blockPosition(), SEARCH_BLOCKS))
                .map(pos -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(pos, level,
                        dev.otectus.mcaquests.quest.guidance.GuidanceKind.BIOME, target.describe(), ARRIVE_RADIUS, true));
    }

    /** Blocks the search may sample outward. */
    private static final int SEARCH_BLOCKS = 3200;
    /** How close counts as arrived, for fading the marker out. The poll decides completion. */
    private static final int ARRIVE_RADIUS = 48;

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
        ServerLevel level = (ServerLevel) player.level();
        return target.matches(level.getBiome(player.blockPosition()));
    }
}
