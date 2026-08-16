package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import net.minecraft.network.chat.Component;
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

    public static final Codec<EnterStructureObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StructureTarget.MAP_CODEC.forGetter(EnterStructureObjective::structure)
    ).apply(instance, EnterStructureObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.ENTER_STRUCTURE;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.enter_structure", structure.describe());
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
