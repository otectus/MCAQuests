package dev.otectus.mcaquests;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.ftbq.QuestFilter;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link QuestFilter} (spec §29.1 #2 / §15.0-15.1). Deliberately lives in
 * {@code dev.otectus.mcaquests} (flat, matching this repo's test layout) and imports nothing from
 * {@code dev.ftb.mods}, proving {@code QuestFilter} itself is FTB-free despite its package.
 */
class QuestFilterTest {

    static {
        // Constructing real QuestDefinitions requires the vanilla "bootstrapped" state; see the
        // helper's Javadoc for why the real Bootstrap.bootStrap() cannot be used here.
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation FARMER_WHEAT = new ResourceLocation("mcaquests", "farmer_wheat_request");
    private static final ResourceLocation LIBRARIAN_BOOK = new ResourceLocation("mcaquests", "librarian_book_request");
    private static final ResourceLocation OTHER_NS_QUEST = new ResourceLocation("somepack", "custom_quest");

    private static QuestDefinition definition(ResourceLocation id, List<ResourceLocation> professions,
                                               Optional<String> category, Optional<ChainSpec> chain) {
        GiverSpec giver = new GiverSpec(professions, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new QuestDefinition(id, true, 1, category, Optional.empty(), RepeatRule.DEFAULT, giver,
                Map.of(), List.of(), List.of(), TurnInSpec.DEFAULT, Optional.empty(), chain, Optional.empty(),
                Optional.empty(), OfferShaping.NONE, dev.otectus.mcaquests.quest.reputation.QuestReputationBlock.NONE);
    }

    private static ChainSpec chain(String chainId, int stage, Optional<Integer> stageTotal) {
        return new ChainSpec(chainId, stage, stageTotal, Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    private static Function<ResourceLocation, Optional<QuestDefinition>> resolverOf(QuestDefinition... defs) {
        Map<ResourceLocation, QuestDefinition> byId = new java.util.HashMap<>();
        for (QuestDefinition def : defs) {
            byId.put(def.id(), def);
        }
        return id -> Optional.ofNullable(byId.get(id));
    }

    // --- questIdPattern ---

    @Test
    void exactIdMatches() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("mcaquests:farmer_wheat_request", "", "", "");
        assertTrue(filter.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void exactIdDoesNotMatchDifferentId() {
        QuestDefinition def = definition(LIBRARIAN_BOOK, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("mcaquests:farmer_wheat_request", "", "", "");
        assertFalse(filter.matches(LIBRARIAN_BOOK, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void namespaceWildcardMatchesAnyIdInNamespace() {
        QuestDefinition def = definition(LIBRARIAN_BOOK, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("mcaquests:*", "", "", "");
        assertTrue(filter.matches(LIBRARIAN_BOOK, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void namespaceWildcardDoesNotMatchOtherNamespace() {
        QuestDefinition def = definition(OTHER_NS_QUEST, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("mcaquests:*", "", "", "");
        assertFalse(filter.matches(OTHER_NS_QUEST, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void emptyPatternMatchesAnyResolvedDefinition() {
        QuestDefinition def = definition(OTHER_NS_QUEST, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("", "", "", "");
        assertTrue(filter.matches(OTHER_NS_QUEST, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void malformedPatternsFailClosedWithoutThrowing() {
        // Author typos in the FTBQ editor must never match anything (and never throw): a bare "*"
        // is compared as an exact id (ids always contain ':'), ":*" yields the empty namespace, and
        // "ns:foo:*" yields "ns:foo" as the namespace — none can equal a real definition's namespace.
        QuestDefinition def = definition(FARMER_WHEAT, List.of(), Optional.empty(), Optional.empty());
        for (String malformed : List.of("*", ":*", "mcaquests:foo:*")) {
            assertFalse(new QuestFilter(malformed, "", "", "")
                            .matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT),
                    "pattern '" + malformed + "' should fail closed");
        }
    }

    // --- profession (via ProfessionMatcher modes) ---

    @Test
    void professionStrictRequiresExactMatch() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(new ResourceLocation("minecraft", "farmer")),
                Optional.empty(), Optional.empty());
        QuestFilter sameNamespace = new QuestFilter("", "minecraft:farmer", "", "");
        QuestFilter differentNamespace = new QuestFilter("", "mca:farmer", "", "");
        assertTrue(sameNamespace.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
        assertFalse(differentNamespace.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void professionNormalizedMatchesSamePathDifferentNamespace() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(new ResourceLocation("minecraft", "farmer")),
                Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("", "mca:farmer", "", "");
        assertTrue(filter.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.NORMALIZED));
    }

    @Test
    void professionEmptyMeansAny() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(new ResourceLocation("minecraft", "farmer")),
                Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("", "", "", "");
        assertTrue(filter.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void professionFilterMatchesGenericGiverRegardlessOfMode() {
        QuestDefinition genericGiver = definition(FARMER_WHEAT, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("", "minecraft:farmer", "", "");
        assertTrue(filter.matches(FARMER_WHEAT, resolverOf(genericGiver), ProfessionMatchingMode.STRICT));
    }

    // --- chain / category ---

    @Test
    void chainIdMatchesChainSpecChain() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(), Optional.empty(),
                Optional.of(chain("family_arc", 1, Optional.of(3))));
        QuestFilter matching = new QuestFilter("", "", "family_arc", "");
        QuestFilter nonMatching = new QuestFilter("", "", "other_arc", "");
        assertTrue(matching.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
        assertFalse(nonMatching.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void chainIdFilterFailsWhenQuestHasNoChain() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(), Optional.empty(), Optional.empty());
        QuestFilter filter = new QuestFilter("", "", "family_arc", "");
        assertFalse(filter.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    @Test
    void categoryMatchesExactCategory() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(), Optional.of("farming"), Optional.empty());
        QuestFilter matching = new QuestFilter("", "", "", "farming");
        QuestFilter nonMatching = new QuestFilter("", "", "", "combat");
        assertTrue(matching.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
        assertFalse(nonMatching.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    // --- AND composition ---

    @Test
    void filtersComposeWithAnd() {
        QuestDefinition def = definition(FARMER_WHEAT, List.of(new ResourceLocation("minecraft", "farmer")),
                Optional.of("farming"), Optional.of(chain("family_arc", 1, Optional.of(3))));
        QuestFilter allMatch = new QuestFilter("mcaquests:farmer_wheat_request", "minecraft:farmer", "family_arc", "farming");
        assertTrue(allMatch.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));

        QuestFilter oneWrong = new QuestFilter("mcaquests:farmer_wheat_request", "minecraft:farmer", "family_arc", "combat");
        assertFalse(oneWrong.matches(FARMER_WHEAT, resolverOf(def), ProfessionMatchingMode.STRICT));
    }

    // --- synthetic-situation resolution ---

    @Test
    void syntheticSituationIdIsFilteredByItsResolvedOfferDefinitionAttributes() {
        // Mirrors production: SituationDefinition.toOfferQuestDefinition() builds the offer
        // definition with id = syntheticId(), so the resolved id equals the completed history id by
        // construction. The interesting behavior is that profession/chain/category are read from the
        // RESOLVED OFFER DEFINITION — a situation completion is filterable by its offer's attributes
        // even though the raw history entry carries none of them.
        ResourceLocation syntheticId = new ResourceLocation("mcaquests", "situation/mcaquests/barn_raising");
        QuestDefinition offerDefinition = definition(syntheticId,
                List.of(new ResourceLocation("minecraft", "farmer")), Optional.of("village_life"),
                Optional.of(chain("barn_arc", 1, Optional.empty())));
        Function<ResourceLocation, Optional<QuestDefinition>> stubResolver =
                id -> id.equals(syntheticId) ? Optional.of(offerDefinition) : Optional.empty();

        QuestFilter byAttributes = new QuestFilter("", "minecraft:farmer", "barn_arc", "village_life");
        assertTrue(byAttributes.matches(syntheticId, stubResolver, ProfessionMatchingMode.STRICT),
                "profession/chain/category must come from the resolved offer definition");

        QuestFilter wrongProfession = new QuestFilter("", "minecraft:cleric", "", "");
        assertFalse(wrongProfession.matches(syntheticId, stubResolver, ProfessionMatchingMode.STRICT));

        QuestFilter byExactId = new QuestFilter(syntheticId.toString(), "", "", "");
        assertTrue(byExactId.matches(syntheticId, stubResolver, ProfessionMatchingMode.STRICT),
                "the synthetic id itself is targetable since raw and resolved ids coincide");
    }

    // --- unresolvable definitions ---

    @Test
    void unresolvableEntryMatchesOnlyTheAnyPattern() {
        ResourceLocation goneId = new ResourceLocation("mcaquests", "removed_quest");
        Function<ResourceLocation, Optional<QuestDefinition>> emptyResolver = id -> Optional.empty();

        QuestFilter any = new QuestFilter("", "", "", "");
        assertTrue(any.matches(goneId, emptyResolver, ProfessionMatchingMode.STRICT));
    }

    @Test
    void unresolvableEntryDoesNotMatchNonAnyFilters() {
        ResourceLocation goneId = new ResourceLocation("mcaquests", "removed_quest");
        Function<ResourceLocation, Optional<QuestDefinition>> emptyResolver = id -> Optional.empty();

        assertFalse(new QuestFilter("mcaquests:removed_quest", "", "", "")
                .matches(goneId, emptyResolver, ProfessionMatchingMode.STRICT));
        assertFalse(new QuestFilter("", "minecraft:farmer", "", "")
                .matches(goneId, emptyResolver, ProfessionMatchingMode.STRICT));
        assertFalse(new QuestFilter("", "", "family_arc", "")
                .matches(goneId, emptyResolver, ProfessionMatchingMode.STRICT));
        assertFalse(new QuestFilter("", "", "", "farming")
                .matches(goneId, emptyResolver, ProfessionMatchingMode.STRICT));
    }
}
