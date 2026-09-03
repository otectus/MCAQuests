package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.resources.ResourceLocation;

/**
 * Requires another quest to have failed at least once (branching chains — spec section 13). With
 * {@code scope: giver} it only counts failures recorded against the villager being talked to.
 */
public record QuestFailedCondition(ResourceLocation quest, HistoryScope scope) implements QuestCondition {

    public static final MapCodec<QuestFailedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestFailedCondition::quest),
            HistoryScope.CODEC.optionalFieldOf("scope", HistoryScope.GLOBAL).forGetter(QuestFailedCondition::scope)
    ).apply(instance, QuestFailedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_FAILED;
    }

    @Override
    public boolean test(QuestContext context) {
        int count = scope == HistoryScope.GIVER
                ? context.data().history().outcomeCountByGiver(quest, context.villager().getUUID(), QuestHistory.Outcome.FAILED)
                : context.data().history().outcomeCount(quest, QuestHistory.Outcome.FAILED);
        return count > 0;
    }
}
