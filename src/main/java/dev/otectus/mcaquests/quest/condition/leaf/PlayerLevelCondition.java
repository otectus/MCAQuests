package dev.otectus.mcaquests.quest.condition.leaf;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.Optional;

/** Requires the player's XP level within an optional min/max (spec section 13). */
public record PlayerLevelCondition(Optional<Integer> min, Optional<Integer> max) implements QuestCondition {

    public static final Codec<PlayerLevelCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.strictOptional(Codec.INT, "min").forGetter(PlayerLevelCondition::min),
            StrictCodecs.strictOptional(Codec.INT, "max").forGetter(PlayerLevelCondition::max)
    ).apply(instance, PlayerLevelCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.PLAYER_LEVEL;
    }

    @Override
    public boolean test(QuestContext context) {
        int level = context.player().experienceLevel;
        return min.map(m -> level >= m).orElse(true) && max.map(m -> level <= m).orElse(true);
    }
}
