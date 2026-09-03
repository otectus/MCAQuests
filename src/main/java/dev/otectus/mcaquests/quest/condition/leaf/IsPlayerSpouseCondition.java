package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/** Offered only when the giver is married to the interacting player (v0.3.0). No fields. */
public record IsPlayerSpouseCondition() implements QuestCondition {

    public static final MapCodec<IsPlayerSpouseCondition> CODEC = MapCodec.unit(IsPlayerSpouseCondition::new);

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.IS_PLAYER_SPOUSE;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().isPlayerSpouse();
    }
}
