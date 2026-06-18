package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;

/** Requires the player's favor with the villager to be within an optional min/max (spec section 13). */
public record FavorCondition(Optional<Integer> min, Optional<Integer> max) implements QuestCondition {

    public static final Codec<FavorCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("min").forGetter(FavorCondition::min),
            Codec.INT.optionalFieldOf("max").forGetter(FavorCondition::max)
    ).apply(instance, FavorCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.FAVOR;
    }

    @Override
    public boolean test(QuestContext context) {
        int favor = context.favor();
        return min.map(m -> favor >= m).orElse(true) && max.map(m -> favor <= m).orElse(true);
    }
}
