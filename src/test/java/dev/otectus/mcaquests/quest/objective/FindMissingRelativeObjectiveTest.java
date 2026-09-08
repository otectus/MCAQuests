package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.data.ObjectiveValidator;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The objective that materialises a relative MCA knows about but who is nowhere in the world. */
class FindMissingRelativeObjectiveTest {

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded(); // the validator reads strictJsonValidation
    }

    private static final String MINIMAL = "{\"relative\":{\"mode\":\"family\",\"relation\":\"child\"}}";

    private static FindMissingRelativeObjective parse(String json) {
        DataResult<FindMissingRelativeObjective> result =
                FindMissingRelativeObjective.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.result().orElseThrow(() ->
                new AssertionError("expected " + json + " to parse: " + result.error().orElseThrow().message()));
    }

    @Nested
    @DisplayName("the codec")
    class Codec {

        @Test
        @DisplayName("only the relative is required; everything else has a documented default")
        void defaults() {
            FindMissingRelativeObjective objective = parse(MINIMAL);

            assertEquals(96, objective.minDistance(), "the default must keep the search out of the home village");
            assertEquals(24, objective.discoverRadius());
            assertEquals(12, objective.spawnDistance());
            assertTrue(objective.biome().isEmpty(), "an unhinted search is legitimate and must not be forced");
            assertTrue(objective.structure().isEmpty());
        }

        @Test
        @DisplayName("a fully specified objective round-trips through JSON")
        void roundTrips() {
            FindMissingRelativeObjective original = parse("""
                    {"relative":{"mode":"family","relation":"sibling"},
                     "structure":{"structure_tag":"minecraft:mineshaft"},
                     "min_distance":64,"discover_radius":16,"spawn_distance":8}""");

            JsonElement encoded = FindMissingRelativeObjective.CODEC
                    .encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();

            assertEquals(original, FindMissingRelativeObjective.CODEC
                            .parse(JsonOps.INSTANCE, encoded).result().orElseThrow(),
                    "a datapack value must survive being written back out and re-read");
        }

        @Test
        @DisplayName("the relative is required")
        void relativeIsRequired() {
            assertTrue(FindMissingRelativeObjective.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_distance\":64}"))
                            .error().isPresent(),
                    "with nobody to look for the objective could never complete, so it must fail at load");
        }

        @Test
        @DisplayName("an invalid discovery radius is rejected while an omitted radius keeps its default")
        void outOfRangeOptionalIsRejected() {
            var invalid = FindMissingRelativeObjective.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"relative\":{\"mode\":\"family\"},\"discover_radius\":0}"));
            assertTrue(invalid.error().isPresent());
            assertTrue(invalid.result().isEmpty());
            assertEquals(24, parse("{\"relative\":{\"mode\":\"family\"}}").discoverRadius());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        private List<String> errorsFor(String json) {
            List<String> errors = new ArrayList<>();
            parse(json).validate(new ResourceLocation("mcaquests", "test"), 0, errors);
            return errors;
        }

        @Test
        @DisplayName("a well-formed objective validates clean")
        void clean() {
            assertTrue(errorsFor(MINIMAL).isEmpty());
        }

        @Test
        @DisplayName("targeting the giver is rejected — the giver is never the one who is missing")
        void selfIsRejected() {
            List<String> errors = errorsFor("{\"relative\":{\"mode\":\"self\"}}");

            assertEquals(1, errors.size(), "one bad field should produce exactly one message");
            assertTrue(errors.get(0).contains("self"), "the message must name the mode the author wrote");
        }

        @Test
        @DisplayName("an unknown relation is caught")
        void unknownRelation() {
            assertEquals(1, errorsFor("{\"relative\":{\"mode\":\"family\",\"relation\":\"cousin\"}}").size());
        }

        @Test
        @DisplayName("placing the relative beyond the discovery radius is worth a warning")
        void spawnBeyondDiscoveryWarns() {
            assertTrue(parse(MINIMAL).spawnsWithinDiscoveryRange(),
                    "the shipped defaults must not warn: 12 blocks out, discovered within 24");
            assertTrue(parse(withRanges(24, 24)).spawnsWithinDiscoveryRange(), "equal is inside");
            assertFalse(parse(withRanges(32, 24)).spawnsWithinDiscoveryRange());

            assertTrue(warningsFor(parse(MINIMAL)).isEmpty(), "the defaults must load silently");
            List<String> warnings = warningsFor(parse(withRanges(32, 24)));
            assertEquals(1, warnings.size(), warnings.toString());
            assertTrue(warnings.get(0).contains("walk toward them"), warnings.get(0));
        }

        /** The load-time warnings a one-objective pack containing {@code objective} produces. */
        private List<String> warningsFor(FindMissingRelativeObjective objective) {
            GiverSpec giver = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
            ResourceLocation id = new ResourceLocation("testpack", "find_missing_relative");
            QuestDefinition def = new QuestDefinition(id, true, 1, Optional.empty(), Optional.empty(),
                    RepeatRule.DEFAULT, giver, Map.of(), List.of(objective), List.of(),
                    TurnInSpec.DEFAULT, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), OfferShaping.NONE, QuestReputationBlock.NONE);
            List<String> warnings = new ArrayList<>();
            ObjectiveValidator.validate(new LinkedHashMap<>(Map.of(id, def)), new ArrayList<>(), warnings);
            return warnings;
        }

        private String withRanges(int spawnDistance, int discoverRadius) {
            return "{\"relative\":{\"mode\":\"family\",\"relation\":\"child\"},\"spawn_distance\":"
                    + spawnDistance + ",\"discover_radius\":" + discoverRadius + "}";
        }
    }

    @Nested
    @DisplayName("discovery")
    class Discovery {

        /**
         * The rule the poll applies to a relative who is in the world. Materialising one used to
         * complete the objective outright, from wherever the player happened to be standing.
         */
        @Test
        @DisplayName("a relative is found only once the player is inside the discovery radius")
        void radiusDecidesDiscovery() {
            FindMissingRelativeObjective objective = parse(MINIMAL); // discover_radius 24

            assertTrue(objective.isDiscovered(true, 24.0D * 24.0D), "the radius itself counts");
            assertTrue(objective.isDiscovered(true, 4.0D));
            assertFalse(objective.isDiscovered(true, 25.0D * 25.0D));
        }

        @Test
        @DisplayName("a spawn at the default distance is not itself a discovery until the poll checks")
        void spawnDistanceIsNotCredit() {
            FindMissingRelativeObjective objective = parse(MINIMAL); // spawn_distance 12
            double spawned = (double) objective.spawnDistance() * objective.spawnDistance();

            assertTrue(objective.isDiscovered(true, spawned),
                    "the default spawn lands inside the radius, so the very next poll credits it");
            assertFalse(objective.isDiscovered(false, spawned),
                    "a dead relative is never found, however close the body is");
        }

        @Test
        @DisplayName("a spawn beyond the radius has to be walked to")
        void distantSpawnMustBeApproached() {
            FindMissingRelativeObjective objective =
                    parse("{\"relative\":{\"mode\":\"family\",\"relation\":\"child\"},"
                            + "\"spawn_distance\":40,\"discover_radius\":16}");

            assertFalse(objective.isDiscovered(true, 40.0D * 40.0D), "no credit where they appear");
            assertTrue(objective.isDiscovered(true, 10.0D * 10.0D), "credit once the player closes in");
        }
    }

    @Nested
    @DisplayName("where the relative appears")
    class SpawnPlacement {

        @Test
        @DisplayName("the same seed always gives the same spot")
        void deterministic() {
            assertEquals(FindMissingRelativeObjective.spawnOffset(12345L, 12),
                    FindMissingRelativeObjective.spawnOffset(12345L, 12),
                    "a reconnect mid-search must not teleport the relative to the other side of the player");
        }

        @Test
        @DisplayName("the offset lands at roughly the requested distance")
        void respectsDistance() {
            for (long seed = 0; seed < 200; seed++) {
                FindMissingRelativeObjective.Vec3iOffset offset =
                        FindMissingRelativeObjective.spawnOffset(seed, 12);
                double distance = Math.sqrt(offset.x() * offset.x() + (double) offset.z() * offset.z());
                assertTrue(Math.abs(distance - 12) <= 1.0D,
                        "seed " + seed + " produced distance " + distance + "; rounding to whole blocks may"
                                + " shift it by well under a block, never further");
            }
        }

        @Test
        @DisplayName("offsets are spread around the player, not biased to one bearing")
        void coversEveryQuadrant() {
            Set<String> quadrants = new HashSet<>();
            for (long seed = 0; seed < 200; seed++) {
                FindMissingRelativeObjective.Vec3iOffset offset =
                        FindMissingRelativeObjective.spawnOffset(seed, 20);
                quadrants.add((offset.x() >= 0 ? "+" : "-") + (offset.z() >= 0 ? "+" : "-"));
            }
            assertEquals(4, quadrants.size(),
                    "the relative should be found in any direction; a bias would make every search feel"
                            + " identical and could always point players the same way out of a village");
        }
    }
}
