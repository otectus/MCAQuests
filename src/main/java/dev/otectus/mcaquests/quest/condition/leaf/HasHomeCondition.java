package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Offered based on whether the giver has an assigned home position (v0.3.0). {@code value} defaults
 * to {@code true} (require a home); set it {@code false} to require a homeless villager.
 */
public record HasHomeCondition(boolean value) implements QuestCondition {

    public static final Codec<HasHomeCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("value", true).forGetter(HasHomeCondition::value)
    ).apply(instance, HasHomeCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.HAS_HOME;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().hasHome() == value;
    }
}
