package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectScopeSpec;
import dev.otectus.mcaquests.project.SharedReward;
import dev.otectus.mcaquests.project.SharedRewardTarget;
import dev.otectus.mcaquests.project.SponsorSpec;
import dev.otectus.mcaquests.project.objective.DonateItemObjective;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.leaf.HeartsCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObtainItemObjective;
import dev.otectus.mcaquests.quest.reward.CurrencyReward;
import dev.otectus.mcaquests.quest.reward.EffectReward;
import dev.otectus.mcaquests.quest.reward.ItemReward;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A malformed nested rule must not turn into a smaller payment or a broader eligibility gate. */
class NestedOptionalCodecTest {
    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    private record Malformed(Codec<?> codec, String json) { }

    @Test
    void nestedPaymentAndEligibilityFieldsRejectInvalidPresentValues() {
        for (Malformed fixture : List.of(
                new Malformed(SponsorSpec.CODEC, "{\"professions\":42}"),
                new Malformed(SponsorSpec.CODEC, "{\"on_death\":\"typo\"}"),
                new Malformed(SponsorSpec.CODEC, "{\"pinned_sponsors\":[\"invalid uuid\"]}"),
                new Malformed(ProjectScopeSpec.CODEC, "{\"scope\":\"profession\",\"professions\":42}"),
                new Malformed(SharedReward.CODEC, "{\"reward\":{\"type\":\"mcaquests:xp\",\"amount\":3},\"target\":\"top_contributer\"}"),
                new Malformed(ItemDeliveryObjective.CODEC.codec(), "{\"item\":\"minecraft:apple\",\"count\":0}"),
                new Malformed(ItemDeliveryObjective.CODEC.codec(), "{\"item\":\"minecraft:apple\",\"consume\":\"typo\"}"),
                new Malformed(DeliverToVillagerObjective.CODEC.codec(), "{\"recipient\":{\"mode\":\"self\"},\"item\":\"minecraft:apple\",\"count\":-1}"),
                new Malformed(DeliverToVillagerObjective.CODEC.codec(), "{\"recipient\":{\"mode\":\"self\"},\"item\":\"minecraft:apple\",\"destination\":42}"),
                new Malformed(ObtainItemObjective.CODEC.codec(), "{\"item\":\"minecraft:apple\",\"count\":\"typo\"}"),
                new Malformed(DonateItemObjective.CODEC.codec(), "{\"item\":\"minecraft:apple\",\"per_player_cap\":-1}"),
                new Malformed(ItemReward.CODEC.codec(), "{\"item\":\"minecraft:apple\",\"count\":0}"),
                new Malformed(CurrencyReward.CODEC.codec(), "{\"difficulty\":\"typo\"}"),
                new Malformed(EffectReward.CODEC.codec(), "{\"effect\":\"minecraft:speed\",\"duration\":0}"),
                new Malformed(HeartsCondition.CODEC.codec(), "{\"min\":\"typo\"}"),
                new Malformed(QuestCompletedCondition.CODEC.codec(), "{\"quest\":\"test:previous\",\"scope\":\"typo\"}"),
                new Malformed(VillagerTarget.CODEC, "{\"mode\":\"self\",\"profession\":42}"),
                new Malformed(ItemTarget.MAP_CODEC.codec(), "{\"tag\":\"minecraft:planks\",\"item\":\"missingmod:unknown\"}"),
                new Malformed(SourceHint.CODEC, "{\"biome\":42}"))) {
            // Each fixture's final field is the malformed optional. Its absence must still load,
            // proving rejection is caused by that field rather than an unrelated broken fixture.
            var omitted = JsonParser.parseString(fixture.json()).getAsJsonObject();
            String lastField = omitted.keySet().stream().reduce((first, next) -> next).orElseThrow();
            omitted.remove(lastField);
            assertTrue(fixture.codec().parse(JsonOps.INSTANCE, omitted).result().isPresent(),
                    "valid omitted-field baseline: " + fixture.json());
            assertRejected(fixture.codec(), fixture.json());
        }
    }

    @Test
    void omittedNestedDefaultsAndValidDefinitionsStillRoundTrip() {
        assertEquals(SponsorSpec.ANY, parse(SponsorSpec.CODEC, "{}"));
        ItemDeliveryObjective delivery = parse(ItemDeliveryObjective.CODEC.codec(), "{\"item\":\"minecraft:apple\"}");
        assertEquals(1, delivery.count());
        assertTrue(delivery.consume());
        assertFalse(delivery.destination().isTransfer());
        assertEquals(SharedRewardTarget.CONTRIBUTORS, parse(SharedReward.CODEC,
                "{\"reward\":{\"type\":\"mcaquests:xp\",\"amount\":3}}").target());
        assertTrue(parse(HeartsCondition.CODEC.codec(), "{}").min().isEmpty());
        assertTrue(parse(HeartsCondition.CODEC.codec(), "{}").max().isEmpty());

        var quest = parse(QuestDefinition.CODEC, "{\"id\":\"test:nested\",\"giver\":{},\"dialogue\":{},"
                + "\"objectives\":[{\"type\":\"mcaquests:item_delivery\",\"item\":\"minecraft:apple\",\"count\":2,\"consume\":false}],"
                + "\"conditions\":{\"type\":\"mcaquests:hearts\",\"min\":5}}");
        JsonElement encoded = QuestDefinition.CODEC.encodeStart(JsonOps.INSTANCE, quest).result().orElseThrow();
        assertEquals(quest, QuestDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void outerDefinitionsCannotHideInvalidInnerFields() {
        assertRejected(QuestDefinition.CODEC, "{\"id\":\"test:nested\",\"giver\":{},\"dialogue\":{},"
                + "\"objectives\":[{\"type\":\"mcaquests:item_delivery\",\"item\":\"minecraft:apple\",\"count\":0}]}");
        assertRejected(QuestDefinition.CODEC, "{\"id\":\"test:nested\",\"giver\":{},\"dialogue\":{},"
                + "\"conditions\":{\"type\":\"mcaquests:hearts\",\"min\":\"typo\"}}");
        assertRejected(ProjectDefinition.CODEC, "{\"id\":\"test:nested\",\"scope\":\"village\","
                + "\"sponsor\":{\"professions\":42},\"phases\":[{}]}");
    }

    @Test
    void unknownDefaultedRegistryEntriesDoNotBecomeAir() {
        assertRejected(ItemDeliveryObjective.CODEC.codec(), "{\"item\":\"missingmod:unknown\"}");
        assertRejected(ItemReward.CODEC.codec(), "{\"item\":\"missingmod:unknown\"}");
        assertRejected(SourceHint.CODEC, "{\"block\":\"missingmod:unknown\"}");
        assertTrue(parse(ItemTarget.MAP_CODEC.codec(), "{\"item\":\"minecraft:air\"}").item().isPresent(),
                "explicit registered air remains valid for existing datapacks");
    }

    private static void assertRejected(Codec<?> codec, String json) {
        var result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.error().isPresent(), json);
        assertTrue(result.result().isEmpty(), json);
    }

    private static <T> T parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }
}
