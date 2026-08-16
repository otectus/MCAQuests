package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BiomeTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Enter a matching biome (spec section 14). Checked on a throttled player tick; sticky once visited. */
public record VisitBiomeObjective(BiomeTarget target) implements QuestObjective {

    public static final Codec<VisitBiomeObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeTarget.MAP_CODEC.forGetter(VisitBiomeObjective::target)
    ).apply(instance, VisitBiomeObjective::new));

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
