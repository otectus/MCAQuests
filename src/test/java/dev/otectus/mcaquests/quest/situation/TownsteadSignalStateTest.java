package dev.otectus.mcaquests.quest.situation;

import net.minecraft.core.RegistryAccess;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that stop the Townstead situation detector crying wolf (Townstead spec §7.3).
 *
 * <p>Every failure mode here looks the same to a player: a queue of situations announcing things that
 * happened days ago, or the same emergency re-opening every second. The tests are written as the
 * player-visible claim rather than as the method contract, because that is what would actually be lost.
 */
class TownsteadSignalStateTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static TownsteadSignalStateSavedData fresh() {
        return new TownsteadSignalStateSavedData();
    }

    /** Round-trips through NBT exactly as a world save and reload would. */
    private static TownsteadSignalStateSavedData reloaded(TownsteadSignalStateSavedData data) {
        return TownsteadSignalStateSavedData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
    }

    @Nested
    @DisplayName("a first sighting")
    class FirstSighting {

        @Test
        @DisplayName("is recorded but never announced")
        void isNeverNews() {
            TownsteadSignalStateSavedData state = fresh();

            assertFalse(state.observeChanged("12|spirit_id", "nautical".hashCode()),
                    "installing the mod on an existing world must not open a situation for every "
                            + "village in it");
            assertFalse(state.observeIncrease("12|spirit", 3));
            assertFalse(state.observeRisingEdge(UUID.randomUUID() + "|collapsed", true),
                    "a villager already lying down when we first look has not just collapsed");
        }

        @Test
        @DisplayName("still establishes the baseline the next change is measured from")
        void establishesTheBaseline() {
            TownsteadSignalStateSavedData state = fresh();
            state.observeIncrease("12|spirit", 3);

            assertEquals(3, state.lastReading("12|spirit", -1));
            assertTrue(state.observeIncrease("12|spirit", 4), "the rise after it is genuine news");
        }
    }

    @Nested
    @DisplayName("transitions")
    class Transitions {

        @Test
        @DisplayName("a value that has not moved is not news")
        void unchangedIsQuiet() {
            TownsteadSignalStateSavedData state = fresh();
            state.observeChanged("12|need|hunger", 1);

            assertFalse(state.observeChanged("12|need|hunger", 1),
                    "a village that is still hungry must not re-open the famine every scan");
            assertTrue(state.observeChanged("12|need|hunger", 0), "recovering is a change");
            assertTrue(state.observeChanged("12|need|hunger", 1), "and sliding back is news again");
        }

        @Test
        @DisplayName("only increases count for tiers")
        void tiersOnlyRise() {
            TownsteadSignalStateSavedData state = fresh();
            state.observeIncrease("12|spirit", 3);

            assertFalse(state.observeIncrease("12|spirit", 2),
                    "losing a building is not a promotion to celebrate");
            assertTrue(state.observeIncrease("12|spirit", 3),
                    "climbing back to a tier it fell from is worth announcing again");
        }

        @Test
        @DisplayName("collapse fires on the way down, once")
        void collapseIsAnEdge() {
            TownsteadSignalStateSavedData state = fresh();
            String key = UUID.randomUUID() + "|collapsed";
            state.observeRisingEdge(key, false); // first sighting: upright

            assertTrue(state.observeRisingEdge(key, true), "the moment they go down is the emergency");
            assertFalse(state.observeRisingEdge(key, true),
                    "a villager who stays down is not collapsing again every second");
            state.observeRisingEdge(key, false);
            assertTrue(state.observeRisingEdge(key, true), "collapsing a second time is a second emergency");
        }
    }

    @Nested
    @DisplayName("across a restart")
    class Restarts {

        /**
         * The case the whole store exists for. Without persistence, every restart would look like the
         * entire village had changed at once and the player would be met by a backlog of emergencies for
         * things they already dealt with.
         */
        @Test
        @DisplayName("nothing is replayed")
        void nothingReplays() {
            TownsteadSignalStateSavedData before = fresh();
            String collapsed = UUID.randomUUID() + "|collapsed";
            before.observeRisingEdge(collapsed, false);
            before.observeRisingEdge(collapsed, true);   // announced once, while running
            before.observeIncrease("12|spirit", 4);
            before.observeChanged("12|need|hunger", 1);

            TownsteadSignalStateSavedData after = reloaded(before);

            assertFalse(after.observeRisingEdge(collapsed, true),
                    "the villager is still down, but that is not news a second time");
            assertFalse(after.observeIncrease("12|spirit", 4));
            assertFalse(after.observeChanged("12|need|hunger", 1));
            assertEquals(4, after.lastReading("12|spirit", -1));
        }

        @Test
        @DisplayName("readings written by a different schema are discarded, quietly")
        void aSchemaMismatchStaysQuiet() {
            TownsteadSignalStateSavedData before = fresh();
            before.observeIncrease("12|spirit", 4);

            CompoundTag tag = before.save(new CompoundTag(), RegistryAccess.EMPTY);
            tag.putInt("schema", 999); // as though a future build had written it

            TownsteadSignalStateSavedData after = TownsteadSignalStateSavedData.load(tag, RegistryAccess.EMPTY);

            assertEquals(0, after.size(), "a baseline whose meaning may have changed is dropped");
            assertFalse(after.observeIncrease("12|spirit", 4),
                    "and the re-observation behaves as a first sighting, so nothing fires");
        }
    }

    @Nested
    @DisplayName("TriggerSignal")
    class Signals {

        /**
         * The six original signals predate {@code SignalContext} and must be completely unaffected by it,
         * including every existing call site that constructs one positionally.
         */
        @Test
        @DisplayName("keeps its original factories working, with no context")
        void originalFactoriesAreUntouched() {
            TriggerSignal raid = TriggerSignal.raid(null, 12);

            assertEquals(SituationSignalType.RAID, raid.type());
            assertEquals(12, raid.villageId());
            assertTrue(raid.signalContext().isEmpty());

            TriggerSignal legacy = new TriggerSignal(SituationSignalType.LOW_FOOD, null, 12,
                    null, null, 0f, 4, false);
            assertEquals(4, legacy.magnitude());
            assertTrue(legacy.signalContext().isEmpty(),
                    "the eight-argument form still compiles and means what it always did");
        }

        @Test
        @DisplayName("carries the extra facts a Townstead signal needs")
        void townsteadSignalsCarryContext() {
            TriggerSignal tier = TriggerSignal.townsteadProfessionTier(null, 12, UUID.randomUUID(),
                    "minecraft:farmer", 2, 3);

            assertEquals(SituationSignalType.TOWNSTEAD_PROFESSION_TIER, tier.type());
            assertEquals(3, tier.magnitude());
            SignalContext context = tier.signalContext().orElseThrow();
            assertTrue(context.matchesString("MINECRAFT:FARMER"), "profession matching is case-insensitive");
            assertEquals(1, context.tierJump());
            assertEquals(2, context.tierBefore().orElseThrow());
        }

        @Test
        @DisplayName("appending signal kinds leaves the original ordinals alone")
        void ordinalsAreStable() {
            // The ordinal is one term of the per-village draw seed, so reordering would silently change
            // which situation an existing village opens on an existing day.
            assertEquals(0, SituationSignalType.RAID.ordinal());
            assertEquals(5, SituationSignalType.NIGHT.ordinal());
            assertTrue(SituationSignalType.TOWNSTEAD_NEED.ordinal() > SituationSignalType.NIGHT.ordinal());
        }
    }

    @Nested
    @DisplayName("crisis hysteresis")
    class Hysteresis {

        private static final double ENTER = 0.34D;
        private static final double GAP = 0.10D;

        private boolean state(boolean was, double fraction) {
            return TownsteadSituationDetector.inCrisis(was, fraction, ENTER, GAP);
        }

        @Test
        @DisplayName("opens at the threshold and not before")
        void opensAtTheThreshold() {
            assertFalse(state(false, 0.33D));
            assertTrue(state(false, 0.34D));
            assertTrue(state(false, 0.90D));
        }

        /**
         * The point of the gap. On a single threshold a village sitting exactly on the line opens and
         * closes the same emergency every scan, which a player sees as a situation flickering in and out
         * of their list.
         */
        @Test
        @DisplayName("stays open through the band below the threshold")
        void staysOpenInsideTheBand() {
            assertTrue(state(true, 0.30D), "still above the leave threshold of 0.24");
            assertTrue(state(true, 0.25D));
            assertFalse(state(true, 0.24D), "at the leave threshold it finally closes");
            assertFalse(state(true, 0.10D));
        }

        @Test
        @DisplayName("a village on the exact threshold does not flap")
        void doesNotFlapOnTheLine() {
            boolean open = state(false, 0.34D);
            assertTrue(open);
            // The fraction wobbles a little either side of the entry threshold, as it will when one
            // villager in a village of thirty eats.
            for (double fraction : new double[] {0.33D, 0.35D, 0.32D, 0.34D, 0.31D}) {
                assertTrue(state(open, fraction),
                        "the crisis must stay open at " + fraction + " rather than re-firing");
            }
        }

        @Test
        @DisplayName("a hysteresis of zero degrades to a single threshold rather than misbehaving")
        void zeroGapIsASingleThreshold() {
            assertTrue(TownsteadSituationDetector.inCrisis(false, ENTER, ENTER, 0.0D),
                    "it still opens at the threshold");
            assertTrue(TownsteadSituationDetector.inCrisis(true, 0.35D, ENTER, 0.0D),
                    "and stays open above it");
            assertFalse(TownsteadSituationDetector.inCrisis(true, ENTER, ENTER, 0.0D),
                    "with no gap, sitting exactly on the line closes it -- which is precisely the "
                            + "flapping the configurable gap exists to prevent");
        }

        @Test
        @DisplayName("a hysteresis wider than the threshold cannot make the band negative")
        void anOversizedGapIsClamped() {
            assertTrue(TownsteadSituationDetector.inCrisis(true, 0.01D, ENTER, 0.90D),
                    "the leave threshold floors at zero, so any suffering at all keeps it open");
            assertFalse(TownsteadSituationDetector.inCrisis(true, 0.0D, ENTER, 0.90D),
                    "and no suffering at all still closes it");
        }
    }
}
