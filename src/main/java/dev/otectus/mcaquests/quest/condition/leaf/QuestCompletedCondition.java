package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Requires another quest to have been completed at least once (prerequisite chains — spec section 13).
 * With {@code scope: giver} it only counts completions turned in to the villager being talked to, so a
 * per-villager arc gates on what was done with <em>this</em> villager.
 */
public record QuestCompletedCondition(ResourceLocation quest, HistoryScope scope) implements QuestCondition {

    public static final MapCodec<QuestCompletedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestCompletedCondition::quest),
            HistoryScope.CODEC.optionalFieldOf("scope", HistoryScope.GLOBAL).forGetter(QuestCompletedCondition::scope)
    ).apply(instance, QuestCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        int count = scope == HistoryScope.GIVER
                ? context.data().history().completionCountByGiver(quest, context.villager().getUUID())
                : context.data().history().completionCount(quest);
        return count > 0;
    }
}
