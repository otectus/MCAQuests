package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.List;

/**
 * Offered only when the giver's MCA age state is one of {@code groups} (v0.3.0). MCA has no
 * {@code elder} state, so it is not an accepted value.
 */
public record AgeGroupCondition(List<String> groups) implements QuestCondition {

    public static final Codec<AgeGroupCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            McaConditionCodecs.validatedNonEmptyList("age group", McaConditionCodecs.AGE_GROUPS)
                    .fieldOf("groups").forGetter(AgeGroupCondition::groups)
    ).apply(instance, AgeGroupCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.AGE_GROUP;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().ageGroup().map(groups::contains).orElse(false);
    }
}
