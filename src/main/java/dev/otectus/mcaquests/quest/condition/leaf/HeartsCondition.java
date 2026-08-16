package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;

/** Requires the player's hearts with the villager to be within an optional min/max (spec section 13). */
public record HeartsCondition(Optional<Integer> min, Optional<Integer> max) implements QuestCondition {

    public static final Codec<HeartsCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.lenientOptionalFieldOf("min").forGetter(HeartsCondition::min),
            Codec.INT.lenientOptionalFieldOf("max").forGetter(HeartsCondition::max)
    ).apply(instance, HeartsCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.HEARTS;
    }

    @Override
    public boolean test(QuestContext context) {
        int hearts = context.hearts();
        return min.map(m -> hearts >= m).orElse(true) && max.map(m -> hearts <= m).orElse(true);
    }
}
