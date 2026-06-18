package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.BiomeTarget;

/** Requires the player to be in a matching biome or biome tag (spec section 13). */
public record BiomeCondition(BiomeTarget target) implements QuestCondition {

    public static final Codec<BiomeCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeTarget.MAP_CODEC.forGetter(BiomeCondition::target)
    ).apply(instance, BiomeCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.BIOME;
    }

    @Override
    public boolean test(QuestContext context) {
        return target.matches(context.level().getBiome(context.player().blockPosition()));
    }
}
