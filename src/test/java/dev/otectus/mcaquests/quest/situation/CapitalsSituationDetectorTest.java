package dev.otectus.mcaquests.quest.situation;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.situation.state.CapitalsSignalStateSavedData;
import dev.otectus.mcaquests.quest.situation.trigger.CapitalInterregnumTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.CapitalWarTrigger;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the MCA Capitals situation detector that can be decided without a world (1.6.0).
 *
 * <p>{@code CapitalsSituationDetector.poll} itself is not driven here: it needs a {@code MinecraftServer}
 * for {@link CapitalsSignalStateSavedData} and for {@code SituationManager}, and there is no server
 * harness in this source set (the Townstead detector has no test for the same reason). What is covered
 * instead is everything the poll's correctness actually rests on and that is pure: the two triggers'
 * codecs and matching, the unordered pair key, and the transition bookkeeping that decides whether a
 * signal is raised at all.
 */
class CapitalsSituationDetectorTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final UUID ARDEN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRELL = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static CapitalInterregnumTrigger interregnum(String json) {
        return CapitalInterregnumTrigger.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, error -> {
                    throw new AssertionError(error);
                });
    }

    @Nested
    @DisplayName("the interregnum trigger")
    class Interregnum {

        @Test
        @DisplayName("defaults to any vacancy when the pack does not say which")
        void defaultsToAny() {
            CapitalInterregnumTrigger trigger = interregnum("{}");

            assertEquals("any", trigger.sovereign());
            assertEquals(SituationSignalType.CAPITAL_INTERREGNUM, trigger.signalType());
            assertTrue(trigger.matches(TriggerSignal.capitalInterregnum(null, 12, ARDEN, false)));
            assertTrue(trigger.matches(TriggerSignal.capitalInterregnum(null, 12, null, true)));
        }

        @Test
        @DisplayName("tells a dead villager sovereign from an absent player one")
        void narrowsOnWhoHeldTheThrone() {
            CapitalInterregnumTrigger villager = interregnum("{\"sovereign\": \"villager\"}");
            CapitalInterregnumTrigger player = interregnum("{\"sovereign\": \"player\"}");

            TriggerSignal villagerDied = TriggerSignal.capitalInterregnum(null, 12, ARDEN, false);
            TriggerSignal playerGone = TriggerSignal.capitalInterregnum(null, 12, null, true);

            assertTrue(villager.matches(villagerDied));
            assertFalse(villager.matches(playerGone),
                    "a court settling its own succession is not a capital waiting on an absent player");
            assertTrue(player.matches(playerGone));
            assertFalse(player.matches(villagerDied));
        }

        @Test
        @DisplayName("carries the sovereign who died, so the offer can be about them")
        void carriesTheDeceased() {
            TriggerSignal signal = TriggerSignal.capitalInterregnum(null, 12, ARDEN, false);

            assertEquals(Optional.of(ARDEN), signal.villager());
            assertEquals(12, signal.villageId());
        }
    }

    @Nested
    @DisplayName("the war trigger")
    class War {

        @Test
        @DisplayName("opens on any declaration, whatever the pair were before")
        void opensOnAnyDeclaration() {
            CapitalWarTrigger trigger = CapitalWarTrigger.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                    .getOrThrow(false, error -> {
                        throw new AssertionError(error);
                    });

            assertEquals(SituationSignalType.CAPITAL_WAR, trigger.signalType());
            assertTrue(trigger.matches(TriggerSignal.capitalWar(null, 12, BRELL, "ALLIANCE")));
            assertTrue(trigger.matches(TriggerSignal.capitalWar(null, 12, BRELL, "TRUCE")));
        }

        @Test
        @DisplayName("says which relation the war came out of, and against whom")
        void carriesTheTransition() {
            TriggerSignal signal = TriggerSignal.capitalWar(null, 12, BRELL, "ALLIANCE");
            SignalContext context = signal.signalContext().orElseThrow();

            assertTrue(context.matchesKind("relation:" + BRELL));
            assertEquals(Optional.of("ALLIANCE"), context.from());
            assertEquals(Optional.of("WAR"), context.to());
        }
    }

    @Nested
    @DisplayName("the pair key")
    class PairKey {

        @Test
        @DisplayName("is the same whichever capital declared")
        void isUnordered() {
            assertEquals(CapitalsSituationDetector.relationKey(ARDEN, BRELL),
                    CapitalsSituationDetector.relationKey(BRELL, ARDEN),
                    "one war between two capitals must be remembered once, or it opens twice");
        }

        @Test
        @DisplayName("keeps different pairs apart")
        void separatesPairs() {
            UUID third = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

            assertFalse(CapitalsSituationDetector.relationKey(ARDEN, BRELL)
                    .equals(CapitalsSituationDetector.relationKey(ARDEN, third)));
        }
    }

    @Nested
    @DisplayName("the persisted baseline")
    class Baseline {

        @Test
        @DisplayName("never announces the vacancy or the war it found on arrival")
        void firstObservationIsSilent() {
            CapitalsSignalStateSavedData state = new CapitalsSignalStateSavedData();

            assertFalse(state.observeRisingEdge(ARDEN + "|interregnum", true),
                    "a throne already empty when we first look has not just fallen vacant");
            assertTrue(state.observeLabel(CapitalsSituationDetector.relationKey(ARDEN, BRELL), "WAR")
                    .isEmpty(), "a war already being fought when we first look was not just declared");
        }

        @Test
        @DisplayName("announces a vacancy once, not every poll it lasts")
        void vacancyIsAnnouncedOnce() {
            CapitalsSignalStateSavedData state = new CapitalsSignalStateSavedData();
            String key = ARDEN + "|interregnum";
            state.observeRisingEdge(key, false); // baseline: throne occupied

            assertTrue(state.observeRisingEdge(key, true));
            assertFalse(state.observeRisingEdge(key, true), "the interregnum has not happened twice");
            assertFalse(state.observeRisingEdge(key, false), "an heir taking the throne is not news here");
            assertTrue(state.observeRisingEdge(key, true), "the next vacancy is news again");
        }

        @Test
        @DisplayName("reports the relation a war came out of, across a save and reload")
        void reportsTheRelationWarCameOutOf() {
            CapitalsSignalStateSavedData state = new CapitalsSignalStateSavedData();
            String key = CapitalsSituationDetector.relationKey(ARDEN, BRELL);
            state.observeLabel(key, "ALLIANCE");

            CapitalsSignalStateSavedData reloaded =
                    CapitalsSignalStateSavedData.load(state.save(new CompoundTag()));

            assertEquals(Optional.of("ALLIANCE"), reloaded.observeLabel(key, "WAR"),
                    "a war declared just before a stop must still be news on the first poll after it");
            assertTrue(reloaded.observeLabel(key, "WAR").isEmpty(),
                    "and must not be declared again on every poll it lasts");
        }

        @Test
        @DisplayName("does not record a relation it could not read")
        void unreadableRelationIsNotABaseline() {
            CapitalsSignalStateSavedData state = new CapitalsSignalStateSavedData();
            String key = CapitalsSituationDetector.relationKey(ARDEN, BRELL);
            state.observeLabel(key, "PEACE");

            assertTrue(state.observeLabel(key, "").isEmpty());
            assertEquals(Optional.of("PEACE"), state.observeLabel(key, "WAR"),
                    "a poll that could not reach Capitals must not look like a transition out of nowhere");
        }
    }
}
