package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.QuestHistory;
import net.minecraft.resources.ResourceLocation;

/**
 * Requires another quest to have been abandoned at least once (branching chains — spec section 13).
 * With {@code scope: giver} it only counts abandons recorded against the villager being talked to.
 */
public record QuestAbandonedCondition(ResourceLocation quest, HistoryScope scope) implements QuestCondition {

    public static final Codec<QuestAbandonedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestAbandonedCondition::quest),
            HistoryScope.CODEC.lenientOptionalFieldOf("scope", HistoryScope.GLOBAL).forGetter(QuestAbandonedCondition::scope)
    ).apply(instance, QuestAbandonedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_ABANDONED;
    }

    @Override
    public boolean test(QuestContext context) {
        int count = scope == HistoryScope.GIVER
                ? context.data().history().outcomeCountByGiver(quest, context.villager().getUUID(), QuestHistory.Outcome.ABANDONED)
                : context.data().history().outcomeCount(quest, QuestHistory.Outcome.ABANDONED);
        return count > 0;
    }
}
