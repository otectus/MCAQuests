package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two things about Townstead objectives that a player would notice if they broke: a frozen
 * baseline that quietly moves, and a quest that dies because a mod was uninstalled.
 *
 * <p>The whole suite runs with Townstead genuinely absent — it is not on the test classpath at all —
 * so the suspension assertions here exercise the real absent path rather than a mock of it.
 */
class TownsteadObjectiveTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static <T> DataResult<T> parse(com.mojang.serialization.Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static <T> T ok(com.mojang.serialization.Codec<T> codec, String json) {
        DataResult<T> result = parse(codec, json);
        assertTrue(result.result().isPresent(),
                "expected a valid definition, got: " + result.error().map(Object::toString).orElse("?"));
        return result.result().orElseThrow();
    }

    // ------------------------------------------------------------------------------------ baselines

    @Nested
    @DisplayName("a frozen baseline")
    class Baselines {

        @Test
        @DisplayName("is written once and never overwritten")
        void isWrittenOnce() {
            ObjectiveProgress progress = new ObjectiveProgress();
            UUID villager = UUID.randomUUID();

            assertTrue(TownsteadBaseline.freeze(progress, "villager", "needs.hunger", villager, 20, 100L),
                    "the first freeze should take");
            assertFalse(TownsteadBaseline.freeze(progress, "villager", "needs.hunger", villager, 95, 200L),
                    "a second freeze must be refused, or feeding the villager would move the start line");

            assertEquals(20.0D, TownsteadBaseline.number(progress).orElseThrow());
            assertEquals(100L, TownsteadBaseline.tick(progress).orElseThrow());
        }

        /**
         * The case the whole design exists for: a restart must not re-baseline. If it did, a quest to
         * raise hunger by 45 would restart measuring from the villager's current, already-improved value
         * and could never be finished — or, worse, would complete for free on a decrease objective.
         */
        @Test
        @DisplayName("survives a save and load unchanged")
        void survivesARoundTrip() {
            ObjectiveProgress progress = new ObjectiveProgress();
            UUID villager = UUID.randomUUID();
            TownsteadBaseline.freeze(progress, "villager", "needs.hunger", villager, 20, 100L);
            progress.setCount(7);

            CompoundTag saved = progress.save();
            ObjectiveProgress loaded = ObjectiveProgress.load(saved);

            assertTrue(TownsteadBaseline.isFrozen(loaded));
            assertEquals(20.0D, TownsteadBaseline.number(loaded).orElseThrow());
            assertEquals(villager, TownsteadBaseline.target(loaded).orElseThrow());
            assertEquals(7, loaded.count());
            assertFalse(TownsteadBaseline.freeze(loaded, "villager", "needs.hunger", villager, 95, 300L),
                    "a loaded baseline is still frozen; a post-restart poll must not re-take it");
        }

        @Test
        @DisplayName("refuses to be compared against a different question")
        void refusesAMismatchedQuestion() {
            ObjectiveProgress progress = new ObjectiveProgress();
            TownsteadBaseline.freeze(progress, "villager", "needs.hunger", null, 20, 100L);

            assertTrue(TownsteadBaseline.matches(progress, "villager", "needs.hunger"));
            assertFalse(TownsteadBaseline.matches(progress, "villager", "needs.fatigue"),
                    "a datapack edited under a live quest must not compare hunger against fatigue");
        }

        @Test
        @DisplayName("writes nothing into progress that never froze one")
        void leavesUntouchedProgressAlone() {
            ObjectiveProgress progress = new ObjectiveProgress(3);

            assertFalse(TownsteadBaseline.isFrozen(progress));
            assertEquals(OptionalDouble.empty(), TownsteadBaseline.number(progress));
            assertFalse(progress.save().contains("extra"),
                    "an ordinary objective must serialise exactly as it always did");
        }
    }

    // ----------------------------------------------------------------------------------- suspension

    @Nested
    @DisplayName("with Townstead absent")
    class Suspension {

        private final TownsteadStateObjective objective = ok(TownsteadStateObjective.CODEC, """
                {"source":"villager","path":"schedule.currentActivity","operator":"eq","value":"work",
                 "hold_ticks":600}""");

        @Test
        @DisplayName("every Townstead objective reports itself unavailable")
        void reportsUnavailable() {
            assertTrue(objective.unavailableReason(null, null, new ObjectiveProgress(), null).isPresent(),
                    "an absent Townstead must suspend the objective, not silently satisfy or fail it");
            assertFalse(objective.townsteadReady());
        }

        /**
         * Suspension must never be mistaken for completion. {@code isSatisfied} is deliberately left
         * answering from stored progress alone — the guard lives in {@code QuestManager.isComplete}, so
         * that one predicate covers ready toasts, self-complete, settleProgress and turn-in together.
         */
        @Test
        @DisplayName("stored progress is preserved rather than reset")
        void preservesProgress() {
            ObjectiveProgress progress = new ObjectiveProgress();
            progress.addElapsed(400L);

            assertEquals(20, objective.current(null, progress),
                    "20 of the 30 held seconds must still be there when Townstead comes back");
            assertFalse(objective.isSatisfied(null, progress));
        }

        @Test
        @DisplayName("the declared capabilities are exactly what the query reads")
        void declaresPreciseCapabilities() {
            assertEquals(java.util.Set.of(TownsteadCapability.READ_VILLAGER,
                            TownsteadCapability.READ_SCHEDULE),
                    objective.requiredCapabilities(),
                    "a schedule query must not claim needs or profession, or one missing accessor would "
                            + "suspend quests that never touched it");

            TownsteadStateObjective hunger = ok(TownsteadStateObjective.CODEC, """
                    {"source":"villager","path":"needs.hunger","operator":"gte","value":60}""");
            assertEquals(java.util.Set.of(TownsteadCapability.READ_VILLAGER,
                    TownsteadCapability.READ_NEEDS), hunger.requiredCapabilities());
        }
    }

    // --------------------------------------------------------------------------------------- codecs

    @Nested
    @DisplayName("definitions")
    class Codecs {

        @Test
        @DisplayName("parse with Townstead absent, so a pack always loads")
        void parseWithoutTownstead() {
            ok(TownsteadChangeObjective.CODEC, """
                    {"source":"villager","path":"needs.hunger","operator":"gte","value":0,
                     "direction":"increase","amount":45,"minimum_final":70}""");
            ok(TownsteadBuildingRegisteredObjective.CODEC, """
                    {"building_type":"dock","minimum_level":2,"count":1}""");
            ok(TownsteadHealthyResidentsObjective.CODEC, """
                    {"minimum_observed":4,"minimum_fraction":0.75,"hunger_min":60,"hold_ticks":1200}""");
            ok(TownsteadSpiritProgressObjective.CODEC, """
                    {"spirit":"industrious","points_delta":60}""");
        }

        @Test
        @DisplayName("reject a goal that says two contradictory things")
        void rejectContradictoryGoals() {
            assertTrue(parse(TownsteadProfessionProgressObjective.CODEC, """
                    {"profession":"minecraft:farmer","xp_delta":100,"target_tier":3}""")
                    .error().isPresent(), "a relative and an absolute goal cannot both apply");
            assertTrue(parse(TownsteadProfessionProgressObjective.CODEC, """
                    {"profession":"minecraft:farmer"}""")
                    .error().isPresent(), "a profession objective with no goal is meaningless");
            assertTrue(parse(TownsteadSpiritProgressObjective.CODEC, """
                    {"points_delta":10,"target_tier":2}""")
                    .error().isPresent());
        }

        @Test
        @DisplayName("carry sensible defaults")
        void carryDefaults() {
            TownsteadBuildingRegisteredObjective building =
                    ok(TownsteadBuildingRegisteredObjective.CODEC, """
                            {"building_type":"wool_shed"}""");

            assertEquals(1, building.count());
            assertEquals(1, building.minimumLevel());
            assertTrue(building.requireNewOrUpgraded(),
                    "\"build us one\" is the common case; merely having one completes instantly");

            TownsteadProfessionProgressObjective profession =
                    ok(TownsteadProfessionProgressObjective.CODEC, """
                            {"profession":"minecraft:farmer","xp_delta":120}""");
            assertTrue(profession.requireCurrentProfession());
            assertEquals(120, profession.required());
        }

        @Test
        @DisplayName("hold progress reads in seconds, not ticks")
        void holdIsCountedInSeconds() {
            TownsteadStateObjective objective = ok(TownsteadStateObjective.CODEC, """
                    {"source":"villager","path":"needs.hunger","operator":"gte","value":60,
                     "hold_ticks":1200}""");

            assertEquals(60, objective.required(), "1200 ticks is a minute, and should read as 60");
        }

        @Test
        @DisplayName("an unknown building tier suffix is treated as no tier, not as a parse failure")
        void tierParsingIsForgiving() {
            assertEquals(1, dev.otectus.mcaquests.compat.TownsteadBuildingView.levelOf("pen"));
            assertEquals(3, dev.otectus.mcaquests.compat.TownsteadBuildingView.levelOf("dock_l3"));
            assertEquals("dock", dev.otectus.mcaquests.compat.TownsteadBuildingView.familyOf("dock_l3"));
            assertEquals("wool_shed", dev.otectus.mcaquests.compat.TownsteadBuildingView.familyOf("wool_shed"));
            assertEquals("odd_lx", dev.otectus.mcaquests.compat.TownsteadBuildingView.familyOf("odd_lx"),
                    "a non-numeric suffix is part of the name, not a tier");
        }
    }

    @Test
    @DisplayName("an optional capability id is parsed case-insensitively and validated")
    void capabilityIdsAreValidated() {
        assertEquals(Optional.of(TownsteadCapability.READ_NEEDS),
                TownsteadCapability.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"read_needs\""))
                        .result());
        assertEquals(Optional.of(TownsteadCapability.READ_NEEDS),
                TownsteadCapability.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"READ_NEEDS\""))
                        .result());
        assertTrue(TownsteadCapability.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString("\"read_vibes\"")).error().isPresent(),
                "a typo'd capability gate must fail the reload, not silently gate on nothing");
    }
}
