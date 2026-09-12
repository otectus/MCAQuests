package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadResidentRecordView;
import dev.otectus.mcaquests.project.objective.TownsteadResidentWellbeingProjectObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When Townstead's last-known record of an unloaded villager may count as a reading. The rule has
 * to be conservative by construction: a record is a snapshot with an age, not a simulation, so
 * anything a pack author did not explicitly accept is refused.
 */
class TownsteadResidentEvidenceTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");

    private static TownsteadResidentRecordView record(ResourceLocation dimension, int village, boolean alive,
                                                      long lastSeenDay) {
        return new TownsteadResidentRecordView(UUID.randomUUID(), "Ann", dimension, village, "minecraft:farmer", 1,
                new TownsteadNeedsView(80, 0f, 0f, 15, 0, 0f, 3, false, false), false, alive, 1000L, lastSeenDay);
    }

    @Test
    @DisplayName("is off unless the definition names a maximum age")
    void offByDefault() {
        assertFalse(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, true, 100), OVERWORLD, 3, 100, 0));
    }

    @Test
    @DisplayName("accepts a fresh, alive record of the judged village")
    void freshAliveSameVillage() {
        assertTrue(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, true, 98), OVERWORLD, 3, 100, 2));
        assertTrue(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, true, 100), OVERWORLD, 3, 100, 1));
    }

    @Test
    @DisplayName("refuses a record older than the accepted age")
    void stale() {
        assertFalse(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, true, 97), OVERWORLD, 3, 100, 2));
    }

    @Test
    @DisplayName("refuses the dead, the moved, another dimension's village and an unfiled villager")
    void membership() {
        assertFalse(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, false, 100), OVERWORLD, 3, 100, 5));
        assertFalse(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 4, true, 100), OVERWORLD, 3, 100, 5));
        assertFalse(TownsteadResidentEvidence.acceptable(record(NETHER, 3, true, 100), OVERWORLD, 3, 100, 5));
        assertFalse(TownsteadResidentEvidence.acceptable(record(null, 3, true, 100), OVERWORLD, 3, 100, 5));
    }

    @Test
    @DisplayName("refuses a record from the future, which can only mean a clock that was reset")
    void clockReset() {
        assertFalse(TownsteadResidentEvidence.acceptable(record(OVERWORLD, 3, true, 120), OVERWORLD, 3, 100, 5));
    }

    @Test
    @DisplayName("both objectives parse the field, and default it to off")
    void codecs() {
        TownsteadHealthyResidentsObjective personal = TownsteadHealthyResidentsObjective.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"minimum_observed\": 4}")).result().orElseThrow();
        assertEquals(0, personal.lastKnownMaxAgeDays());
        assertEquals(0.5D, personal.minimumLoadedFraction());
        TownsteadHealthyResidentsObjective opted = TownsteadHealthyResidentsObjective.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"minimum_observed\": 4, \"last_known_max_age_days\": 3}")).result().orElseThrow();
        assertEquals(3, opted.lastKnownMaxAgeDays());
        TownsteadResidentWellbeingProjectObjective project = TownsteadResidentWellbeingProjectObjective.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString("{\"last_known_max_age_days\": 2}")).result().orElseThrow();
        assertEquals(2, project.lastKnownMaxAgeDays());
        assertTrue(TownsteadHealthyResidentsObjective.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"last_known_max_age_days\": -1}")).error().isPresent(),
                "a negative age is a parse error, not a silent zero");
    }
}
