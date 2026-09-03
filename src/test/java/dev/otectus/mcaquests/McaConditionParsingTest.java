package dev.otectus.mcaquests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.leaf.AgeGroupCondition;
import dev.otectus.mcaquests.quest.condition.leaf.HasHomeCondition;
import dev.otectus.mcaquests.quest.condition.leaf.HealthBelowCondition;
import dev.otectus.mcaquests.quest.condition.leaf.InfectedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.IsFamilyMemberCondition;
import dev.otectus.mcaquests.quest.condition.leaf.IsPlayerSpouseCondition;
import dev.otectus.mcaquests.quest.condition.leaf.MoodCondition;
import dev.otectus.mcaquests.quest.condition.leaf.PersonalityCondition;
import dev.otectus.mcaquests.quest.condition.leaf.RelatedVillagerStatusCondition;
import dev.otectus.mcaquests.quest.condition.leaf.RelationshipStateCondition;
import dev.otectus.mcaquests.quest.condition.leaf.VillageMemberCondition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 validation demonstration: each new MCA-aware condition codec accepts the canonical syntax,
 * and bad field values produce a parse error (lenient mode = the loader skips + logs the quest;
 * strict mode {@code strictJsonValidation} = hard error). Each condition's own {@code CODEC} is
 * exercised directly so the test stays pure-logic (no game bootstrap / Forge runtime); the
 * {@code all_of}/{@code any_of}/{@code not} composition is pre-existing behaviour covered by
 * {@code QuestLogicTest}, and the full datapack dispatch path is verified at dev-runtime via
 * {@code /reload}.
 */
class McaConditionParsingTest {

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
    void validConditionsParse() {
        ok(IsPlayerSpouseCondition.CODEC.codec(), "{}");
        ok(RelationshipStateCondition.CODEC.codec(), "{\"states\":[\"married_to_player\",\"engaged\"]}");
        ok(IsFamilyMemberCondition.CODEC.codec(), "{\"relation\":\"child\"}");
        ok(IsFamilyMemberCondition.CODEC.codec(), "{}"); // relation defaults to "any"
        ok(AgeGroupCondition.CODEC.codec(), "{\"groups\":[\"adult\",\"teen\"]}");
        ok(PersonalityCondition.CODEC.codec(), "{\"personalities\":[\"friendly\"]}");
        ok(MoodCondition.CODEC.codec(), "{\"min\":0,\"moods\":[\"happy\"]}");
        ok(MoodCondition.CODEC.codec(), "{\"max\":-5}");
        ok(VillageMemberCondition.CODEC.codec(), "{}");
        ok(HasHomeCondition.CODEC.codec(), "{\"value\":false}");
        ok(HealthBelowCondition.CODEC.codec(), "{\"threshold\":0.5}");
        ok(InfectedCondition.CODEC.codec(), "{}");
        ok(RelatedVillagerStatusCondition.CODEC.codec(), "{\"relation\":\"child\",\"status\":\"missing\"}");
    }

    @Test
    void caseIsNormalisedAndDefaultsApply() {
        // Enum-like values are lowercased on parse, matching the snapshot's lowercased state.
        DataResult<AgeGroupCondition> upper =
                AgeGroupCondition.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{\"groups\":[\"ADULT\"]}"));
        assertTrue(upper.result().isPresent(), "uppercase value accepted");
        assertEquals("adult", upper.result().get().groups().get(0));

        DataResult<IsFamilyMemberCondition> defaulted =
                IsFamilyMemberCondition.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{}"));
        assertEquals("any", defaulted.result().get().relation());
    }

    @Test
    void badFieldValuesAreRejected() {
        fails(AgeGroupCondition.CODEC.codec(), "{\"groups\":[\"elder\"]}");   // no ELDER state in MCA
        fails(AgeGroupCondition.CODEC.codec(), "{\"groups\":[]}");            // empty list
        fails(AgeGroupCondition.CODEC.codec(), "{}");                          // missing required field
        fails(RelationshipStateCondition.CODEC.codec(), "{\"states\":[\"divorced\"]}");
        fails(PersonalityCondition.CODEC.codec(), "{\"personalities\":[\"chaotic\"]}");
        fails(IsFamilyMemberCondition.CODEC.codec(), "{\"relation\":\"cousin\"}");
        fails(RelatedVillagerStatusCondition.CODEC.codec(), "{\"relation\":\"cousin\",\"status\":\"missing\"}");
        fails(RelatedVillagerStatusCondition.CODEC.codec(), "{\"relation\":\"child\",\"status\":\"reanimated\"}");
        fails(HealthBelowCondition.CODEC.codec(), "{\"threshold\":1.5}");     // out of (0,1]
        fails(HealthBelowCondition.CODEC.codec(), "{\"threshold\":0}");       // out of (0,1]
        fails(InfectedCondition.CODEC.codec(), "{\"min_progress\":2.0}");     // out of [0,1]
        fails(MoodCondition.CODEC.codec(), "{}");                             // needs at least one field
    }
}
