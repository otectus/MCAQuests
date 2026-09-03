package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

/** Requires the player to be in a given dimension (spec section 13). */
public record DimensionCondition(ResourceLocation dimension) implements QuestCondition {

    public static final MapCodec<DimensionCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(DimensionCondition::dimension)
    ).apply(instance, DimensionCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.DIMENSION;
    }

    @Override
    public boolean test(QuestContext context) {
        return context.level().dimension().location().equals(dimension);
    }
}
