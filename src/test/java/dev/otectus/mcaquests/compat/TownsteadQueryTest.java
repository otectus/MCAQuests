package dev.otectus.mcaquests.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Townstead query language's parse rules and comparison semantics (Townstead spec §4.2, §16.1).
 *
 * <p>These matter more than most codec tests: a datapack's meaning is defined by them, and unlike a
 * malformed field a <em>wrong</em> comparison is silent. Everything is exercised against real
 * {@code Townstead*View} records so path resolution is covered end to end rather than against a
 * stand-in map that could not catch an accessor rename.
 */
class TownsteadQueryTest {

    private static final TownsteadNeedsView HUNGRY_AND_TIRED =
            new TownsteadNeedsView(24, 1.5f, 0.25f, 80, 3, 0.1f, 14, false, false);

    private static final TownsteadScheduleView WORKING = new TownsteadScheduleView(
            "daily", "townstead:standard_default", false, false, 9, 9, 0, "work", "work",
            "townstead:standard_default", List.of(0, 0, 2), List.of("townstead:day_off"));

    private static final TownsteadVillagerView HANS = new TownsteadVillagerView(
            java.util.UUID.nameUUIDFromBytes("hans".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "Hans", "minecraft:villager", "townstead_roots:human", "adult", 420L, 31,
            false, false, false, "peppy", "minecraft:farmer", 2, 340, 0.5f,
            WORKING, HUNGRY_AND_TIRED,
            Map.of("eye_colour", "blue"), List.of("blue", "tall"),
            Map.of("townstead_roots:human", 1.0f));

    // ------------------------------------------------------------------------------------- parsing

    private static DataResult<TownsteadQuery> parse(String json) {
        return TownsteadQuery.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static TownsteadQuery ok(String json) {
        DataResult<TownsteadQuery> result = parse(json);
        assertTrue(result.result().isPresent(),
                "expected a valid query, got: " + result.error().map(Object::toString).orElse("?"));
        return result.result().orElseThrow();
    }

    private static void rejected(String json, String expectedFragment) {
        DataResult<TownsteadQuery> result = parse(json);
        assertTrue(result.error().isPresent(), "expected a parse error for: " + json);
        assertTrue(result.error().orElseThrow().message().contains(expectedFragment),
                "error should mention '" + expectedFragment + "' but was: "
                        + result.error().orElseThrow().message());
    }

    @Test
    void targetDefaultsToTheGiverAndMissingDefaultsToFalse() {
        TownsteadQuery query = ok("""
                {"source":"villager","path":"needs.hunger","operator":"lte","value":30}""");

        assertEquals(TownsteadTarget.GIVER, query.target());
        assertFalse(query.missing(),
                "An absent Townstead must make content ineligible, never accidentally satisfied.");
        assertEquals(List.of("needs", "hunger"), query.path());
    }

    @Test
    void pathLimitsAreEnforced() {
        rejected("""
                {"source":"villager","path":"a.b.c.d.e.f.g.h.i","operator":"exists"}""", "deeper than 8");
        rejected("""
                {"source":"villager","path":"needs..hunger","operator":"exists"}""", "empty segment");
        rejected("""
                {"source":"villager","path":"","operator":"exists"}""", "must not be empty");
        rejected("{\"source\":\"villager\",\"path\":\"" + "x".repeat(129) + "\",\"operator\":\"exists\"}",
                "longer than 128");
    }

    @Test
    void valuePresenceMustMatchTheOperator() {
        rejected("""
                {"source":"villager","path":"needs.hunger","operator":"lte"}""", "requires a 'value'");
        rejected("""
                {"source":"villager","path":"needs.hunger","operator":"exists","value":3}""",
                "takes no 'value'");
        rejected("""
                {"source":"villager","path":"personalityId","operator":"in","value":"peppy"}""",
                "requires 'value' to be an array");
        rejected("""
                {"source":"villager","path":"personalityId","operator":"eq","value":["a","b"]}""",
                "not an array");
    }

    @Test
    void invalidRegexIsRejectedAtParseTimeRatherThanAtEvaluation() {
        rejected("""
                {"source":"villager","path":"professionId","operator":"matches","value":"[unclosed"}""",
                "not a valid regular expression");
        rejected("{\"source\":\"villager\",\"path\":\"professionId\",\"operator\":\"matches\",\"value\":\""
                        + "a".repeat(257) + "\"}",
                "longer than 256");
    }

    @Test
    void unknownSourceOperatorAndTargetAreNamedInTheError() {
        rejected("""
                {"source":"weather","path":"x","operator":"exists"}""", "Unknown Townstead source");
        rejected("""
                {"source":"villager","path":"x","operator":"approximately","value":1}""",
                "Unknown Townstead operator");
        rejected("""
                {"source":"villager","target":"everyone","path":"x","operator":"exists"}""",
                "Unknown Townstead target");
    }

    // ---------------------------------------------------------------------------------- evaluation

    private static boolean test(String json, Object subject) {
        return ok(json).test(subject);
    }

    @Test
    void numericOperatorsWalkNestedRecordComponents() {
        assertTrue(test("""
                {"source":"villager","path":"needs.hunger","operator":"lte","value":30}""", HANS));
        assertFalse(test("""
                {"source":"villager","path":"needs.hunger","operator":"gt","value":30}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"needs.fatigue","operator":"gte","value":14}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"professionXp","operator":"lt","value":500}""", HANS));
    }

    /**
     * {@code energy} is not a stored field — it is the rising-scale inverse of Townstead's fatigue.
     * Pack authors should not have to know which of the two is a record component.
     */
    @Test
    void derivedAccessorsAreAddressableJustLikeStoredFields() {
        assertEquals(6, HUNGRY_AND_TIRED.energy());
        assertTrue(test("""
                {"source":"villager","path":"needs.energy","operator":"lte","value":6}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"schedule.onSchedule","operator":"eq","value":true}""", HANS));
    }

    @Test
    void numbersCompareByValueSoThirtyMatchesThirtyPointZero() {
        assertTrue(test("""
                {"source":"villager","path":"professionLevel","operator":"eq","value":2.0}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"fertility","operator":"eq","value":0.5}""", HANS));
    }

    @Test
    void stringsCompareCaseInsensitivelyAndBareIdsMatchTheMinecraftNamespace() {
        assertTrue(test("""
                {"source":"villager","path":"personalityId","operator":"eq","value":"PEPPY"}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"professionId","operator":"eq","value":"farmer"}""", HANS),
                "A bare id should match the minecraft-namespaced one.");
        assertTrue(test("""
                {"source":"villager","path":"professionId","operator":"eq","value":"minecraft:farmer"}""",
                HANS));
    }

    /**
     * The namespace shorthand must not fire on ordinary words, or {@code "work"} would start matching
     * {@code "minecraft:work"} and schedule comparisons would quietly widen.
     */
    @Test
    void theNamespaceShorthandDoesNotTurnPlainWordsIntoIds() {
        assertFalse(test("""
                {"source":"villager","path":"schedule.currentActivity","operator":"eq","value":"rest"}""",
                HANS));
        assertTrue(test("""
                {"source":"villager","path":"schedule.currentActivity","operator":"eq","value":"work"}""",
                HANS));
        assertFalse(test("""
                {"source":"villager","path":"rootId","operator":"eq","value":"human"}""", HANS),
                "A bare word must not match an id in a different namespace.");
    }

    @Test
    void containsWorksOnStringsListsAndMaps() {
        assertTrue(test("""
                {"source":"villager","path":"expressedAlleles","operator":"contains","value":"tall"}""",
                HANS));
        assertTrue(test("""
                {"source":"villager","path":"carriedVariants","operator":"contains","value":"eye_colour"}""",
                HANS), "On a map, contains tests the key.");
        assertTrue(test("""
                {"source":"villager","path":"professionId","operator":"contains","value":"farm"}""", HANS));
        assertFalse(test("""
                {"source":"villager","path":"expressedAlleles","operator":"contains","value":"short"}""",
                HANS));
    }

    @Test
    void inTestsAScalarAgainstAnArray() {
        assertTrue(test("""
                {"source":"villager","path":"personalityId","operator":"in","value":["shy","peppy"]}""",
                HANS));
        assertFalse(test("""
                {"source":"villager","path":"personalityId","operator":"in","value":["shy","grumpy"]}""",
                HANS));
        assertTrue(test("""
                {"source":"villager","path":"professionLevel","operator":"in","value":[1,2,3]}""", HANS));
    }

    @Test
    void matchesIsAnchoredAndCompiledOnce() {
        assertTrue(test("""
                {"source":"villager","path":"professionId","operator":"matches","value":"minecraft:.*er"}""",
                HANS));
        assertFalse(test("""
                {"source":"villager","path":"professionId","operator":"matches","value":"farmer"}""", HANS),
                "matches is a full match, so a bare substring must not satisfy it.");
    }

    @Test
    void indexedSegmentsWalkLists() {
        assertTrue(test("""
                {"source":"villager","path":"schedule.shifts.2","operator":"eq","value":2}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"heritage.townstead_roots:human","operator":"eq","value":1.0}""",
                HANS), "Map keys may themselves be resource ids.");
    }

    @Test
    void existsDistinguishesAResolvablePathFromAnUnknownOne() {
        assertTrue(test("""
                {"source":"villager","path":"needs.hunger","operator":"exists"}""", HANS));
        assertFalse(test("""
                {"source":"villager","path":"needs.morale","operator":"exists"}""", HANS));
        assertFalse(test("""
                {"source":"villager","path":"schedule.shifts.99","operator":"exists"}""", HANS));
    }

    // ------------------------------------------------------------------------------ missing policy

    @Test
    void anAbsentSubjectFollowsTheMissingPolicy() {
        assertFalse(test("""
                {"source":"villager","path":"needs.hunger","operator":"lte","value":30}""", null));
        assertTrue(test("""
                {"source":"villager","path":"needs.hunger","operator":"lte","value":30,"missing":true}""",
                null));
    }

    @Test
    void anUnresolvablePathFollowsTheMissingPolicyRatherThanReadingAsZero() {
        assertFalse(test("""
                {"source":"villager","path":"needs.morale","operator":"lte","value":30}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"needs.morale","operator":"lte","value":30,"missing":true}""",
                HANS));
    }

    /**
     * Comparing a non-numeric value with a numeric operator is a pack authoring mistake that cannot be
     * caught at parse time, because the path's type is not known until evaluation. It must read as
     * "unknown" and follow the missing policy, never as {@code 0}.
     */
    @Test
    void aNonNumericValueUnderANumericOperatorIsUnknownNotZero() {
        assertFalse(test("""
                {"source":"villager","path":"personalityId","operator":"lt","value":5}""", HANS));
        assertTrue(test("""
                {"source":"villager","path":"personalityId","operator":"lt","value":5,"missing":true}""",
                HANS));
    }

    // -------------------------------------------------------------------------------- round tripping

    @Test
    void queriesRoundTripThroughTheCodec() {
        TownsteadQuery query = ok("""
                {"source":"villager","target":"related","path":"needs.hunger","operator":"lte","value":30}""");

        JsonElement encoded = TownsteadQuery.CODEC.encodeStart(JsonOps.INSTANCE, query)
                .result().orElseThrow();
        TownsteadQuery again = TownsteadQuery.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(query, again);
        assertEquals("villager.needs.hunger lte 30", query.describe());
    }
}
