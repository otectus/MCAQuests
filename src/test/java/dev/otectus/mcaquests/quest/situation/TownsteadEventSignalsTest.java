package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalKeys;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One intended signal per real transition, whichever of Townstead's event feed and the polling scan
 * noticed it first. Each case plays the same moment through both paths in every order and counts
 * what reached the situation manager.
 */
class TownsteadEventSignalsTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final int VILLAGE = 12;

    private final TownsteadSignalStateSavedData state = new TownsteadSignalStateSavedData();
    private final List<TriggerSignal> fired = new ArrayList<>();
    private final EnumSet<SituationSignalType> wanted = EnumSet.allOf(SituationSignalType.class);
    private final TownsteadEventSignals events = new TownsteadEventSignals(state, wanted::contains, fired::add);

    /** What the polling detector does for a collapse it sees: the same store call, verbatim. */
    private boolean scanSeesCollapsed(UUID uuid, boolean collapsed) {
        return state.observeRisingEdge(uuid + "|collapsed", collapsed);
    }

    private static TownsteadSignalStateSavedData reloaded(TownsteadSignalStateSavedData data) {
        return TownsteadSignalStateSavedData.load(data.save(new CompoundTag()));
    }

    @Nested
    @DisplayName("a collapse")
    class Collapse {

        @Test
        @DisplayName("fires from the event even when no scan has ever seen the villager")
        void eventIsAuthoritativeWithoutABaseline() {
            UUID uuid = UUID.randomUUID();
            events.collapsed(null, VILLAGE, uuid);
            assertEquals(1, fired.size(), "a collapse event is a transition, not a first sighting");
            assertEquals(SituationSignalType.TOWNSTEAD_COLLAPSE, fired.get(0).type());
            assertFalse(scanSeesCollapsed(uuid, true), "the scan that follows finds the baseline already moved");
        }

        @Test
        @DisplayName("fires once when the scan saw it first")
        void scanThenEvent() {
            UUID uuid = UUID.randomUUID();
            scanSeesCollapsed(uuid, false);            // seed: the villager was fine
            assertTrue(scanSeesCollapsed(uuid, true)); // the scan reports the collapse
            events.collapsed(null, VILLAGE, uuid);      // the event arrives late
            assertEquals(0, fired.size(), "the scan already reported this collapse");
        }

        @Test
        @DisplayName("is not repeated by a second delivery, and is news again after a recovery")
        void repeatsAndRecovery() {
            UUID uuid = UUID.randomUUID();
            events.collapsed(null, VILLAGE, uuid);
            events.collapsed(null, VILLAGE, uuid);
            assertEquals(1, fired.size());
            events.recovered(uuid);
            events.collapsed(null, VILLAGE, uuid);
            assertEquals(2, fired.size(), "collapse, recovery, collapse is two collapses");
        }

        @Test
        @DisplayName("survives a restart without replaying")
        void persists() {
            UUID uuid = UUID.randomUUID();
            events.collapsed(null, VILLAGE, uuid);
            TownsteadSignalStateSavedData after = reloaded(state);
            List<TriggerSignal> later = new ArrayList<>();
            new TownsteadEventSignals(after, wanted::contains, later::add).collapsed(null, VILLAGE, uuid);
            assertTrue(later.isEmpty(), "the reloaded baseline still says down");
        }

        @Test
        @DisplayName("records nothing when no definition wants collapses, exactly like the scan")
        void unwantedIsNotObserved() {
            wanted.remove(SituationSignalType.TOWNSTEAD_COLLAPSE);
            UUID uuid = UUID.randomUUID();
            events.collapsed(null, VILLAGE, uuid);
            assertTrue(fired.isEmpty());
            assertEquals(0, state.size(), "an unwanted signal seeds no baseline");
        }
    }

    @Nested
    @DisplayName("a tier change")
    class Tier {

        @Test
        @DisplayName("uses the event's own before when there is no baseline")
        void eventBeforeWithoutBaseline() {
            UUID uuid = UUID.randomUUID();
            events.tierChanged(null, VILLAGE, uuid, "minecraft:farmer", 1, 2);
            assertEquals(1, fired.size());
            assertEquals(2, fired.get(0).magnitude());
            assertEquals(Integer.valueOf(1), fired.get(0).context().oldTier());
            // A later scan reading tier 2 finds it already recorded.
            assertFalse(state.observeIncrease(uuid + "|tier|minecraft:farmer", 2));
        }

        @Test
        @DisplayName("defers to a baseline the scan already advanced")
        void scanFirst() {
            UUID uuid = UUID.randomUUID();
            String key = uuid + "|tier|minecraft:farmer";
            state.observeIncrease(key, 1);
            assertTrue(state.observeIncrease(key, 2)); // the scan reported 1 -> 2
            events.tierChanged(null, VILLAGE, uuid, "minecraft:farmer", 1, 2);
            assertTrue(fired.isEmpty());
        }

        @Test
        @DisplayName("reports the rise from the last recorded tier when several passed between scans")
        void successiveRises() {
            UUID uuid = UUID.randomUUID();
            String key = uuid + "|tier|minecraft:farmer";
            state.observeIncrease(key, 1);
            events.tierChanged(null, VILLAGE, uuid, "minecraft:farmer", 1, 2);
            events.tierChanged(null, VILLAGE, uuid, "minecraft:farmer", 2, 3);
            assertEquals(2, fired.size());
            assertEquals(Integer.valueOf(2), fired.get(1).context().oldTier());
            assertEquals(3, fired.get(1).magnitude());
        }

        @Test
        @DisplayName("a fall is recorded but never announced")
        void fallIsQuiet() {
            UUID uuid = UUID.randomUUID();
            events.tierChanged(null, VILLAGE, uuid, "minecraft:farmer", 3, 2);
            assertTrue(fired.isEmpty());
            assertEquals(2, state.lastReading(uuid + "|tier|minecraft:farmer", -1));
        }
    }

    @Nested
    @DisplayName("a life-stage crossing")
    class Life {

        @Test
        @DisplayName("announces the raw and canonical axes from the event's before, and only seeds senior")
        void axes() {
            UUID uuid = UUID.randomUUID();
            events.lifeStageChanged(null, VILLAGE, uuid, "child", "adult", false, "child", "adult");
            assertEquals(2, fired.size(), "life_stage and canonical_stage; senior has no before to report");
            assertEquals("child", fired.get(0).context().from().orElse(""));
            assertEquals("adult", fired.get(0).context().to().orElse(""));
            assertEquals("false", state.lastLabel(uuid + "|senior"));
        }

        @Test
        @DisplayName("stays quiet for a crossing the scan already reported")
        void scanFirst() {
            UUID uuid = UUID.randomUUID();
            state.observeLabel(uuid + "|life_stage", "child");
            assertTrue(state.observeLabel(uuid + "|life_stage", "adult").isPresent());
            events.lifeStageChanged(null, VILLAGE, uuid, "child", "adult", false, "", "");
            assertTrue(fired.isEmpty());
        }

        @Test
        @DisplayName("forgets a dead villager's baselines")
        void death() {
            UUID uuid = UUID.randomUUID();
            events.collapsed(null, VILLAGE, uuid);
            events.lifeStageChanged(null, VILLAGE, uuid, "child", "adult", false, "child", "adult");
            events.died(uuid);
            assertEquals(0, state.size());
        }
    }

    @Nested
    @DisplayName("a spirit change")
    class Spirit {

        @Test
        @DisplayName("fires from before/after without a baseline, then stays quiet for the scan")
        void authoritative() {
            events.spiritChanged(null, VILLAGE, 0, 1, "", "townstead:harbor", "settlement", "single");
            assertEquals(1, fired.size());
            TriggerSignal signal = fired.get(0);
            assertEquals(Integer.valueOf(0), signal.context().oldTier());
            assertEquals(1, signal.magnitude());
            assertEquals("settlement", signal.context().from().orElse(""));
            assertEquals("single", signal.context().to().orElse(""));
            // The scan's own observation of the same readout is silent.
            assertFalse(state.observeIncrease("12|spirit", 1));
            assertFalse(state.observeChanged("12|spirit_id", "townstead:harbor".hashCode()));
            assertTrue(state.observeLabel("12|spirit_class", "single").isEmpty());
        }

        @Test
        @DisplayName("is silent for a readout the scan already recorded")
        void scanFirst() {
            state.observeIncrease("12|spirit", 0);
            state.observeChanged("12|spirit_id", "".hashCode());
            state.observeLabel("12|spirit_class", "settlement");
            state.observeIncrease("12|spirit", 1);
            state.observeChanged("12|spirit_id", "townstead:harbor".hashCode());
            state.observeLabel("12|spirit_class", "single");
            events.spiritChanged(null, VILLAGE, 0, 1, "", "townstead:harbor", "settlement", "single");
            assertTrue(fired.isEmpty());
        }
    }

    @Nested
    @DisplayName("a building")
    class Building {

        @Test
        @DisplayName("fires without a baseline and leaves the register signature for the scan")
        void authoritative() {
            events.building(null, VILLAGE, OptionalInt.of(4711), "dock", 2);
            assertEquals(1, fired.size());
            assertEquals("dock", fired.get(0).context().stringValue());
            assertEquals(2, fired.get(0).magnitude());
            assertFalse(state.observeChanged("12|buildings", 4711), "the scan sees an unchanged register");
        }

        @Test
        @DisplayName("is silent when the scan already reported the same register, and on redelivery")
        void scanFirstAndRepeat() {
            state.observeChanged("12|buildings", 100);
            assertTrue(state.observeChanged("12|buildings", 4711)); // the scan reported the change
            events.building(null, VILLAGE, OptionalInt.of(4711), "dock", 2);
            events.building(null, VILLAGE, OptionalInt.of(4711), "dock", 2);
            assertTrue(fired.isEmpty());
        }

        @Test
        @DisplayName("fires and moves the baseline when the register changed again since the scan")
        void eventThenScan() {
            state.observeChanged("12|buildings", 100);
            events.building(null, VILLAGE, OptionalInt.of(4711), "dock", 2);
            assertEquals(1, fired.size());
            assertFalse(state.observeChanged("12|buildings", 4711));
        }

        @Test
        @DisplayName("takes the event at its word when the register cannot be read")
        void unreadableRegister() {
            state.observeChanged("12|buildings", 100);
            events.building(null, VILLAGE, OptionalInt.empty(), "dock", 2);
            assertEquals(1, fired.size());
            assertEquals(Integer.MIN_VALUE, state.lastReading("12|buildings", Integer.MIN_VALUE),
                    "the stale signature is dropped so the next scan re-seeds instead of firing");
        }
    }

    @Nested
    @DisplayName("a day rollover")
    class Calendar {

        private TownsteadCalendarView day(String season, int dayOfYear, int year) {
            return new TownsteadCalendarView("townstead:default", 100L + dayOfYear, 0, "world", year, 1, 1,
                    dayOfYear, dayOfYear % 7, season);
        }

        @Test
        @DisplayName("announces only the periods that changed, once per village, from the event's before")
        void seasonTurns() {
            List<VillagePlace> villages = List.of(new VillagePlace(null, 1), new VillagePlace(null, 2));
            events.dayRolledOver(day("autumn", 90, 1), day("winter", 91, 1), villages);
            List<String> periods = fired.stream().map(s -> s.context().stringValue()).toList();
            assertEquals(2, fired.stream().filter(s -> "season".equals(s.context().stringValue())).count(),
                    "season changed for both villages: " + periods);
            assertEquals(0, fired.stream().filter(s -> "year".equals(s.context().stringValue())).count());
            for (TriggerSignal signal : fired) {
                if ("season".equals(signal.context().stringValue())) {
                    assertEquals("autumn", signal.context().from().orElse(""));
                    assertEquals("winter", signal.context().to().orElse(""));
                }
            }
        }

        @Test
        @DisplayName("is silent for a rollover the scan already recorded for that village")
        void scanFirst() {
            String key = TownsteadSignalKeys.calendar("townstead:default", "season", null, 1);
            state.observeLabel(key, "autumn");
            assertTrue(state.observeLabel(key, "winter").isPresent());
            events.dayRolledOver(day("autumn", 90, 1), day("winter", 91, 1), List.of(new VillagePlace(null, 1)));
            assertTrue(fired.stream().noneMatch(s -> "season".equals(s.context().stringValue())));
        }

        @Test
        @DisplayName("a profile switch seeds and never synthesises a transition")
        void profileSwitch() {
            TownsteadCalendarView other = new TownsteadCalendarView("pack:other", 200L, 0, "world", 1, 1, 1, 1, 1, "spring");
            events.dayRolledOver(other, day("winter", 91, 1), List.of(new VillagePlace(null, 1)));
            assertTrue(fired.isEmpty());
            assertEquals("winter", state.lastLabel(TownsteadSignalKeys.calendar("townstead:default", "season", null, 1)));
        }
    }

    @Nested
    @DisplayName("village keys")
    class Keys {

        @Test
        @DisplayName("keep the bare id in the overworld so existing baselines stay valid")
        void overworldIsBare() {
            assertEquals("12", TownsteadSignalKeys.village((ResourceLocation) null, 12));
            assertEquals("12", TownsteadSignalKeys.village(new ResourceLocation("minecraft", "overworld"), 12));
        }

        @Test
        @DisplayName("qualify any other dimension so village 12 there never collides with village 12 here")
        void otherDimensionsAreQualified() {
            ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
            assertEquals("minecraft:the_nether|12", TownsteadSignalKeys.village(nether, 12));
            assertEquals("calendar|p|season|minecraft:the_nether|12",
                    TownsteadSignalKeys.calendar("p", "season", nether, 12));
        }
    }
}
