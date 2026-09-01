package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that closes the 1.4.0 defect: telling a real Townstead progression track apart from the
 * zero/default one that Townstead hands back for a profession it has no progression for.
 *
 * <p>These are shaped after Townstead 0.7.6's actual built-ins. {@code ProfessionXpType} supplies
 * progression for farmer, butcher, cook and shepherd; fisherman and leatherworker have work tasks that
 * award no XP at all, so {@code ProfessionProgressions.spec("fisherman")} returns the default. Before
 * 1.4.1 nothing could tell those apart, and three shipped quests waited forever as a result.
 */
class TownsteadProfessionTrackTest {

    /** The shape Townstead 0.7.6 returns for a profession it has no progression for. */
    private static TownsteadProfessionTrackView defaultSpec(String profession) {
        return new TownsteadProfessionTrackView(profession, List.of(), 0, 0, 0, false);
    }

    /** A five-tier track of the kind the 0.7.6 built-ins expose. */
    private static TownsteadProfessionTrackView realTrack(String profession) {
        return new TownsteadProfessionTrackView(profession, List.of(0, 50, 150, 350, 700), 5, 1000, 40, false);
    }

    @Test
    @DisplayName("the zero/default spec is not a progressive track")
    void defaultSpecIsNotProgressive() {
        for (String profession : List.of("minecraft:fisherman", "minecraft:leatherworker")) {
            TownsteadProfessionTrackView track = defaultSpec(profession);
            assertFalse(track.progressive(), profession + " must not read as progressive");
            assertFalse(track.supportsTier(1), profession + " cannot reach any tier");
            assertFalse(track.supportsXpDelta(0, 1),
                    profession + " cannot earn a single point, which is the whole 1.4.0 bug");
            assertEquals(0, track.remainingXp(0));
        }
    }

    @Test
    @DisplayName("a real track reports the tiers and headroom it actually has")
    void realTrackAnswersHonestly() {
        TownsteadProfessionTrackView track = realTrack("minecraft:farmer");
        assertTrue(track.progressive());
        assertTrue(track.supportsTier(3));
        assertTrue(track.supportsTier(5));
        assertFalse(track.supportsTier(6), "a tier above maxTier is not reachable");
        assertFalse(track.supportsTier(0), "tier 0 is where everyone starts; it is not a goal");
    }

    @Test
    @DisplayName("an XP delta is only supported while there is room above the current XP")
    void xpDeltaRespectsTheCeiling() {
        TownsteadProfessionTrackView track = realTrack("minecraft:shepherd");
        assertTrue(track.supportsXpDelta(0, 150));
        assertTrue(track.supportsXpDelta(900, 100), "exactly the remaining headroom is still reachable");
        assertFalse(track.supportsXpDelta(900, 101), "one point past the ceiling is not");
        assertFalse(track.supportsXpDelta(1000, 1), "a villager at the maximum cannot earn more");
        assertEquals(100, track.remainingXp(900));
        assertEquals(0, track.remainingXp(1200), "remaining XP is never negative");
    }

    @Test
    @DisplayName("maxTier is taken from the spec, not from counting thresholds")
    void maxTierIsNotTheThresholdCount() {
        // 0.7.6's built-ins report tier 5 from five thresholds, so the two happen to agree there --
        // but a track whose spec disagrees must be believed rather than recounted.
        TownsteadProfessionTrackView track =
                new TownsteadProfessionTrackView("minecraft:butcher", List.of(0, 100), 4, 400, 30, false);
        assertTrue(track.supportsTier(4), "the spec says four tiers even though two thresholds were derived");
        assertFalse(track.supportsTier(5));
    }

    @Test
    @DisplayName("a threshold is only reported for a tier the track can reach")
    void thresholdsAreBounded() {
        TownsteadProfessionTrackView track = realTrack("minecraft:farmer");
        assertEquals(150, track.thresholdFor(3).orElse(-1));
        assertTrue(track.thresholdFor(6).isEmpty());
        assertTrue(defaultSpec("minecraft:fisherman").thresholdFor(1).isEmpty());
    }

    @Test
    @DisplayName("the absent-track value is safe to hand to every caller")
    void noneIsTotal() {
        TownsteadProfessionTrackView none = TownsteadProfessionTrackView.none("minecraft:fisherman");
        assertEquals("minecraft:fisherman", none.professionId());
        assertFalse(none.progressive());
        assertEquals(List.of(), none.tierThresholds());
        assertEquals(0, none.maxTier());
        assertEquals(0, none.dailyCap());
        assertFalse(none.dataDriven());
    }
}
