package dev.otectus.mcaquests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.ProjectScopeSpec;
import dev.otectus.mcaquests.project.SharedRewardTarget;
import dev.otectus.mcaquests.project.SponsorDeathBehavior;
import dev.otectus.mcaquests.project.objective.ProjectTalkObjective;
import dev.otectus.mcaquests.quest.condition.leaf.VillageReputationCondition;
import dev.otectus.mcaquests.quest.reward.HeartsWithParticipantsReward;
import dev.otectus.mcaquests.quest.reward.UnlockReward;
import dev.otectus.mcaquests.quest.reward.VillageReputationReward;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic codec tests for the village-projects data model (no game launch / item registry needed).
 * Item/entity/block-target codecs depend on the bootstrapped registries, so — like
 * {@code McaConditionParsingTest} — these exercise the registry-free paths (scopes, enums, the talk
 * objective, reputation/xp rewards, conditions). The full datapack dispatch path is verified at
 * dev-runtime via {@code /reload} + {@code /mcaquests project validate}.
 */
class ProjectCodecTest {

    private static <T> boolean parses(Codec<T> codec, String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, element);
        return result.result().isPresent();
    }

    private static <T> void ok(Codec<T> codec, String json) {
        assertTrue(parses(codec, json), () -> "expected parse success: " + json);
    }

    private static <T> void fails(Codec<T> codec, String json) {
        assertTrue(!parses(codec, json), () -> "expected parse failure: " + json);
    }

    @Test
    void scopeSpecAcceptsBareStringAndObject() {
        ok(ProjectScopeSpec.CODEC, "\"village\"");
        ok(ProjectScopeSpec.CODEC, "\"family\"");
        ok(ProjectScopeSpec.CODEC, "{\"scope\":\"profession\",\"professions\":[\"minecraft:librarian\"]}");
        ok(ProjectScopeSpec.CODEC, "{\"scope\":\"village\",\"fallback_radius\":48}");
        fails(ProjectScopeSpec.CODEC, "\"kingdom\"");
        fails(ProjectScopeSpec.CODEC, "{\"scope\":\"kingdom\"}");

        DataResult<ProjectScopeSpec> bare = ProjectScopeSpec.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"village\""));
        assertEquals(ProjectScope.VILLAGE, bare.result().orElseThrow().scope());
    }

    @Test
    void enumCodecsNormaliseCaseAndRejectUnknown() {
        ok(ProjectScope.CODEC, "\"VILLAGE\"");
        ok(SponsorDeathBehavior.CODEC, "\"transfer\"");
        ok(SponsorDeathBehavior.CODEC, "\"turn_in_to_village\"");
        ok(SharedRewardTarget.CODEC, "\"top_contributor\"");
        fails(SponsorDeathBehavior.CODEC, "\"explode\"");
        fails(SharedRewardTarget.CODEC, "\"mayor\"");
    }

    @Test
    void newRewardAndConditionCodecsParse() {
        ok(VillageReputationReward.CODEC.codec(), "{\"amount\":5}");
        ok(UnlockReward.CODEC.codec(), "{\"target\":\"mcaquests:other_project\"}");
        ok(HeartsWithParticipantsReward.CODEC.codec(), "{\"amount\":12}");
        ok(HeartsWithParticipantsReward.CODEC.codec(), "{\"amount\":12,\"include_residents\":true}");
        ok(VillageReputationCondition.CODEC.codec(), "{\"min\":10}");
        ok(VillageReputationCondition.CODEC.codec(), "{\"min\":10,\"max\":50}");
        ok(VillageReputationCondition.CODEC.codec(), "{}");
    }

    @Test
    void talkObjectiveParses() {
        ok(ProjectTalkObjective.CODEC.codec(), "{\"profession\":\"minecraft:librarian\",\"count\":3}");
        ok(ProjectTalkObjective.CODEC.codec(), "{\"profession\":\"minecraft:librarian\"}"); // count defaults to 1
        fails(ProjectTalkObjective.CODEC.codec(), "{\"count\":3}"); // missing required profession
    }

    // The full ProjectDefinition.CODEC pulls in the item/entity/block-target codecs (via the donate/
    // kill/place objectives), which read the bootstrapped registries. Those aren't available in this
    // pure-logic harness, so the full dispatch parse — including the six built-in example projects — is
    // verified at dev-runtime via /reload + /mcaquests project validate (see DATAPACK.md).
}
