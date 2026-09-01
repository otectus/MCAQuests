package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.situation.SituationSignalType;
import dev.otectus.mcaquests.quest.situation.TriggerSignal;
import dev.otectus.mcaquests.quest.situation.trigger.InfectionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.LowFoodTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.MissingKinTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.NightTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.RaidTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for situation trigger matching + signal factories (0.8.0). */
class SituationTriggerMatchTest {

    @Test
    void eachTriggerDeclaresItsSignalType() {
        assertEquals(SituationSignalType.RAID, new RaidTrigger().signalType());
        assertEquals(SituationSignalType.VILLAGER_DEATH, new VillagerDeathTrigger("any").signalType());
        assertEquals(SituationSignalType.INFECTION, new InfectionTrigger(0f).signalType());
        assertEquals(SituationSignalType.MISSING_KIN, new MissingKinTrigger("any").signalType());
        assertEquals(SituationSignalType.LOW_FOOD, new LowFoodTrigger(16).signalType());
        assertEquals(SituationSignalType.NIGHT, new NightTrigger(false).signalType());
    }

    @Test
    void parameterlessTriggersAlwaysMatch() {
        assertTrue(new RaidTrigger().matches(TriggerSignal.raid(null, 1)));
        // A death opens the situation whoever died; which villager may then raise it is decided by
        // VillagerDeathTrigger#relation at offer eligibility, where there is a candidate giver to ask about.
        assertTrue(new VillagerDeathTrigger("any").matches(TriggerSignal.villagerDeath(null, 1, null, null)));
        assertTrue(new MissingKinTrigger("any").matches(TriggerSignal.missingKin(null, 1, null)));
    }

    /**
     * A narrowed missing-kin trigger asks about a specific villager, and refuses when it cannot.
     *
     * <p>This used to return {@code true} unconditionally — the {@code relation} field was parsed and
     * never read — which is why {@code find_missing_child}, whose trigger says {@code "relation": "child"},
     * opened just as readily when a villager's spouse went missing. With no level and no villager in the
     * signal there is nothing to ask, so it fails closed rather than firing as if it had no filter.
     */
    @Test
    void narrowedMissingKinFailsClosedWhenItCannotTell() {
        assertFalse(new MissingKinTrigger("child").matches(TriggerSignal.missingKin(null, 1, null)));
        assertFalse(new MissingKinTrigger("child")
                .matches(TriggerSignal.missingKin(null, 1, java.util.UUID.randomUUID(), null)));
    }

    @Test
    void infectionRespectsMinProgress() {
        InfectionTrigger trigger = new InfectionTrigger(0.5f);
        assertFalse(trigger.matches(TriggerSignal.infection(null, 1, null, 0.4f)));
        assertTrue(trigger.matches(TriggerSignal.infection(null, 1, null, 0.5f)));
        assertTrue(trigger.matches(TriggerSignal.infection(null, 1, null, 0.9f)));
    }

    @Test
    void lowFoodFiresAtOrBelowThreshold() {
        LowFoodTrigger trigger = new LowFoodTrigger(16);
        assertTrue(trigger.matches(TriggerSignal.lowFood(null, 1, 0)));
        assertTrue(trigger.matches(TriggerSignal.lowFood(null, 1, 16)));
        assertFalse(trigger.matches(TriggerSignal.lowFood(null, 1, 17)));
    }

    @Test
    void nightRespectsFullMoonRequirement() {
        assertTrue(new NightTrigger(false).matches(TriggerSignal.night(null, 1, false)));
        assertTrue(new NightTrigger(false).matches(TriggerSignal.night(null, 1, true)));
        assertFalse(new NightTrigger(true).matches(TriggerSignal.night(null, 1, false)));
        assertTrue(new NightTrigger(true).matches(TriggerSignal.night(null, 1, true)));
    }

    @Test
    void signalFactoriesCarryTheirPayload() {
        UUID villager = UUID.randomUUID();
        TriggerSignal infection = TriggerSignal.infection(null, 7, villager, 0.3f);
        assertEquals(SituationSignalType.INFECTION, infection.type());
        assertEquals(7, infection.villageId());
        assertEquals(Optional.of(villager), infection.villager());
        assertEquals(0.3f, infection.fraction());

        UUID familyRoot = UUID.randomUUID();
        TriggerSignal missing = TriggerSignal.missingKin(null, 9, familyRoot);
        assertEquals(Optional.of(familyRoot), missing.familyRoot());
        assertEquals(Optional.empty(), missing.villager());

        assertEquals(42, TriggerSignal.lowFood(null, 2, 42).magnitude());
        assertTrue(TriggerSignal.night(null, 2, true).fullMoon());
    }
}
