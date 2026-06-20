package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.List;

/** Offered only when the giver's MCA relationship state is one of {@code states} (v0.3.0). */
public record RelationshipStateCondition(List<String> states) implements QuestCondition {

    public static final Codec<RelationshipStateCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            McaConditionCodecs.validatedNonEmptyList("relationship state", McaConditionCodecs.RELATIONSHIP_STATES)
                    .fieldOf("states").forGetter(RelationshipStateCondition::states)
    ).apply(instance, RelationshipStateCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.RELATIONSHIP_STATE;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().relationshipState().map(states::contains).orElse(false);
    }
}
