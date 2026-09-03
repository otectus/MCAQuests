package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Offered only when the giver's zombie-infection progress is at least {@code min_progress} (v0.3.0).
 * {@code min_progress} defaults to {@code 0}, meaning "any infection in progress" ({@code > 0}).
 */
public record InfectedCondition(double minProgress) implements QuestCondition {

    // Validate on the MapCodec, never on a Codec chained off create(...): the dispatch registry
    // takes a MapCodec so the fields stay inline beside "type" rather than under a nested
    // "value" key, which optionalFieldOf would then swallow silently.
    // See DispatchedCodecInlinesTest.
    public static final MapCodec<InfectedCondition> CODEC = RecordCodecBuilder.<InfectedCondition>mapCodec(
            instance -> instance.group(
                    Codec.DOUBLE.lenientOptionalFieldOf("min_progress", 0.0D).forGetter(InfectedCondition::minProgress)
            ).apply(instance, InfectedCondition::new)).flatXmap(InfectedCondition::validate, InfectedCondition::validate);

    private static DataResult<InfectedCondition> validate(InfectedCondition condition) {
        if (condition.minProgress < 0.0D || condition.minProgress > 1.0D) {
            return DataResult.error(() -> "mcaquests:infected 'min_progress' must be in [0, 1], was " + condition.minProgress);
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.INFECTED;
    }

    @Override
    public boolean test(QuestContext context) {
        float progress = context.mca().infectionProgress();
        return progress > 0.0F && progress >= minProgress;
    }
}
