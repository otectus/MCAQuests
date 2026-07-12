package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure unit tests for {@link ReputationService#tierIndex(ReputationTierSet, int)} (0.7.0). */
class TierIndexTest {

    private static final ReputationTierSet LADDER = ReputationTiers.BUILTIN_DEFAULT;

    @Test
    void reputationBelowLowestThreshold() {
        assertEquals(-1, ReputationService.tierIndex(LADDER, -50),
                "reputation below lowest threshold returns -1");
    }

    @Test
    void reputationAtLowestThreshold() {
        assertEquals(0, ReputationService.tierIndex(LADDER, 0),
                "reputation at lowest threshold returns index 0");
    }

    @Test
    void reputationBetweenThresholds() {
        assertEquals(1, ReputationService.tierIndex(LADDER, 25),
                "reputation at exact threshold is inclusive");
        assertEquals(1, ReputationService.tierIndex(LADDER, 50),
                "reputation between thresholds returns matching tier index");
        assertEquals(2, ReputationService.tierIndex(LADDER, 75),
                "reputation at exact threshold of higher tier");
    }

    @Test
    void reputationAboveHighestThreshold() {
        assertEquals(4, ReputationService.tierIndex(LADDER, 300),
                "reputation at highest threshold returns last index");
        assertEquals(4, ReputationService.tierIndex(LADDER, 99999),
                "reputation above highest threshold returns last index");
    }

    @Test
    void emptyLadder() {
        ReputationTierSet empty = new ReputationTierSet(List.of());
        assertEquals(-1, ReputationService.tierIndex(empty, 0),
                "empty ladder returns -1 for any reputation");
        assertEquals(-1, ReputationService.tierIndex(empty, 100),
                "empty ladder returns -1 for any reputation");
    }

    @Test
    void singleTierLadder() {
        ReputationTierSet single = new ReputationTierSet(List.of(
                new ReputationTier("only", 0, "Only", Optional.empty())));
        assertEquals(0, ReputationService.tierIndex(single, 0),
                "single tier at threshold returns index 0");
        assertEquals(0, ReputationService.tierIndex(single, 100),
                "single tier above threshold returns index 0");
        assertEquals(-1, ReputationService.tierIndex(single, -1),
                "single tier below threshold returns -1");
    }
}
