package dev.otectus.mcaquests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.leaf.ReputationTierCondition;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry-free codec round-trips for the 0.7.0 progression data model. */
class ProgressionCodecTest {

    @Test
    void tierRoundTrip() {
        ReputationTier tier = new ReputationTier("honored", 150, "Honored",
                Optional.of(new ResourceLocation("mcaquests", "honored_of_village")));
        DataResult<JsonElement> encoded = ReputationTier.CODEC.encodeStart(JsonOps.INSTANCE, tier);
        ReputationTier decoded = ReputationTier.CODEC.parse(JsonOps.INSTANCE, encoded.result().orElseThrow())
                .result().orElseThrow();
        assertEquals(tier, decoded);
    }

    @Test
    void tierSetDecodesFromDocumentedJson() {
        String json = """
                {
                  "tiers": [
                    { "id": "stranger", "threshold": 0, "name": "Stranger" },
                    { "id": "friend", "threshold": 75, "name": "Friend", "grants_title": "mcaquests:village_friend" }
                  ]
                }
                """;
        ReputationTierSet set = ReputationTierSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
        assertEquals(2, set.tiers().size());
        assertEquals("stranger", set.tiers().get(0).id());
        assertTrue(set.tiers().get(0).grantsTitle().isEmpty());
        assertEquals(new ResourceLocation("mcaquests", "village_friend"),
                set.tiers().get(1).grantsTitle().orElseThrow());
    }

    @Test
    void reputationTierConditionRoundTrip() {
        ReputationTierCondition cond = new ReputationTierCondition("friend", Optional.of("honored"),
                Optional.of(new ResourceLocation("mcaquests", "default")));
        DataResult<JsonElement> encoded = ReputationTierCondition.CODEC.codec().encodeStart(JsonOps.INSTANCE, cond);
        ReputationTierCondition decoded = ReputationTierCondition.CODEC.codec()
                .parse(JsonOps.INSTANCE, encoded.result().orElseThrow()).result().orElseThrow();
        assertEquals(cond, decoded);
    }

    @Test
    void reputationTierConditionDecodesMinimalJson() {
        ReputationTierCondition cond = ReputationTierCondition.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{ \"min_tier\": \"friend\" }"))
                .result().orElseThrow();
        assertEquals("friend", cond.minTier());
        assertTrue(cond.maxTier().isEmpty());
        assertTrue(cond.ladder().isEmpty());
    }

    @Test
    void tierSetEncodeDecodeEquality() {
        ReputationTierSet set = new ReputationTierSet(List.of(
                new ReputationTier("a", 0, "A", Optional.empty()),
                new ReputationTier("b", 10, "B", Optional.empty())));
        JsonElement json = ReputationTierSet.CODEC.encodeStart(JsonOps.INSTANCE, set).result().orElseThrow();
        ReputationTierSet decoded = ReputationTierSet.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(set, decoded);
    }
}
