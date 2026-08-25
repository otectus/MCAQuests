package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind Townstead rewards and crises.
 *
 * <p>All of this used to live inside methods that need a bound Townstead to reach, which meant none of
 * it had ever been executed — and every one of these rules fails <em>quietly</em>. A daily cap applied
 * against yesterday's counter, a clamp using the wrong need's ceiling, a crisis threshold with no gap:
 * none of them throw. They just hand out the wrong number, or flicker a situation on and off, and
 * nobody finds out until a player notices something is off.
 */
class TownsteadMathTest {

    @Nested
    @DisplayName("profession experience")
    class Xp {

        private static final int MAX = 1000;
        private static final int CAP = 100;

        private TownsteadXpMath.Award award(int requested, int currentXp, int xpToday, long recordedDay,
                                            long worldDay, boolean respectCap) {
            return TownsteadXpMath.award(requested, currentXp, MAX, xpToday, recordedDay, worldDay,
                    CAP, respectCap);
        }

        @Test
        @DisplayName("grants the whole request when there is room and allowance")
        void grantsInFull() {
            TownsteadXpMath.Award result = award(40, 200, 0, 5L, 5L, true);

            assertTrue(result.granted());
            assertEquals(40, result.applied());
            assertEquals(240, result.newXp());
            assertEquals(40, result.newXpToday());
        }

        @Test
        @DisplayName("trims the request to what today's allowance has left")
        void trimsToTheDailyCap() {
            TownsteadXpMath.Award result = award(60, 200, 70, 5L, 5L, true);

            assertTrue(result.granted());
            assertEquals(30, result.applied(), "70 of the 100 daily cap was already spent");
            assertEquals(100, result.newXpToday());
        }

        /**
         * The rule the whole day-stamp exists for. Without it, a villager who earned their allowance
         * yesterday could never earn again, because the stored counter would keep limiting today.
         */
        @Test
        @DisplayName("a new world day wipes yesterday's counter before measuring")
        void resetsOnANewDay() {
            TownsteadXpMath.Award result = award(60, 200, 100, 4L, 5L, true);

            assertTrue(result.granted());
            assertEquals(60, result.applied(), "yesterday's spend must not limit today");
            assertEquals(60, result.newXpToday(), "and the counter restarts from this award");
        }

        @Test
        @DisplayName("reports the cap rather than granting nothing silently")
        void reportsAnExhaustedCap() {
            TownsteadXpMath.Award result = award(40, 200, 100, 5L, 5L, true);

            assertFalse(result.granted());
            assertEquals(TownsteadXpMath.Outcome.DAILY_CAP, result.outcome());
            assertEquals(0, result.applied());
            assertEquals(200, result.newXp(), "a refused award must not move the total");
        }

        @Test
        @DisplayName("bypassing the cap still respects the maximum")
        void uncappedStillStopsAtMax() {
            TownsteadXpMath.Award uncapped = award(500, 200, 100, 5L, 5L, false);
            assertEquals(500, uncapped.applied(), "with the cap waived the whole request lands");

            TownsteadXpMath.Award atCeiling = award(500, 900, 0, 5L, 5L, false);
            assertEquals(100, atCeiling.applied(), "but never past the profession maximum");
            assertEquals(MAX, atCeiling.newXp());
        }

        @Test
        @DisplayName("a profession already at maximum is distinguishable from one that is capped")
        void reportsAtMaxSeparately() {
            TownsteadXpMath.Award result = award(40, MAX, 0, 5L, 5L, true);

            assertEquals(TownsteadXpMath.Outcome.AT_MAX, result.outcome(),
                    "at-max and daily-cap are different problems and should not read the same");
        }

        @Test
        @DisplayName("a nonsensical request is refused rather than subtracting")
        void refusesNonPositiveRequests() {
            assertEquals(TownsteadXpMath.Outcome.INVALID, award(0, 200, 0, 5L, 5L, true).outcome());
            assertEquals(TownsteadXpMath.Outcome.INVALID, award(-50, 200, 0, 5L, 5L, true).outcome());
            assertEquals(200, award(-50, 200, 0, 5L, 5L, true).newXp());
        }

        /**
         * A retry after a partial award must pick up from what actually landed, not from the original
         * request -- otherwise a reward granted twice would exceed both the cap and the maximum.
         */
        @Test
        @DisplayName("a retry after a partial award sees the spend that already happened")
        void retryHonoursThePreviousAward() {
            TownsteadXpMath.Award first = award(80, 200, 40, 5L, 5L, true);
            assertEquals(60, first.applied(), "only 60 of the 100 cap was left");

            TownsteadXpMath.Award retry = award(80, first.newXp(), first.newXpToday(), 5L, 5L, true);
            assertFalse(retry.granted(), "the cap is now spent; a retry must not grant again");
        }

        @Test
        @DisplayName("the tier-up tick only moves when the tier actually rose")
        void tierUpTickIsPreserved() {
            assertEquals(9000L, TownsteadXpMath.tierUpTick(2, 3, 100L, 9000L), "a promotion stamps now");
            assertEquals(100L, TownsteadXpMath.tierUpTick(2, 2, 100L, 9000L),
                    "an ordinary award must not reset the pacing timer Townstead reads");
            assertEquals(100L, TownsteadXpMath.tierUpTick(3, 2, 100L, 9000L),
                    "and neither should a tier that somehow fell");
        }

        @Test
        @DisplayName("an uncapped profession is not treated as one with a zero allowance")
        void zeroCapMeansUncapped() {
            TownsteadXpMath.Award result = TownsteadXpMath.award(80, 200, MAX, 500, 5L, 5L, 0, true);

            assertTrue(result.granted(), "a dailyCap of 0 means Townstead sets no daily limit");
            assertEquals(80, result.applied());
        }
    }

    @Nested
    @DisplayName("need clamping")
    class Clamping {

        /**
         * The ranges genuinely differ, which is the entire reason this is a per-need function. A shared
         * ceiling of 100 would let thirst run five times past its maximum; a shared ceiling of 20 would
         * truncate four fifths of every hunger reward.
         */
        @Test
        @DisplayName("uses each need's own ceiling")
        void eachNeedHasItsOwnCeiling() {
            assertEquals(100.0D, NeedMutation.clamp(NeedMutation.Need.HUNGER, 250));
            assertEquals(20.0D, NeedMutation.clamp(NeedMutation.Need.THIRST, 250));
            assertEquals(20.0D, NeedMutation.clamp(NeedMutation.Need.QUENCHED, 250));
            assertEquals(20.0D, NeedMutation.clamp(NeedMutation.Need.FATIGUE, 250));
        }

        @Test
        @DisplayName("energy shares the fatigue range, being the same axis read backwards")
        void energyUsesTheFatigueRange() {
            assertEquals(TownsteadNeedsView.FATIGUE_MAX,
                    NeedMutation.clamp(NeedMutation.Need.ENERGY, 999));
        }

        @Test
        @DisplayName("nothing goes below zero")
        void floorsAtZero() {
            for (NeedMutation.Need need : NeedMutation.Need.values()) {
                assertEquals(0.0D, NeedMutation.clamp(need, -40), need + " must not go negative");
            }
        }

        @Test
        @DisplayName("saturation is floored but not given an invented ceiling")
        void saturationHasNoInventedCeiling() {
            assertEquals(0.0D, NeedMutation.clamp(NeedMutation.Need.SATURATION, -1));
            assertEquals(5000.0D, NeedMutation.clamp(NeedMutation.Need.SATURATION, 5000),
                    "Townstead publishes no saturation maximum, so none should be made up");
        }

        @Test
        @DisplayName("values already in range pass through untouched")
        void inRangeIsUntouched() {
            assertEquals(45.0D, NeedMutation.clamp(NeedMutation.Need.HUNGER, 45));
            assertEquals(12.0D, NeedMutation.clamp(NeedMutation.Need.THIRST, 12));
        }
    }
}
