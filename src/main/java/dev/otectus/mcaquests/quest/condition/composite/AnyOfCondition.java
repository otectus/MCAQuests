package dev.otectus.mcaquests.quest.condition.composite;

import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import javax.annotation.Nullable;
import java.util.List;

/** Composite: true when at least one child condition is true (spec section 13). */
public record AnyOfCondition(List<QuestCondition> conditions) implements QuestCondition {

    @Nullable
    @Override
    public QuestConditionType<?> type() {
        return null;
    }

    @Override
    public boolean test(QuestContext context) {
        return conditions.stream().anyMatch(c -> c.test(context));
    }
}
