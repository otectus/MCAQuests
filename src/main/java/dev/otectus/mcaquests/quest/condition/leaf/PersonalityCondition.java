package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.McaConditionCodecs;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

import java.util.List;

/** Offered only when the giver's MCA personality is one of {@code personalities} (v0.3.0). */
public record PersonalityCondition(List<String> personalities) implements QuestCondition {

    public static final MapCodec<PersonalityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            McaConditionCodecs.validatedNonEmptyList("personality", McaConditionCodecs.PERSONALITIES)
                    .fieldOf("personalities").forGetter(PersonalityCondition::personalities)
    ).apply(instance, PersonalityCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.PERSONALITY;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().personality().map(personalities::contains).orElse(false);
    }
}
