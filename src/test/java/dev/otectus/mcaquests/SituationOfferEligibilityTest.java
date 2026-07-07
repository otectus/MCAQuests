package dev.otectus.mcaquests;

import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.situation.DynamicOfferSource;
import dev.otectus.mcaquests.quest.situation.SituationScope;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for situation offer eligibility: scope match + giver gate (0.8.0). */
class SituationOfferEligibilityTest {

    private static final UUID VILLAGER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();

    @Test
    void villageScopeMatchesAnyResident() {
        assertTrue(DynamicOfferSource.scopeMatches(SituationScope.VILLAGE, null, null, VILLAGER, null));
        assertTrue(DynamicOfferSource.scopeMatches(SituationScope.VILLAGE, OTHER, FAMILY, VILLAGER, null));
    }

    @Test
    void villagerScopeMatchesOnlyTheFocalVillager() {
        assertTrue(DynamicOfferSource.scopeMatches(SituationScope.VILLAGER, VILLAGER, null, VILLAGER, null));
        assertFalse(DynamicOfferSource.scopeMatches(SituationScope.VILLAGER, OTHER, null, VILLAGER, null));
        assertFalse(DynamicOfferSource.scopeMatches(SituationScope.VILLAGER, null, null, VILLAGER, null));
    }

    @Test
    void familyScopeMatchesSameLineage() {
        assertTrue(DynamicOfferSource.scopeMatches(SituationScope.FAMILY, null, FAMILY, VILLAGER, FAMILY));
        assertFalse(DynamicOfferSource.scopeMatches(SituationScope.FAMILY, null, FAMILY, VILLAGER, OTHER));
        assertFalse(DynamicOfferSource.scopeMatches(SituationScope.FAMILY, null, FAMILY, VILLAGER, null));
        assertFalse(DynamicOfferSource.scopeMatches(SituationScope.FAMILY, null, null, VILLAGER, FAMILY));
    }

    @Test
    void genericGiverAcceptsAnyAdultInHeartsRange() {
        GiverSpec generic = GiverSpec.ANY; // adultOnly=true, unbounded hearts
        assertTrue(DynamicOfferSource.giverEligible(generic, null, true, 0, ProfessionMatchingMode.NORMALIZED));
        assertFalse(DynamicOfferSource.giverEligible(generic, null, false, 0, ProfessionMatchingMode.NORMALIZED));
    }

    @Test
    void heartsBoundsAreEnforced() {
        GiverSpec needsHearts = new GiverSpec(List.of(), false, 10, 100);
        assertFalse(DynamicOfferSource.giverEligible(needsHearts, null, true, 5, ProfessionMatchingMode.NORMALIZED));
        assertTrue(DynamicOfferSource.giverEligible(needsHearts, null, true, 10, ProfessionMatchingMode.NORMALIZED));
        assertFalse(DynamicOfferSource.giverEligible(needsHearts, null, true, 101, ProfessionMatchingMode.NORMALIZED));
    }

    @Test
    void professionListGatesNonGenericGivers() {
        GiverSpec cleric = new GiverSpec(List.of(new ResourceLocation("minecraft:cleric")), false,
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertTrue(DynamicOfferSource.giverEligible(cleric, new ResourceLocation("minecraft:cleric"),
                true, 0, ProfessionMatchingMode.NORMALIZED));
        assertFalse(DynamicOfferSource.giverEligible(cleric, new ResourceLocation("minecraft:farmer"),
                true, 0, ProfessionMatchingMode.NORMALIZED));
        assertFalse(DynamicOfferSource.giverEligible(cleric, null, true, 0, ProfessionMatchingMode.NORMALIZED));
    }
}
