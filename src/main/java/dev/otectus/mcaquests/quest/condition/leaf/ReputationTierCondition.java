package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Requires the player's reputation with the giver's village to be at least {@code min_tier} (and at most
 * an optional {@code max_tier}) on a tier ladder (default {@code mcaquests:default}). Mirrors how
 * {@link VillageReputationCondition} resolves the giver's village so it reads the same value a
 * {@code village_reputation} reward writes. Fails safe to "not met" when no village resolves, when the
 * ladder lacks the named tier, or when reputation tiers are disabled (spec 0.7.0).
 */
public record ReputationTierCondition(String minTier, Optional<String> maxTier,
                                      Optional<ResourceLocation> ladder) implements QuestCondition {

    public static final Codec<ReputationTierCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("min_tier").forGetter(ReputationTierCondition::minTier),
            Codec.STRING.lenientOptionalFieldOf("max_tier").forGetter(ReputationTierCondition::maxTier),
            ResourceLocation.CODEC.lenientOptionalFieldOf("ladder").forGetter(ReputationTierCondition::ladder)
    ).apply(instance, ReputationTierCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.REPUTATION_TIER;
    }

    @Override
    public boolean test(QuestContext context) {
        if (!McaQuestsConfig.COMMON.enableReputationTiers.get()) {
            return false;
        }
        Optional<QuestReputation.Community> community = QuestReputation.resolve(context.villager());
        if (community.isEmpty()) {
            return false;
        }
        ReputationTierSet set = ladder.map(ReputationTiers::getOrDefault).orElseGet(ReputationTiers::getDefault);
        int minIndex = set.indexOf(minTier);
        if (minIndex < 0) {
            return false; // unknown tier id — fail safe
        }
        int rep = QuestReputation.score(context.player(), community.get());
        int currentIndex = set.indexOf(set.tierFor(rep).id());
        if (currentIndex < minIndex) {
            return false;
        }
        if (maxTier.isPresent()) {
            int maxIndex = set.indexOf(maxTier.get());
            if (maxIndex >= 0 && currentIndex > maxIndex) {
                return false;
            }
        }
        return true;
    }
}
