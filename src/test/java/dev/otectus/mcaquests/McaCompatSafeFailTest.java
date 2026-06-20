package dev.otectus.mcaquests;

import dev.otectus.mcaquests.compat.McaCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 safe-fail demonstration (spec): every new {@link McaCompat} data-point method must return
 * its documented safe default when the backing MCA data is missing — here exercised with a
 * non-MCA/absent entity. No game launch or MCA villager instance is required; the {@code instanceof}
 * and {@code EntityRelationship.of(...)} guards short-circuit before any MCA object is dereferenced.
 */
class McaCompatSafeFailTest {

    @Test
    void booleanDataPointsDefaultFalseOnMissingData() {
        assertFalse(McaCompat.isPlayerSpouse(null, null), "spouse");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "any"), "family:any");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "child"), "family:child");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "grandparent"), "family:grandparent");
        assertFalse(McaCompat.hasHomeVillage(null), "homeVillage");
        assertFalse(McaCompat.hasHome(null), "home");
        assertFalse(McaCompat.relativesWithStatus(null, null, "child", "missing"), "relativeStatus");
    }

    @Test
    void optionalDataPointsDefaultEmptyOnMissingData() {
        assertTrue(McaCompat.getRelationshipState(null).isEmpty(), "relationshipState");
        assertTrue(McaCompat.getAgeStateName(null).isEmpty(), "ageState");
        assertTrue(McaCompat.getPersonalityName(null).isEmpty(), "personality");
        assertTrue(McaCompat.getMoodValue(null).isEmpty(), "moodValue");
        assertTrue(McaCompat.getMoodName(null).isEmpty(), "moodName");
        assertTrue(McaCompat.getHealthFraction(null).isEmpty(), "healthFraction");
    }

    @Test
    void infectionDefaultsToZeroOnMissingData() {
        assertEquals(0f, McaCompat.getInfectionProgress(null), "infectionProgress");
    }
}
