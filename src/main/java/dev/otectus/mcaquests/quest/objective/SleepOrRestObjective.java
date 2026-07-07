package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rest by sleeping through to morning. Credited on Forge's player wake/sleep-finished events when the
 * player slept long enough. (The "ensure a villager reaches their bed" variant is not implemented;
 * MCA does not expose villager sleep state reliably — see DATAPACK.md.)
 */
public record SleepOrRestObjective(boolean requireMorning) implements QuestObjective {

    public static final Codec<SleepOrRestObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("require_morning", true).forGetter(SleepOrRestObjective::requireMorning)
    ).apply(instance, SleepOrRestObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.SLEEP_OR_REST;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.sleep_or_rest");
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
}
