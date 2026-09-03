package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The objective that materialises a relative MCA knows about but who is nowhere in the world. */
class FindMissingRelativeObjectiveTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final String MINIMAL = "{\"relative\":{\"mode\":\"family\",\"relation\":\"child\"}}";

    private static FindMissingRelativeObjective parse(String json) {
        DataResult<FindMissingRelativeObjective> result =
                FindMissingRelativeObjective.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
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

            JsonElement encoded = FindMissingRelativeObjective.CODEC.codec()
                    .encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();

            assertEquals(original, FindMissingRelativeObjective.CODEC.codec()
                            .parse(JsonOps.INSTANCE, encoded).result().orElseThrow(),
                    "a datapack value must survive being written back out and re-read");
        }

        @Test
        @DisplayName("the relative is required")
        void relativeIsRequired() {
            assertTrue(FindMissingRelativeObjective.CODEC.codec()
                            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"min_distance\":64}"))
                            .error().isPresent(),
                    "with nobody to look for the objective could never complete, so it must fail at load");
        }

        @Test
        @DisplayName("an out-of-range optional falls back to its default rather than failing the quest")
        void outOfRangeOptionalFallsBackToTheDefault() {
            // DFU's optionalFieldOf cannot tell "absent" from "present but invalid", so a bad value reads
            // as absent. That is how every optional field in the objective layer already behaves; pinned
            // here so nobody later reads the intRange bound as a load-time guarantee.
            assertEquals(24, parse("{\"relative\":{\"mode\":\"family\"},\"discover_radius\":0}").discoverRadius(),
                    "an unusable radius must degrade to the default, never to a zero that cannot complete");
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
