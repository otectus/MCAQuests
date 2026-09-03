package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Passes with the given probability (spec section 13). The roll is stable per player/villager/day/
 * quest, so a rare quest does not flicker in and out as the menu is reopened.
 */
public record RandomChanceCondition(double chance) implements QuestCondition {

    public static final MapCodec<RandomChanceCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("chance").forGetter(RandomChanceCondition::chance)
    ).apply(instance, RandomChanceCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.RANDOM_CHANCE;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.stableRandom("random_chance").nextDouble() < chance;
    }
}
