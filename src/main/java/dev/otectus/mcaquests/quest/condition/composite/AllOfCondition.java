package dev.otectus.mcaquests.quest.condition.composite;

import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import javax.annotation.Nullable;
import java.util.List;

/** Composite: true when every child condition is true (spec section 13). */
public record AllOfCondition(List<QuestCondition> conditions) implements QuestCondition {

    @Nullable
    @Override
    public QuestConditionType<?> type() {
        return null; // composites are handled directly by ConditionTypes.CODEC
    }

    @Override
    public boolean test(QuestContext context) {
        return conditions.stream().allMatch(c -> c.test(context));
    }
}
