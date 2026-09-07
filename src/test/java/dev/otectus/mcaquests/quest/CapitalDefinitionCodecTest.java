package dev.otectus.mcaquests.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationOffer;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Leaf codec errors must survive the optional fields on complete datapack definitions. */
class CapitalDefinitionCodecTest {
    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    void invalidFemaleTitleRejectsQuestAndSituationInsteadOfDroppingRewards() {
        rejectsBoth("rewards", """
                [{"type":"mcaquests:capital_title","title":"knight","female_title":"queen"}]
                """, "female_title");
    }

    @Test
    void invalidVillagerTitleTargetRejectsQuestAndSituation() {
        rejectsBoth("rewards", """
                [{"type":"mcaquests:capital_villager_title","title":"lord",
                  "villager":{"mode":"capital_role","role":"archduke"}}]
                """, "archduke");
    }

    @Test
    void invalidDiplomacyGateRejectsQuestAndSituationInsteadOfUngatingThem() {
        rejectsBoth("conditions", """
                {"type":"mcaquests:capital_relation","state":["alliannce"]}
                """, "alliannce");
    }

    @Test
    void invalidCapitalObjectiveSelectorRejectsQuestAndSituation() {
        rejectsBoth("objectives", """
                [{"type":"mcaquests:trade_with_villager",
                  "villager":{"mode":"capital_role","role":"sovereegn"}}]
                """, "sovereegn");
    }

    @Test
    void malformedOptionalContainersAreErrors() {
        rejectsBoth("objectives", "{}", "objectives");
        rejectsBoth("rewards", "{}", "rewards");
        rejectsBoth("conditions", "[]", "conditions");
    }

    @Test
    void absentAndExplicitEmptyFieldsKeepTheirDefaults() {
        for (JsonObject body : List.of(new JsonObject(), JsonParser.parseString("""
                {"objectives":[],"rewards":[]}
                """).getAsJsonObject())) {
            QuestDefinition quest = quest(body).result().orElseThrow();
            SituationOffer offer = situation(body).result().orElseThrow().offer();
            assertTrue(quest.objectives().isEmpty());
            assertTrue(quest.rewards().isEmpty());
            assertTrue(quest.conditions().isEmpty());
            assertTrue(offer.objectives().isEmpty());
            assertTrue(offer.rewards().isEmpty());
            assertTrue(offer.conditions().isEmpty());
        }
    }

    @Test
    void validCapitalFieldsSurviveFullDefinitionRoundTrips() {
        JsonObject body = JsonParser.parseString("""
                {"objectives":[{"type":"mcaquests:trade_with_villager",
                   "villager":{"mode":"capital_role","role":"herald"}}],
                 "rewards":[{"type":"mcaquests:capital_title","title":"knight","female_title":"dame"}],
                 "conditions":{"type":"mcaquests:capital_relation","state":["alliance"]}}
                """).getAsJsonObject();
        QuestDefinition quest = quest(body).result().orElseThrow();
        SituationDefinition situation = situation(body).result().orElseThrow();
        assertEquals(1, quest.objectives().size());
        assertEquals(1, quest.rewards().size());
        assertTrue(quest.conditions().isPresent());
        assertEquals(quest.objectives(), situation.offer().objectives());
        assertEquals(quest.rewards(), situation.offer().rewards());
        assertEquals(quest.conditions(), situation.offer().conditions());
        assertEquals(quest, QuestDefinition.CODEC.parse(JsonOps.INSTANCE,
                QuestDefinition.CODEC.encodeStart(JsonOps.INSTANCE, quest).result().orElseThrow()).result().orElseThrow());
        assertEquals(situation, SituationDefinition.CODEC.parse(JsonOps.INSTANCE,
                SituationDefinition.CODEC.encodeStart(JsonOps.INSTANCE, situation).result().orElseThrow()).result().orElseThrow());
    }

    private static void rejectsBoth(String field, String json, String expectedError) {
        JsonObject body = new JsonObject();
        body.add(field, JsonParser.parseString(json));
        for (DataResult<?> result : List.of(quest(body), situation(body))) {
            String error = result.error().orElseThrow(() -> new AssertionError(
                    "invalid " + field + " was silently accepted: " + body)).message();
            assertTrue(error.contains(field), error);
            assertTrue(error.contains(expectedError), error);
            assertTrue(result.result().isEmpty(), "invalid content must not become a valid empty definition");
        }
    }

    private static DataResult<QuestDefinition> quest(JsonObject body) {
        JsonObject root = body.deepCopy();
        root.addProperty("id", "test:capital");
        root.add("giver", new JsonObject());
        root.add("dialogue", new JsonObject());
        return QuestDefinition.CODEC.parse(JsonOps.INSTANCE, root);
    }

    private static DataResult<SituationDefinition> situation(JsonObject body) {
        JsonObject root = JsonParser.parseString("""
                {"id":"test:capital","trigger":{"type":"mcaquests:capital_war"}}
                """).getAsJsonObject();
        root.add("offer", body.deepCopy());
        return SituationDefinition.CODEC.parse(JsonOps.INSTANCE, root);
    }
}
