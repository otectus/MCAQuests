package dev.otectus.mcaquests;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.ftbq.QuestFilter;
import dev.otectus.mcaquests.quest.ChainSpec;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
        // Constructing a QuestDefinition triggers its <clinit>, which builds its DFU codec; that
        // reaches ObjectiveTypes -> ItemDeliveryObjective -> BuiltInRegistries.ITEM.byNameCodec(),
        // and vanilla's MappedRegistry asserts Bootstrap.bootStrap() has run before allowing that.
        // We don't launch a game (or even want to — the real Bootstrap.bootStrap() call in this Forge
        // dev environment reaches net.minecraftforge.network.NetworkHooks.init(), which needs an
        // actual running Forge instance and NPEs here). All that's really needed for building codecs
        // is the "bootstrapped" flag itself, so flip it directly instead of running the heavy method.
        SharedConstants.tryDetectVersion(); // needed by DataFixers.<clinit>, reached via EntityType/Items
        try {
            Field isBootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
            isBootstrapped.setAccessible(true);
            isBootstrapped.set(null, true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final ResourceLocation FARMER_WHEAT = new ResourceLocation("mcaquests", "farmer_wheat_request");
    private static final ResourceLocation LIBRARIAN_BOOK = new ResourceLocation("mcaquests", "librarian_book_request");
    private static final ResourceLocation OTHER_NS_QUEST = new ResourceLocation("somepack", "custom_quest");

    private static QuestDefinition definition(ResourceLocation id, List<ResourceLocation> professions,
                                               Optional<String> category, Optional<ChainSpec> chain) {
        GiverSpec giver = new GiverSpec(professions, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return new QuestDefinition(id, true, 1, category, Optional.empty(), RepeatRule.DEFAULT, giver,
                Map.of(), List.of(), List.of(), TurnInSpec.DEFAULT, Optional.empty(), chain, Optional.empty(),
                Optional.empty(), OfferShaping.NONE);
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
    void syntheticSituationIdResolvesToOfferDefinitionForMatching() {
        ResourceLocation syntheticId = new ResourceLocation("mcaquests", "situation_offer_1234");
        ResourceLocation offerDefinitionId = new ResourceLocation("mcaquests", "barn_raising_offer");
        QuestDefinition offerDefinition = definition(offerDefinitionId,
                List.of(new ResourceLocation("minecraft", "farmer")), Optional.empty(), Optional.empty());
        Function<ResourceLocation, Optional<QuestDefinition>> stubResolver =
                id -> id.equals(syntheticId) ? Optional.of(offerDefinition) : Optional.empty();

        QuestFilter filter = new QuestFilter("mcaquests:barn_raising_offer", "", "", "");
        assertTrue(filter.matches(syntheticId, stubResolver, ProfessionMatchingMode.STRICT),
                "the id pattern should match the resolved OFFER definition's id, not the synthetic history id");
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
