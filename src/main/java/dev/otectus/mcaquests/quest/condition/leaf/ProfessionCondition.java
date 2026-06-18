package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Requires the giver villager to have one of the listed professions (spec section 13). */
public record ProfessionCondition(List<ResourceLocation> professions) implements QuestCondition {

    public static final Codec<ProfessionCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("professions").forGetter(ProfessionCondition::professions)
    ).apply(instance, ProfessionCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.PROFESSION;
    }

    @Override
    public boolean test(QuestContext context) {
        ResourceLocation actual = context.profession().orElse(null);
        return ProfessionMatcher.matchesAny(professions, actual);
    }
}
