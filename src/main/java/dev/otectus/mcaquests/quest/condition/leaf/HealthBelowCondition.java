package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Offered only when the giver's health fraction (current / max) is below {@code threshold} (v0.3.0).
 * {@code threshold} is in {@code (0, 1]}.
 */
public record HealthBelowCondition(double threshold) implements QuestCondition {

    public static final Codec<HealthBelowCondition> CODEC = RecordCodecBuilder.<HealthBelowCondition>create(
            instance -> instance.group(
                    Codec.DOUBLE.fieldOf("threshold").forGetter(HealthBelowCondition::threshold)
            ).apply(instance, HealthBelowCondition::new)).flatXmap(HealthBelowCondition::validate, HealthBelowCondition::validate);

    private static DataResult<HealthBelowCondition> validate(HealthBelowCondition condition) {
        if (condition.threshold <= 0.0D || condition.threshold > 1.0D) {
            return DataResult.error(() -> "mcaquests:health_below 'threshold' must be in (0, 1], was " + condition.threshold);
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.HEALTH_BELOW;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().healthFraction().stream().anyMatch(fraction -> fraction < threshold);
    }
}
