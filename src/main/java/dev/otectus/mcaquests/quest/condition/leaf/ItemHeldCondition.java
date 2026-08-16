package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.ItemTarget;

/** Requires the player to hold a matching item in either hand (spec section 13). */
public record ItemHeldCondition(ItemTarget target) implements QuestCondition {

    public static final Codec<ItemHeldCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemTarget.MAP_CODEC.forGetter(ItemHeldCondition::target)
    ).apply(instance, ItemHeldCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.ITEM_HELD;
    }

    @Override
    public boolean test(QuestContext context) {
        return target.matches(context.player().getMainHandItem())
                || target.matches(context.player().getOffhandItem());
    }
}
