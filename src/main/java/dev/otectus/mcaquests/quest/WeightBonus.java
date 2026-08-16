package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * A conditional bonus added to a quest's selection {@link QuestDefinition#weight() weight} when its
 * {@code when} condition holds for the player/villager being offered the quest (spec section 18). Lets a
 * datapack make a chain stage more likely as a relationship deepens (MCA {@code hearts}), as earlier
 * stages are completed ({@code quest_completed}, including {@code scope: giver}), or on any other
 * condition — without Java. {@code amount} may be negative to make a quest rarer.
 */
public record WeightBonus(QuestCondition when, int amount) {

    public static final Codec<WeightBonus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ConditionTypes.CODEC.fieldOf("when").forGetter(WeightBonus::when),
            Codec.INT.fieldOf("amount").forGetter(WeightBonus::amount)
    ).apply(instance, WeightBonus::new));

    /** This bonus's {@code amount} when its condition holds in {@code context}, otherwise 0. */
    public int evaluate(QuestContext context) {
        return when.test(context) ? amount : 0;
    }
}
