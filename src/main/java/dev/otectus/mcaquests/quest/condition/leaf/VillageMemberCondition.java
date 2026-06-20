package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;

/**
 * Offered based on whether the giver belongs to a home village (v0.3.0). {@code value} defaults to
 * {@code true} (require village membership); set it {@code false} to require a villager with none.
 */
public record VillageMemberCondition(boolean value) implements QuestCondition {

    public static final Codec<VillageMemberCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("value", true).forGetter(VillageMemberCondition::value)
    ).apply(instance, VillageMemberCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.VILLAGE_MEMBER;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.mca().hasHomeVillage() == value;
    }
}
