package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.compat.FtbqIds;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Reads whether an FTB Quests task has been completed by the player's team (spec §17). Registered
 * unconditionally — zero FTB imports; all real work happens behind {@link FtbqBridge#Holder}.
 *
 * <pre>{ "type": "mcaquests:ftbq_task_completed", "task": "F00DF00DF00DF00D", "when_missing": "met" }</pre>
 *
 * <p>{@code when_missing} semantics mirror {@link FtbqQuestCompletedCondition}: it is the fallback
 * whenever FTB Quests' state cannot be authoritatively consulted — absent/disabled integration, a
 * thrown bridge failure, or an id that doesn't resolve to a task in the loaded book. Because
 * {@link FtbqBridge#isTaskCompleted} alone cannot distinguish "task exists, incomplete" from "no such
 * task" (both {@code false}), {@link FtbqBridge#taskIdExists} disambiguates first: only "available AND
 * exists" consults the real completion state.
 */
public record FtbqTaskCompletedCondition(String task, FtbqWhenMissing whenMissing) implements QuestCondition {

    private static final String TYPE_ID = "mcaquests:ftbq_task_completed";

    public static final MapCodec<FtbqTaskCompletedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FtbqIds.hexIdCodec(TYPE_ID, "task").fieldOf("task").forGetter(FtbqTaskCompletedCondition::task),
            FtbqWhenMissing.CODEC.optionalFieldOf("when_missing", FtbqWhenMissing.NOT_MET)
                    .forGetter(FtbqTaskCompletedCondition::whenMissing)
    ).apply(instance, FtbqTaskCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.FTBQ_TASK_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        try {
            FtbqBridge bridge = FtbqBridge.Holder.get();
            if (bridge.isAvailable() && bridge.taskIdExists(task)) {
                return bridge.isTaskCompleted(context.player(), task);
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] {} evaluation failed for id '{}'; falling back to when_missing",
                    TYPE_ID, task, t);
        }
        return whenMissing.asBoolean();
    }
}
