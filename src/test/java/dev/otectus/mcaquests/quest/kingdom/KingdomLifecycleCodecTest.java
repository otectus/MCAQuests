package dev.otectus.mcaquests.quest.kingdom;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomLifecycleCodecTest {
    @Test
    void everyModeAndStandingScopeParses() {
        for (String mode : new String[]{"offer_only", "bound_at_accept", "live", "fail_on_change"}) {
            for (String scope : new String[]{"local", "faction", "effective", "either", "both"}) {
                String reason = mode.equals("fail_on_change") ? ",\"failure_reason\":\"pack.reason\"" : "";
                var parsed = KingdomLifecycleSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {"mode":"%s"%s,"gate":{"subject":"giver_residence",
                        "include":["ultima_kingdoms:lunari"],"standing":{"scope":"%s","min":1}}}
                        """.formatted(mode, reason, scope))).result().orElseThrow();
                assertEquals(mode, parsed.mode().serializedName());
                assertEquals(scope, parsed.gate().orElseThrow().standing().orElseThrow()
                        .scope().serializedName());
            }
        }
    }

    @Test
    void malformedOrUnsafePoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> KingdomLifecycleSpec.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"mode\":\"fail_on_change\",\"gate\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> KingdomLifecycleSpec.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"civic_building\":{\"recovery\":\"fail_with_reason\"}}")));
        assertThrows(IllegalArgumentException.class, () -> KingdomGateSpec.fromJson(JsonParser.parseString(
                "{\"include\":[\"not canonical\"]}")));
    }

    @Test
    void absentUltimaOnlyAllowsExplicitInlineFallback() {
        assertFalse(KingdomGateSpec.fromJson(JsonParser.parseString("{}")).allowsWhenUnavailable());
        assertTrue(KingdomGateSpec.fromJson(JsonParser.parseString("{\"when_unknown\":\"allow\"}"))
                .allowsWhenUnavailable());
        assertFalse(KingdomGateSpec.fromJson(JsonParser.parseString(
                "{\"when_unknown\":\"allow\",\"standing\":{\"scope\":\"local\",\"min\":0}}"))
                .allowsWhenUnavailable());
    }
}
