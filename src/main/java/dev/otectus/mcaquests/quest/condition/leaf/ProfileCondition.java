package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ReputationProfileMatch;
import dev.otectus.mcaquests.compat.ReputationProfileQuery;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code mcareputation:profile} — what the village, or this one villager, can <em>say</em> about the
 * player: how widely they are known, and what they are known for (MCA: Reputation 0.6.0, §14.5).
 *
 * <pre>{@code
 * {
 *   "type": "mcareputation:profile",
 *   "scope": "giver",
 *   "recognition": { "min": 15 },
 *   "facets": {
 *     "mcareputation:reliability": { "min": 20, "min_evidence": 2 }
 *   },
 *   "allow_partial_history": false,
 *   "on_unavailable": "deny"
 * }
 * }</pre>
 *
 * <h2>Recognition is not liking</h2>
 *
 * <p>This is a different question from {@link ReputationTierCondition} (what the village thinks of
 * you) and from {@link VillagerOpinionCondition} (what this villager thinks of you). Recognition is
 * how well known you are, and an infamous murderer can be as recognised as a revered hero — so a
 * commission gated on {@code recognition.min} is asking "have they heard of you", not "do they like
 * you". What you are known <em>for</em> is the {@code facets} map.
 *
 * <h2>"Does this villager know me?"</h2>
 *
 * <p>{@code "scope": "giver"} with {@code "recognition": {"min": 1}} is that question, and it is
 * answered from the giver's own knowledge: their awareness of each deed, including the delay before
 * rumour reaches them, filtered <b>before</b> anything is aggregated. It never widens to the village
 * view — a giver who genuinely knows nothing answers "no", and a giver who cannot be resolved at all
 * answers "cannot say", which is what {@code on_unavailable} is for.
 *
 * <h2>Three-valued, with the third value authored</h2>
 *
 * <p>The condition can be unanswerable: no MCA: Reputation, profiles switched off, a save still
 * migrating, a clause that needs a complete history on a save that does not have one, an unresolvable
 * giver. None of those is a fact about the player, so none of them is silently "no".
 * {@code on_unavailable} says what the pack wants then — {@code deny} (the default, so content gated
 * on a reputation the installation cannot read simply does not offer itself) or {@code allow} (the
 * fallback for a quest that should stay reachable, e.g. because the gate is flavour rather than
 * balance). The unavailable case is logged at debug with Reputation's own reason token.
 *
 * <p>An unknown facet or recognition tier id, by contrast, is a real {@code false}: Reputation fails
 * those closed on purpose, so a datapack typo cannot open a gate.
 */
public record ProfileCondition(ReputationProfileQuery query, boolean allowWhenUnavailable)
        implements QuestCondition {

    /** {@code recognition}: an inclusive band on how widely known the player is. */
    private record Band(OptionalInt min, OptionalInt max) {

        static final Band ANY = new Band(OptionalInt.empty(), OptionalInt.empty());

        static final Codec<Band> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptional(Codec.INT, "min").forGetter(band -> box(band.min())),
                StrictCodecs.strictOptional(Codec.INT, "max").forGetter(band -> box(band.max()))
        ).apply(instance, (min, max) -> new Band(unbox(min), unbox(max))));
    }

    /** One entry of the {@code facets} map. */
    private record FacetBounds(OptionalInt min, OptionalInt max, int minEvidence,
                               boolean allowUnobserved) {

        static final Codec<FacetBounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptional(Codec.INT, "min").forGetter(b -> box(b.min())),
                StrictCodecs.strictOptional(Codec.INT, "max").forGetter(b -> box(b.max())),
                StrictCodecs.strictOptional(Codec.INT, "min_evidence", 1)
                        .forGetter(FacetBounds::minEvidence),
                StrictCodecs.strictOptional(Codec.BOOL, "allow_unobserved", false)
                        .forGetter(FacetBounds::allowUnobserved)
        ).apply(instance, (min, max, evidence, unobserved) ->
                new FacetBounds(unbox(min), unbox(max), evidence, unobserved)));
    }

    private static final Codec<ReputationProfileQuery.Scope> SCOPE_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                for (ReputationProfileQuery.Scope scope : ReputationProfileQuery.Scope.values()) {
                    if (scope.jsonName().equalsIgnoreCase(raw)) {
                        return DataResult.success(scope);
                    }
                }
                return DataResult.error(() -> "Unknown profile scope '" + raw
                        + "' (expected community or giver)");
            },
            ReputationProfileQuery.Scope::jsonName);

    /**
     * Decoded rather than stored as a boolean-shaped string, so a misspelling is a load error the
     * author sees instead of a silent "deny".
     */
    private static final Codec<Boolean> ON_UNAVAILABLE_CODEC = Codec.STRING.comapFlatMap(
            raw -> switch (raw.toLowerCase(Locale.ROOT)) {
                case "deny" -> DataResult.success(Boolean.FALSE);
                case "allow" -> DataResult.success(Boolean.TRUE);
                default -> DataResult.error(() -> "Unknown on_unavailable '" + raw
                        + "' (expected deny or allow)");
            },
            allow -> allow ? "allow" : "deny");

    public static final Codec<ProfileCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(SCOPE_CODEC, "scope",
                            ReputationProfileQuery.Scope.COMMUNITY).forGetter(c -> c.query().scope()),
                    StrictCodecs.strictOptional(Band.CODEC, "recognition", Band.ANY)
                            .forGetter(ProfileCondition::band),
                    StrictCodecs.strictOptional(Codec.STRING, "min_recognition_tier")
                            .forGetter(c -> c.query().minRecognitionTier()),
                    StrictCodecs.strictOptional(Codec.unboundedMap(ResourceLocation.CODEC,
                                    FacetBounds.CODEC), "facets", Map.of())
                            .forGetter(ProfileCondition::facetMap),
                    StrictCodecs.strictOptional(Codec.BOOL, "allow_partial_history", false)
                            .forGetter(c -> c.query().allowPartialHistory()),
                    StrictCodecs.strictOptional(ON_UNAVAILABLE_CODEC, "on_unavailable", Boolean.FALSE)
                            .forGetter(ProfileCondition::allowWhenUnavailable)
            ).apply(instance, ProfileCondition::of));

    private static ProfileCondition of(ReputationProfileQuery.Scope scope, Band recognition,
                                       Optional<String> minRecognitionTier,
                                       Map<ResourceLocation, FacetBounds> facets,
                                       boolean allowPartialHistory, boolean allowWhenUnavailable) {
        List<ReputationProfileQuery.Facet> clauses = new ArrayList<>();
        // Sorted by facet id so the same authored map always produces the same query — Reputation
        // sorts its own predicates for the same reason, and a stable order keeps a log line readable.
        facets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> clauses.add(new ReputationProfileQuery.Facet(entry.getKey(),
                        entry.getValue().min(), entry.getValue().max(),
                        entry.getValue().minEvidence(), entry.getValue().allowUnobserved())));
        return new ProfileCondition(new ReputationProfileQuery(scope, recognition.min(),
                recognition.max(), minRecognitionTier, clauses, allowPartialHistory),
                allowWhenUnavailable);
    }

    private Band band() {
        return new Band(query.minRecognition(), query.maxRecognition());
    }

    private Map<ResourceLocation, FacetBounds> facetMap() {
        Map<ResourceLocation, FacetBounds> out = new java.util.LinkedHashMap<>();
        for (ReputationProfileQuery.Facet facet : query.facets()) {
            out.put(facet.facet(), new FacetBounds(facet.min(), facet.max(), facet.minEvidence(),
                    facet.allowUnobserved()));
        }
        return out;
    }

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.PROFILE;
    }

    @Override
    public boolean test(QuestContext context) {
        if (query.isEmpty()) {
            // A condition that asks nothing would otherwise pass everything, which is not what an
            // author who wrote an empty profile block meant.
            McaQuests.LOGGER.warn("[MCA: Quests] a mcareputation:profile condition states no "
                    + "recognition or facet requirement; treating it as unmet");
            return false;
        }
        Optional<QuestReputation.Community> community = QuestReputation.resolve(context.villager());
        if (community.isEmpty()) {
            return allowWhenUnavailable; // no village: there is no profile to read, not an empty one
        }
        ReputationProfileMatch match = QuestReputation.matchesProfile(context.player().server,
                context.player().getUUID(), community.get(), context.villager(), query);
        if (!match.isAvailable()) {
            McaQuests.LOGGER.debug("[MCA: Quests] a profile condition could not be answered ({}, {}); "
                    + "using the authored on_unavailable={}", match.availability(),
                    match.reason().orElse("no reason given"), allowWhenUnavailable ? "allow" : "deny");
            return allowWhenUnavailable;
        }
        return match.matched();
    }
}
