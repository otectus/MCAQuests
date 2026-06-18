package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

/** Requires another quest to NOT have been completed (prevents repeats/branches — spec section 13). */
public record QuestNotCompletedCondition(ResourceLocation quest) implements QuestCondition {

    public static final Codec<QuestNotCompletedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("quest").forGetter(QuestNotCompletedCondition::quest)
    ).apply(instance, QuestNotCompletedCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.QUEST_NOT_COMPLETED;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.data().history().completionCount(quest) == 0;
    }
}
