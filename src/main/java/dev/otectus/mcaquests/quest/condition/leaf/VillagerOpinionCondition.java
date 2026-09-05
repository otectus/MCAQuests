package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.compat.VillagerOpinionView;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code mcareputation:villager_opinion} — requires <em>this giver's own</em> view of the player to
 * sit in a tier band, and optionally to rest on how they came by it.
 *
 * <pre>{@code
 * {
 *   "type": "mcareputation:villager_opinion",
 *   "min_tier": "friend",
 *   "max_tier": "revered",
 *   "basis": ["involved", "witnessed"]
 * }
 * }</pre>
 *
 * <p>The village-wide {@link ReputationTierCondition} asks what everybody here thinks; this asks what
 * the villager in front of you thinks, which is a different and usually more interesting question. A
 * villager who was <em>involved</em> — the deed happened to them — will not be talked round by a good
 * reputation elsewhere, and one who only has it on {@code hearsay} can be. {@code basis} is where a
 * pack says which of those it wants: a single string or a list, any of {@code involved},
 * {@code witnessed}, {@code hearsay}, {@code none}.
 *
 * <p>Tiers are ordered on the default Quests ladder, the same one {@link ReputationTierCondition}
 * uses, so {@code min_tier}/{@code max_tier} mean here exactly what they mean there.
 *
 * <h2>Without MCA: Reputation</h2>
 *
 * <p>Nothing tracks what an individual villager saw, so the condition is never met and the quest never
 * offers itself — the same honest degradation {@link HasIncidentCondition} makes, and for the same
 * reason: answering "yes" to a question nobody can answer would have villagers reacting to things they
 * never witnessed. The same applies to an older MCA: Reputation without the opinion API. The type is
 * registered either way, so a datapack using it parses cleanly on any install.
 */
public record VillagerOpinionCondition(Optional<String> minTier, Optional<String> maxTier,
                                       List<String> basis) implements QuestCondition {

    /** A single string is the common case; a list is the general one. Both mean the same thing. */
    private static final Codec<List<String>> BASIS_CODEC = Codec
            .either(Codec.STRING, Codec.STRING.listOf())
            .xmap(either -> either.map(List::of, list -> list), Either::right);

    public static final Codec<VillagerOpinionCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.optionalFieldOf("min_tier").forGetter(VillagerOpinionCondition::minTier),
                    Codec.STRING.optionalFieldOf("max_tier").forGetter(VillagerOpinionCondition::maxTier),
                    BASIS_CODEC.optionalFieldOf("basis", List.of())
                            .forGetter(VillagerOpinionCondition::basis)
            ).apply(instance, VillagerOpinionCondition::new));

    public VillagerOpinionCondition {
        minTier = minTier == null ? Optional.empty() : minTier;
        maxTier = maxTier == null ? Optional.empty() : maxTier;
        basis = basis == null ? List.of() : basis.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.VILLAGER_OPINION;
    }

    @Override
    public boolean test(QuestContext context) {
        Entity villager = context.villager();
        if (villager == null) {
            return false; // no giver: nobody to hold an opinion
        }
        Optional<QuestReputation.Community> community = QuestReputation.resolve(villager);
        if (community.isEmpty()) {
            return false;
        }
        Optional<VillagerOpinionView> opinion;
        try {
            opinion = ReputationBridge.backend().villagerOpinion(context.player().server,
                    context.player().getUUID(), villager.getUUID(), community.get().dimension(),
                    community.get().villageId());
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] villagerOpinion failed; the condition is not met", t);
            return false;
        }
        if (opinion.isEmpty()) {
            return false;
        }
        if (!basis.isEmpty() && !basis.contains(opinion.get().basis())) {
            return false;
        }
        return withinBand(opinion.get().tierId());
    }

    private boolean withinBand(String tierId) {
        if (minTier.isEmpty() && maxTier.isEmpty()) {
            return true;
        }
        ReputationTierSet ladder = ReputationTiers.getDefault();
        int current = ladder.indexOf(tierId);
        if (current < 0) {
            return false; // an opinion tier this ladder does not name — fail safe
        }
        if (minTier.isPresent()) {
            int min = ladder.indexOf(minTier.get());
            if (min < 0 || current < min) {
                return false; // unknown tier id — fail safe
            }
        }
        if (maxTier.isPresent()) {
            int max = ladder.indexOf(maxTier.get());
            if (max < 0 || current > max) {
                return false;
            }
        }
        return true;
    }
}
