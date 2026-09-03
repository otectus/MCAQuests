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
 * Requires another quest to have been declined at least once (1.4.3).
 *
 * <p>Lets a pack write "if they turned this down, offer the softer version instead" — the whole reason
 * declining is recorded as its own outcome rather than simply not happening. With {@code scope: giver} it
 * only counts refusals given to the villager being talked to, which is usually what a relationship arc
 * means.
 *
 * <p>Declining costs the player nothing, so branching on it should not read as punishment: it is a
 * preference, not a failure.
 */
public record QuestDeclinedCondition(ResourceLocation quest, HistoryScope scope) implements QuestCondition {

    public static final MapCodec<QuestDeclinedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestDeclinedCondition::quest),
            HistoryScope.CODEC.lenientOptionalFieldOf("scope", HistoryScope.GLOBAL).forGetter(QuestDeclinedCondition::scope)
    ).apply(instance, QuestDeclinedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_DECLINED;
    }

    @Override
    public boolean test(QuestContext context) {
        int count = scope == HistoryScope.GIVER
                ? context.data().history().outcomeCountByGiver(quest, context.villager().getUUID(), QuestHistory.Outcome.DECLINED)
                : context.data().history().outcomeCount(quest, QuestHistory.Outcome.DECLINED);
        return count > 0;
    }
}
