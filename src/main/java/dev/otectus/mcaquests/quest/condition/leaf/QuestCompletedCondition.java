package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

/** Requires another quest to have been completed at least once (prerequisite chains — spec section 13). */
public record QuestCompletedCondition(ResourceLocation quest) implements QuestCondition {

    public static final Codec<QuestCompletedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestCompletedCondition::quest)
    ).apply(instance, QuestCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.data().history().completionCount(quest) > 0;
    }
}
