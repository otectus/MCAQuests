package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeWithVillagerCapitalRoleTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static TradeWithVillagerObjective parse(String json) {
        return TradeWithVillagerObjective.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
    }

    @Test
    void courtTradeCannotBeOfferedWithoutAnOfficeholder() {
        TradeWithVillagerObjective trade = parse(
                "{\"villager\":{\"mode\":\"capital_role\",\"role\":\"sovereign\"}}");
        assertTrue(trade.unofferableReason(new QuestContext(null, null, null, null, null)).isPresent());
    }

    @Test
    void ordinaryTradesRetainTheirExistingOfferAndSuspensionRules() {
        for (String json : new String[]{"{}", "{\"profession\":\"minecraft:farmer\"}",
                "{\"villager\":{\"mode\":\"self\"}}", "{\"villager\":{\"mode\":\"family\"}}"}) {
            TradeWithVillagerObjective trade = parse(json);
            assertTrue(trade.unofferableReason(null).isEmpty(), json);
            assertTrue(trade.unavailableReason(null, null, null, null).isEmpty(), json);
        }
    }

    @Test
    void misspelledCourtRoleCannotSilentlyBecomeATradeWithAnybody() {
        assertTrue(TradeWithVillagerObjective.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"villager\":{\"mode\":\"capital_role\",\"role\":\"sovereegn\"}}"))
                .error().isPresent());
    }
}
