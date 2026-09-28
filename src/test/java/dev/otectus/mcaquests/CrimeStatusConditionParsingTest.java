package dev.otectus.mcaquests;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.leaf.CrimeStatusCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code mcaquests:crime_status} accepts the documented syntax and rejects the rest (1.7.1). */
class CrimeStatusConditionParsingTest {

    private static DataResult<CrimeStatusCondition> parse(String json) {
        return CrimeStatusCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void acceptsEveryDocumentedField() {
        CrimeStatusCondition parsed = parse("{\"wanted\": true, \"band\": \"outlaw\", \"jailed\": false, \"min_heat\": 40}")
                .result().orElseThrow();
        assertEquals(true, parsed.wanted().orElseThrow());
        assertEquals("outlaw", parsed.band().orElseThrow());
        assertEquals(false, parsed.jailed().orElseThrow());
        assertEquals(40L, parsed.minHeat().orElseThrow());
        assertTrue(parse("{\"wanted\": false}").result().isPresent());
    }

    @Test
    void rejectsAnEmptyConditionAnUnknownBandAndNegativeHeat() {
        assertTrue(parse("{}").error().isPresent(), "constraining nothing is a typo");
        assertTrue(parse("{\"band\": \"red\"}").error().isPresent(), "bands are the legal words, not Crime's colours");
        assertTrue(parse("{\"min_heat\": -1}").error().isPresent());
    }

    @Test
    void roundTripsThroughTheCodec() {
        CrimeStatusCondition parsed = parse("{\"band\": \"lawful\", \"min_heat\": 0}").result().orElseThrow();
        var encoded = CrimeStatusCondition.CODEC.encodeStart(JsonOps.INSTANCE, parsed).result().orElseThrow();
        assertEquals(parsed, parse(encoded.toString()).result().orElseThrow());
    }
}
