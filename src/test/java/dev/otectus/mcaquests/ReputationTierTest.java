package dev.otectus.mcaquests;

import dev.otectus.mcaquests.data.ReputationTierValidator;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry-free tests for the pure reputation-tier ladder logic and tier-up detection (0.7.0). */
class ReputationTierTest {

    private static final ReputationTierSet LADDER = ReputationTiers.BUILTIN_DEFAULT;

    @Test
    void tierForBoundaries() {
        assertEquals("stranger", LADDER.tierFor(-50).id(), "below floor maps to lowest tier");
        assertEquals("stranger", LADDER.tierFor(0).id(), "exactly the floor threshold");
        assertEquals("stranger", LADDER.tierFor(24).id(), "just below next threshold");
        assertEquals("acquaintance", LADDER.tierFor(25).id(), "exact threshold is inclusive");
        assertEquals("friend", LADDER.tierFor(100).id(), "between thresholds");
        assertEquals("revered", LADDER.tierFor(300).id(), "top threshold");
        assertEquals("revered", LADDER.tierFor(99999).id(), "above the top stays at the top tier");
    }

    @Test
    void indexOfAndNextTier() {
        assertEquals(0, LADDER.indexOf("stranger"));
        assertEquals(2, LADDER.indexOf("friend"));
        assertEquals(-1, LADDER.indexOf("nonexistent"));
        assertEquals("acquaintance", LADDER.nextTier(0).orElseThrow().id());
        assertTrue(LADDER.nextTier(300).isEmpty(), "top tier has no next");
    }

    @Test
    void validatorAcceptsBuiltinDefault() {
        List<String> errors = new ArrayList<>();
        assertTrue(ReputationTierValidator.validate(ReputationTiers.DEFAULT_ID, LADDER, errors));
        assertTrue(errors.isEmpty(), () -> "unexpected errors: " + errors);
    }

    @Test
    void validatorRejectsNonAscendingThresholds() {
        ReputationTierSet bad = new ReputationTierSet(List.of(
                new ReputationTier("a", 0, "A", Optional.empty()),
                new ReputationTier("b", 0, "B", Optional.empty())));
        List<String> errors = new ArrayList<>();
        assertFalse(ReputationTierValidator.validate(id("bad"), bad, errors));
        assertFalse(errors.isEmpty());
    }

    @Test
    void validatorRejectsHighFloorAndEmpty() {
        List<String> errors = new ArrayList<>();
        assertFalse(ReputationTierValidator.validate(id("empty"), new ReputationTierSet(List.of()), errors));

        ReputationTierSet highFloor = new ReputationTierSet(List.of(
                new ReputationTier("a", 10, "A", Optional.empty())));
        List<String> errors2 = new ArrayList<>();
        assertFalse(ReputationTierValidator.validate(id("highfloor"), highFloor, errors2));
    }

    @Test
    void validatorRejectsDuplicateIds() {
        ReputationTierSet dup = new ReputationTierSet(List.of(
                new ReputationTier("a", 0, "A", Optional.empty()),
                new ReputationTier("a", 10, "A2", Optional.empty())));
        List<String> errors = new ArrayList<>();
        assertFalse(ReputationTierValidator.validate(id("dup"), dup, errors));
    }

    @Test
    void tierUpFiresOnceWhenCrossingUp() {
        // 70 -> 80 crosses friend (75); no high-water yet -> fires friend.
        Optional<ReputationTier> reached = ReputationService.tierUpReached(LADDER, 70, 80, null);
        assertEquals("friend", reached.orElseThrow().id());
    }

    @Test
    void tierUpDoesNotFireWithinSameTier() {
        assertTrue(ReputationService.tierUpReached(LADDER, 80, 100, "friend").isEmpty(),
                "still within friend, no tier-up");
    }

    @Test
    void tierUpDoesNotRefireBelowHighWater() {
        // Dropped to acquaintance then climbed back to friend; high-water already friend -> no refire.
        assertTrue(ReputationService.tierUpReached(LADDER, 40, 80, "friend").isEmpty());
    }

    @Test
    void tierUpFiresAboveHighWater() {
        // High-water friend; climbing into honored (150) is a new milestone.
        assertEquals("honored", ReputationService.tierUpReached(LADDER, 100, 160, "friend").orElseThrow().id());
    }

    @Test
    void reachingTheStartingTierIsNotATierUp() {
        // Starting at stranger and gaining rep without crossing a threshold never fires.
        assertTrue(ReputationService.tierUpReached(LADDER, 0, 10, null).isEmpty());
    }

    @Test
    void parseVillageId() {
        assertEquals(42, ReputationService.parseVillageId("v:42").orElseThrow());
        assertTrue(ReputationService.parseVillageId("family:abc").isEmpty());
        assertTrue(ReputationService.parseVillageId("v:notanumber").isEmpty());
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("mcaquests", path);
    }
}
