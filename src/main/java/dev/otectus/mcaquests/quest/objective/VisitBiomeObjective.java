package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BiomeTarget;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;
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
