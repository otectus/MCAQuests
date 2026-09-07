package dev.otectus.mcaquests.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class CapitalsQuestRequirementsTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @AfterEach
    void reset() { CompatRegistry.get().clearForTest(); }

    private static QuestDefinition quest(String fields) {
        JsonObject json = JsonParser.parseString("{\"id\":\"test:court\",\"giver\":{},\"dialogue\":{},"
                + fields + "}").getAsJsonObject();
        QuestDefinition def = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        if (json.has("rewards")) assertEquals(json.getAsJsonArray("rewards").size(), def.rewards().size());
        if (json.has("objectives")) assertEquals(json.getAsJsonArray("objectives").size(), def.objectives().size());
        if (json.has("conditions")) assertTrue(def.conditions().isPresent());
        return def;
    }

    @Test
    void rewardOnlyDependenciesBlockCompletionAndResumeWithoutReevaluatingPolitics() {
        QuestDefinition def = quest("""
                "rewards":[{"type":"mcaquests:capital_title","title":"knight"}],
                "conditions":{"type":"mcaquests:capital_role","subject":"player","role":"knight","present":false}
                """);
        EnumSet<CapitalsCapability> capabilities = EnumSet.allOf(CapitalsCapability.class);
        assertTrue(CapitalsQuestRequirements.available(def, capabilities::contains));
        capabilities.remove(CapitalsCapability.TITLE_GRANTS);
        assertFalse(CapitalsQuestRequirements.available(def, capabilities::contains));
        capabilities.add(CapitalsCapability.TITLE_GRANTS);
        assertTrue(CapitalsQuestRequirements.available(def, capabilities::contains));

        // These early exits exercise the actual lifecycle with an absent mod, before any server reads.
        CompatRegistry.get().clearForTest();
        assertFalse(QuestManager.isComplete(null, def, null));
        assertTrue(QuestManager.suspensionReason(null, def, null).isPresent());
    }

    @Test
    void everyCapitalRewardRequiresItsWriteCapabilityAndRegistry() {
        String[] rewards = {
                "{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\"}",
                "{\"type\":\"mcaquests:capital_chronicle\",\"key\":\"test.entry\"}",
                "{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"lord\"}"
        };
        CapitalsCapability[] writes = { CapitalsCapability.TITLE_GRANTS, CapitalsCapability.CHRONICLE,
                CapitalsCapability.VILLAGER_TITLES };
        for (int i = 0; i < rewards.length; i++) {
            QuestDefinition def = quest("\"rewards\":[" + rewards[i] + "]");
            EnumSet<CapitalsCapability> caps = EnumSet.of(CapitalsCapability.REGISTRY, writes[i]);
            assertTrue(CapitalsQuestRequirements.available(def, caps::contains));
            caps.remove(writes[i]);
            assertFalse(CapitalsQuestRequirements.available(def, caps::contains));
            caps.add(writes[i]);
            caps.remove(CapitalsCapability.REGISTRY);
            assertFalse(CapitalsQuestRequirements.available(def, caps::contains));
        }
    }

    @Test
    void roleTargetsInsideObjectivesRewardsAndAnchorsRequireRoles() {
        for (String fields : new String[] {
                "\"objectives\":[{\"type\":\"mcaquests:protect_entity\",\"villager\":{\"mode\":\"capital_role\",\"role\":\"heir\"}}]",
                "\"objectives\":[{\"type\":\"mcaquests:reach_location\",\"location\":{\"anchor\":\"bed\",\"villager\":{\"mode\":\"capital_role\",\"role\":\"sovereign\"}}}]",
                "\"rewards\":[{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"lord\",\"villager\":{\"mode\":\"capital_role\",\"role\":\"herald\"}}]"
        }) {
            QuestDefinition def = quest(fields);
            assertTrue(CapitalsQuestRequirements.available(def, c -> true));
            assertFalse(CapitalsQuestRequirements.available(def, c -> c != CapitalsCapability.ROLES));
        }
    }

    @Test
    void optionalAlternativesAndExplicitMissingModConditionsKeepWorking() {
        for (String condition : new String[] {
                "{\"type\":\"mcaquests:compat_capability\",\"provider\":\"mcacapitals\",\"capability\":\"capitals.registry\",\"present\":false}",
                "{\"not\":{\"type\":\"mcaquests:compat_capability\",\"provider\":\"mcacapitals\",\"capability\":\"capitals.registry\"}}",
                "{\"any_of\":[{\"type\":\"mcaquests:capital_present\"},{\"type\":\"mcaquests:quest_completed\",\"quest\":\"test:alternative\"}]}"
        }) {
            assertTrue(CapitalsQuestRequirements.available(quest("\"conditions\":" + condition), c -> false));
        }
        assertFalse(CapitalsQuestRequirements.available(quest("""
                "conditions":{"all_of":[{"type":"mcaquests:capital_present"},{"type":"mcaquests:quest_completed","quest":"test:alternative"}]}
                """), c -> false));
        assertTrue(CapitalsQuestRequirements.available(quest("\"rewards\":[]"), c -> false));
    }

    @Test
    void missingCourtPackAndSyntheticSituationAreAttributedToCapitals() {
        CompatRegistry.get().register(new CapitalsCompat());
        for (ResourceLocation id : new ResourceLocation[] {
                ResourceLocation.fromNamespaceAndPath("mcaquests", "compat/capitals/royal_escort"),
                SituationIds.syntheticId(ResourceLocation.fromNamespaceAndPath("mcaquests", "capitals_drums_of_war")),
                SituationIds.syntheticId(ResourceLocation.fromNamespaceAndPath("mcaquests", "capitals_the_empty_throne"))
        }) assertEquals(new CapitalsCompat().displayName(), QuestManager.compatSuspensionSubject(id).orElseThrow());
        assertFalse(CapitalsQuestRequirements.isBundled(ResourceLocation.fromNamespaceAndPath("test", "compat/capitals/custom")));
        assertTrue(QuestManager.compatSuspensionSubject(ResourceLocation.fromNamespaceAndPath("test", "ordinary")).isEmpty());
    }
}
