package dev.otectus.mcaquests.quest.condition.composite;

import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import javax.annotation.Nullable;

/** Composite: inverts a child condition (spec section 13). */
public record NotCondition(QuestCondition condition) implements QuestCondition {

    @Nullable
    @Override
    public QuestConditionType<?> type() {
        return null;
    }

    @Override
    public boolean test(QuestContext context) {
        return !condition.test(context);
    }
}
