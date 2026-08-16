package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Requires the giver's village (independent mod-side) reputation to be within an optional min/max
 * (spec 0.4.0). Mirrors {@code HeartsCondition}. Resolves the giver's village identity the same way
 * {@code ScopeResolver} does for village scope, so it reads the same value a {@code village_reputation}
 * reward writes. Fails safe to "not met" when no village can be resolved.
 */
public record VillageReputationCondition(Optional<Integer> min, Optional<Integer> max) implements QuestCondition {

    public static final Codec<VillageReputationCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("min").forGetter(VillageReputationCondition::min),
            Codec.INT.optionalFieldOf("max").forGetter(VillageReputationCondition::max)
    ).apply(instance, VillageReputationCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.VILLAGE_REPUTATION;
    }

    @Override
    public boolean test(QuestContext context) {
        // Reads THIS player's standing with the giver's village through the bridge, so the value a
        // condition tests is the value a reward wrote — whether MCA: Reputation owns it or Quests does.
        Optional<QuestReputation.Community> community = QuestReputation.resolve(context.villager());
        if (community.isEmpty()) {
            return false; // no village resolves: fail safe to "not met", exactly as before
        }
        int rep = QuestReputation.score(context.player(), community.get());
        return min.map(m -> rep >= m).orElse(true) && max.map(m -> rep <= m).orElse(true);
    }
}
