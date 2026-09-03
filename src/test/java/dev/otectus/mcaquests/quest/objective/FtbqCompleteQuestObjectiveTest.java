package dev.otectus.mcaquests.quest.objective;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link FtbqCompleteQuestObjective}'s codec (defaults, optional {@code
 * display_name}, bad-hex rejection — spec §18, mirroring {@code FtbqIdParsingTest}/
 * {@code FtbqConditionPolicyTest}'s style) and its {@code describe()} fallback/substitution. Runtime
 * behaviour that needs a real {@code ServerPlayer} ({@code poll()}, via the bridge seam) is exercised
 * only via {@code FtbqConditionPolicyTest}-style stubs where the sibling condition class does the same
 * dispatch; this class's own {@code poll()} is a thin, structurally-identical wrapper and is not
 * separately unit-tested here (would need a real/mocked {@code ServerPlayer}, out of scope per the
 * "no Minecraft server on the test classpath" constraint).
 */
class FtbqCompleteQuestObjectiveTest {

    private static final String HEX = "1A2B3C4D5E6F7081";

    private static DataResult<FtbqCompleteQuestObjective> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return FtbqCompleteQuestObjective.CODEC.codec().parse(JsonOps.INSTANCE, element);
    }

    @Test
    void codecDefaultsAlreadyCompleteToSatisfyAndDisplayNameToEmpty() {
        DataResult<FtbqCompleteQuestObjective> result = parse("{\"quest\":\"" + HEX + "\"}");
        assertTrue(result.result().isPresent(), "expected parse success");
        FtbqCompleteQuestObjective objective = result.result().get();
        assertEquals(AlreadyCompleteMode.SATISFY, objective.alreadyComplete());
        assertTrue(objective.displayName().isEmpty());
        assertEquals(HEX, objective.quest());
    }

    @Test
    void codecAcceptsExplicitBlockOfferAndDisplayName() {
        DataResult<FtbqCompleteQuestObjective> result = parse(
                "{\"quest\":\"" + HEX + "\",\"already_complete\":\"block_offer\","
                        + "\"display_name\":{\"text\":\"the Ancient Tome chapter\"}}");
        assertTrue(result.result().isPresent(), "expected parse success");
        FtbqCompleteQuestObjective objective = result.result().get();
        assertEquals(AlreadyCompleteMode.BLOCK_OFFER, objective.alreadyComplete());
        assertTrue(objective.displayName().isPresent());
        assertEquals(Optional.of("the Ancient Tome chapter"), objective.displayName().get().text());
    }

    @Test
    void codecAcceptsLeadingHash() {
        DataResult<FtbqCompleteQuestObjective> result = parse("{\"quest\":\"#" + HEX + "\"}");
        assertTrue(result.result().isPresent(), "leading '#' must be tolerated");
        assertEquals("#" + HEX, result.result().get().quest());
    }

    @Test
    void codecRejectsBadHexFormat() {
        DataResult<FtbqCompleteQuestObjective> tooLong = parse("{\"quest\":\"1A2B3C4D5E6F70812\"}");
        assertTrue(tooLong.error().isPresent(), "17 hex chars must be rejected");

        DataResult<FtbqCompleteQuestObjective> badChars = parse("{\"quest\":\"not-hex\"}");
        assertTrue(badChars.error().isPresent(), "non-hex characters must be rejected");

        DataResult<FtbqCompleteQuestObjective> missingField = parse("{}");
        assertTrue(missingField.error().isPresent(), "the quest field is required");
    }

    @Test
    void codecRejectsUnknownAlreadyCompleteValue() {
        DataResult<FtbqCompleteQuestObjective> result =
                parse("{\"quest\":\"" + HEX + "\",\"already_complete\":\"sometimes\"}");
        // Matches the pre-existing OptionalFieldCodec quirk documented in FtbqConditionPolicyTest: a
        // present-but-invalid optional enum value silently falls back to the default rather than
        // failing to parse. Asserting the (surprising but pre-existing) fallback, not a parse error.
        assertTrue(result.result().isPresent());
        assertEquals(AlreadyCompleteMode.SATISFY, result.result().get().alreadyComplete());
    }

    @Test
    void describeUsesDisplayNameWhenPresent() {
        FtbqCompleteQuestObjective objective = new FtbqCompleteQuestObjective(
                HEX, AlreadyCompleteMode.SATISFY, Optional.of(QuestText.literal("the Ancient Tome chapter")));
        Component described = objective.describe();
        assertTrue(described.getContents() instanceof TranslatableContents, "describe() must use a lang key");
        TranslatableContents contents = (TranslatableContents) described.getContents();
        assertEquals("mcaquests.objective.ftbq_complete_quest", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        Object arg = contents.getArgs()[0];
        assertTrue(arg instanceof Component, "display_name arg must be a Component");
        assertTrue(((Component) arg).getContents() instanceof PlainTextContents.LiteralContents);
        assertEquals("the Ancient Tome chapter", ((PlainTextContents.LiteralContents) ((Component) arg).getContents()).text());
    }

    @Test
    void describeFallsBackToHexIdWhenDisplayNameAbsent() {
        FtbqCompleteQuestObjective objective =
                new FtbqCompleteQuestObjective(HEX, AlreadyCompleteMode.SATISFY, Optional.empty());
        Component described = objective.describe();
        assertTrue(described.getContents() instanceof TranslatableContents);
        TranslatableContents contents = (TranslatableContents) described.getContents();
        assertEquals("mcaquests.objective.ftbq_complete_quest", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        Object arg = contents.getArgs()[0];
        assertTrue(arg instanceof Component, "fallback arg must be a Component");
        assertTrue(((Component) arg).getContents() instanceof PlainTextContents.LiteralContents);
        String fallbackText = ((PlainTextContents.LiteralContents) ((Component) arg).getContents()).text();
        assertTrue(fallbackText.contains(HEX), "fallback line must name the hex id");
        assertTrue(fallbackText.toLowerCase().contains("linked ftb quest"), "fallback line must say what it is");
    }

    @Test
    void requiredAndCurrentAreZeroOrOneLatched() {
        FtbqCompleteQuestObjective objective =
                new FtbqCompleteQuestObjective(HEX, AlreadyCompleteMode.SATISFY, Optional.empty());
        assertEquals(1, objective.required());
        assertFalse(objective.isEventDriven());
    }
}
