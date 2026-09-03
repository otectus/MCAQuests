package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Offered only when the giver has at least one relative of {@code relation}
 * ({@code spouse}/{@code parent}/{@code child}/{@code sibling}) whose {@code status}
 * ({@code alive}/{@code nearby}/{@code missing}/{@code dead}/{@code same_village}) matches (v0.3.0).
 */
public record RelatedVillagerStatusCondition(String relation, String status) implements QuestCondition {

    public static final MapCodec<RelatedVillagerStatusCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            McaConditionCodecs.validated("related relation", McaConditionCodecs.RELATED_RELATIONS)
                    .fieldOf("relation").forGetter(RelatedVillagerStatusCondition::relation),
            McaConditionCodecs.validated("related status", McaConditionCodecs.RELATED_STATUSES)
                    .fieldOf("status").forGetter(RelatedVillagerStatusCondition::status)
    ).apply(instance, RelatedVillagerStatusCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.RELATED_VILLAGER_STATUS;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().relativesWithStatus(relation, status);
    }
}
