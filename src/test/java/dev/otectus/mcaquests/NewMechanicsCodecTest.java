package dev.otectus.mcaquests;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.leaf.GiverDistanceFromVillageCondition;
import dev.otectus.mcaquests.quest.objective.EscortEntityObjective;
import dev.otectus.mcaquests.quest.objective.ReachLocationObjective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec coverage for the lead-escort rework ({@code lead} / {@code wait_distance}), the new
 * {@code reach_location} objective, and the {@code giver_distance_from_village} gate condition. Like the
 * other codec tests these exercise the record codecs directly with {@link JsonOps#INSTANCE} — no game or
 * registry bootstrap is needed (none of these touch a registry-backed id). The place-anchored
 * {@code defend_location} objective reuses the same {@code EntityTarget} codec as the shipped
 * {@code defend_villager}; full datapack dispatch with real item/entity ids is verified at dev-runtime
 * via {@code /reload}.
 */
class NewMechanicsCodecTest {

    private static boolean parses(Codec<?> codec, String json) {
        var result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        result.error().ifPresent(e -> System.out.println("[NewMechanicsCodecTest] parse error: " + e.message()));
        return result.result().isPresent();
    }

    @Test
    void escortEntityAcceptsLeadAndWaitDistance() {
        // legacy follow-mode (no lead field) still parses — backward compatible
        assertTrue(parses(EscortEntityObjective.CODEC,
                "{\"destination\":{\"anchor\":\"home_village\"},\"radius\":8}"));
        // lead-mode with explicit wait_distance
        assertTrue(parses(EscortEntityObjective.CODEC,
                "{\"villager\":{\"mode\":\"self\"},\"destination\":{\"anchor\":\"bed\"},"
                        + "\"radius\":4,\"lead\":true,\"wait_distance\":6}"));
        // lead a family member to the village
        assertTrue(parses(EscortEntityObjective.CODEC,
                "{\"villager\":{\"mode\":\"family\",\"relation\":\"child\"},"
                        + "\"destination\":{\"anchor\":\"home_village\"},\"radius\":8,\"lead\":true}"));
        // staged escort with an explicit stage_until_near override
        assertTrue(parses(EscortEntityObjective.CODEC,
                "{\"villager\":{\"mode\":\"family\",\"relation\":\"parent\"},"
                        + "\"destination\":{\"anchor\":\"bed\"},\"radius\":4,\"lead\":true,\"stage_until_near\":true}"));
    }

    @Test
    void reachLocationParses() {
        assertTrue(parses(ReachLocationObjective.CODEC,
                "{\"location\":{\"anchor\":\"home_village\"},\"radius\":12}"));
        assertTrue(parses(ReachLocationObjective.CODEC,
                "{\"location\":{\"anchor\":\"nearest_village\",\"radius\":160}}"));
    }

    @Test
    void giverDistanceFromVillageParsesAndValidates() {
        assertTrue(parses(GiverDistanceFromVillageCondition.CODEC, "{\"min_distance\":80}"));
        assertTrue(parses(GiverDistanceFromVillageCondition.CODEC,
                "{\"min_distance\":24,\"require_outside_border\":true}"));
        assertTrue(parses(GiverDistanceFromVillageCondition.CODEC, "{}"), "min_distance defaults to 0");
        // a negative distance is rejected at parse time
        assertFalse(parses(GiverDistanceFromVillageCondition.CODEC, "{\"min_distance\":-5}"));
    }
}
