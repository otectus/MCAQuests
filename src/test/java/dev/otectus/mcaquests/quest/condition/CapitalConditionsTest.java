package dev.otectus.mcaquests.quest.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.capitals.CapitalRole;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.NoopCapitalsBridge;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalAllegianceCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalInterregnumCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalPresentCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalRelationCondition;
import dev.otectus.mcaquests.quest.condition.leaf.CapitalRoleCondition;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five MCA Capitals condition leaves, read the way a datapack writes them.
 *
 * <p>Every case parses through {@link ConditionTypes#CODEC} rather than the per-type codec, because
 * that is the path a quest file takes: a leaf whose codec is not a {@code MapCodecCodec} loses its
 * fields under a nested {@code "value"} key when dispatched, and the defaults below would then all
 * silently apply.
 */
class CapitalConditionsTest {

    @AfterEach
    void resetCompat() {
        CompatRegistry.get().clearForTest();
    }

    private static void capabilities(Set<CapitalsCapability> capabilities) {
        NoopCapitalsBridge absent = new NoopCapitalsBridge(false);
        CapitalsCompat compat = new CapitalsCompat();
        compat.setBridgeForTest((CapitalsBridge) Proxy.newProxyInstance(
                CapitalsBridge.class.getClassLoader(), new Class<?>[]{CapitalsBridge.class},
                (proxy, method, args) -> method.getName().equals("has")
                        ? capabilities.contains(args[0]) : method.invoke(absent, args)));
        CompatRegistry.get().register(compat);
    }

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST = new ResourceLocation("mcaquests", "test");

    private static QuestCondition parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<QuestCondition> result = ConditionTypes.CODEC.parse(JsonOps.INSTANCE, element);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected " + json + " to parse, but got: " + result.error().orElseThrow().message()));
    }

    private static String error(String json) {
        DataResult<QuestCondition> result =
                ConditionTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.error()
                .orElseThrow(() -> new AssertionError("expected " + json + " to be rejected"))
                .message();
    }

    /** A context with nothing in it: the state a condition is in when the giver has gone. */
    private static QuestContext emptyContext() {
        return new QuestContext(null, null, null, QUEST, null);
    }

    @Test
    @DisplayName("all five types are registered under the mcaquests namespace")
    void registered() {
        assertEquals("mcaquests:capital_present", ConditionTypes.CAPITAL_PRESENT.id().toString());
        assertEquals("mcaquests:capital_role", ConditionTypes.CAPITAL_ROLE.id().toString());
        assertEquals("mcaquests:capital_allegiance", ConditionTypes.CAPITAL_ALLEGIANCE.id().toString());
        assertEquals("mcaquests:capital_relation", ConditionTypes.CAPITAL_RELATION.id().toString());
        assertEquals("mcaquests:capital_interregnum", ConditionTypes.CAPITAL_INTERREGNUM.id().toString());
    }

    @Test
    @DisplayName("capital_present defaults to the giver's village being a seat")
    void presentDefaults() {
        CapitalPresentCondition condition = assertInstanceOf(CapitalPresentCondition.class,
                parse("{\"type\":\"mcaquests:capital_present\"}"));

        assertEquals(CapitalPresentCondition.Subject.GIVER, condition.subject());
        assertTrue(condition.present());
        assertEquals(CapitalPresentCondition.Subject.PLAYER_VILLAGE,
                ((CapitalPresentCondition) parse(
                        "{\"type\":\"mcaquests:capital_present\",\"subject\":\"player_village\"}")).subject());
        assertTrue(error("{\"type\":\"mcaquests:capital_present\",\"subject\":\"town\"}")
                .contains("Unknown capital subject: 'town' (expected giver/player_village)"),
                "a misspelled subject must fail the load rather than fall back to the default");
    }

    @Test
    @DisplayName("capital_role defaults to the player, in the giver's own court")
    void roleDefaults() {
        CapitalRoleCondition condition = assertInstanceOf(CapitalRoleCondition.class,
                parse("{\"type\":\"mcaquests:capital_role\",\"role\":\"knight\"}"));

        assertEquals(CapitalRoleCondition.Subject.PLAYER, condition.subject());
        assertEquals(CapitalRoleCondition.Scope.GIVER, condition.capital());
        assertEquals(CapitalRole.KNIGHT, condition.role());
        assertTrue(condition.present());
    }

    @Test
    @DisplayName("capital_role rejects a rank its subject can never hold")
    void roleRejectsImpossibleSubject() {
        assertTrue(error("{\"type\":\"mcaquests:capital_role\",\"subject\":\"player\",\"role\":\"herald\"}")
                .contains("a player cannot hold the role 'herald'"));
        assertTrue(error("{\"type\":\"mcaquests:capital_role\",\"subject\":\"giver\",\"role\":\"archduke\"}")
                .contains("a villager cannot hold the role 'archduke'"));
        assertFalse(((CapitalRoleCondition) parse("{\"type\":\"mcaquests:capital_role\","
                + "\"subject\":\"player\",\"role\":\"knight\",\"present\":false,\"capital\":\"any\"}"))
                .present());
    }

    @Test
    @DisplayName("capital_allegiance defaults to the giver's own capital")
    void allegianceDefaults() {
        CapitalAllegianceCondition condition = assertInstanceOf(CapitalAllegianceCondition.class,
                parse("{\"type\":\"mcaquests:capital_allegiance\"}"));

        assertEquals(CapitalAllegianceCondition.Match.GIVER, condition.match());
        assertTrue(condition.present());
        assertEquals(CapitalAllegianceCondition.Match.ANY,
                ((CapitalAllegianceCondition) parse(
                        "{\"type\":\"mcaquests:capital_allegiance\",\"match\":\"any\"}")).match());
    }

    @Test
    @DisplayName("capital_relation takes a list of states and defaults to any other capital")
    void relationDefaults() {
        CapitalRelationCondition condition = assertInstanceOf(CapitalRelationCondition.class,
                parse("{\"type\":\"mcaquests:capital_relation\",\"state\":[\"alliance\",\"truce\"]}"));

        assertEquals(CapitalRelationCondition.Other.ANY, condition.other());
        assertEquals(List.of("alliance", "truce"), condition.states());
        assertTrue(condition.present());
        assertEquals(CapitalRelationCondition.Other.ALLEGIANCE,
                ((CapitalRelationCondition) parse("{\"type\":\"mcaquests:capital_relation\","
                        + "\"other\":\"allegiance\",\"state\":[\"war\"]}")).other());
    }

    @Test
    @DisplayName("capital_relation honors its documented present field and preserves it on encode")
    void relationCanBeNegated() {
        CapitalRelationCondition condition = (CapitalRelationCondition) parse(
                "{\"type\":\"mcaquests:capital_relation\",\"state\":[\"war\"],\"present\":false}");
        assertFalse(condition.present());
        assertEquals(condition, ConditionTypes.CODEC.parse(JsonOps.INSTANCE,
                ConditionTypes.CODEC.encodeStart(JsonOps.INSTANCE, condition).result().orElseThrow())
                .result().orElseThrow());
        assertTrue(error("{\"type\":\"mcaquests:capital_relation\",\"state\":[\"war\"],\"present\":\"false\"}")
                .contains("present"));
    }

    @Test
    @DisplayName("capital_relation rejects a state Capitals does not record, and an empty list")
    void relationRejectsUnknownState() {
        assertTrue(error("{\"type\":\"mcaquests:capital_relation\",\"state\":[\"feud\"]}")
                .contains("Unknown capital diplomatic state"));
        assertTrue(error("{\"type\":\"mcaquests:capital_relation\",\"state\":[]}")
                .contains("must not be empty"));
    }

    @Test
    @DisplayName("capital_interregnum defaults to true and is usually written false")
    void interregnumDefaults() {
        assertTrue(((CapitalInterregnumCondition) parse("{\"type\":\"mcaquests:capital_interregnum\"}"))
                .present());
        assertFalse(((CapitalInterregnumCondition) parse(
                "{\"type\":\"mcaquests:capital_interregnum\",\"present\":false}")).present());
    }

    @Test
    @DisplayName("with no giver capital the observation is false, and 'present': false therefore passes")
    void noCapitalRespectsPresent() {
        capabilities(Set.of(CapitalsCapability.REGISTRY, CapitalsCapability.INTERREGNUM,
                CapitalsCapability.DIPLOMACY));
        assertFalse(parse("{\"type\":\"mcaquests:capital_present\"}").test(emptyContext()));
        assertTrue(parse("{\"type\":\"mcaquests:capital_present\",\"present\":false}").test(emptyContext()));
        assertFalse(parse("{\"type\":\"mcaquests:capital_interregnum\"}").test(emptyContext()));
        assertTrue(parse("{\"type\":\"mcaquests:capital_interregnum\",\"present\":false}")
                .test(emptyContext()));
        assertFalse(parse("{\"type\":\"mcaquests:capital_relation\",\"state\":[\"war\"]}")
                .test(emptyContext()),
                "a positive relation condition still requires a matching relation");
        assertTrue(parse("{\"type\":\"mcaquests:capital_relation\",\"state\":[\"war\"],\"present\":false}")
                .test(emptyContext()));
    }

    @Test
    @DisplayName("negative capital conditions cannot turn absent, disabled or missing capabilities into eligibility")
    void absentCapabilitiesDoNotMatchNegativeConditions() {
        List<String> negative = List.of(
                "{\"type\":\"mcaquests:capital_present\",\"present\":false}",
                "{\"type\":\"mcaquests:capital_role\",\"role\":\"knight\",\"present\":false}",
                "{\"type\":\"mcaquests:capital_role\",\"subject\":\"giver\",\"role\":\"herald\",\"present\":false}",
                "{\"type\":\"mcaquests:capital_allegiance\",\"present\":false}",
                "{\"type\":\"mcaquests:capital_interregnum\",\"present\":false}",
                "{\"type\":\"mcaquests:capital_relation\",\"state\":[\"war\"],\"present\":false}");
        for (boolean disabled : List.of(false, true)) {
            CapitalsCompat compat = new CapitalsCompat();
            compat.setBridgeForTest(new NoopCapitalsBridge(disabled));
            CompatRegistry.get().register(compat);
            for (String json : negative) {
                assertFalse(parse(json).test(emptyContext()), json);
            }
        }
        capabilities(Set.of(CapitalsCapability.REGISTRY));
        for (String json : negative.subList(1, negative.size())) {
            assertFalse(parse(json).test(emptyContext()), "registry alone cannot satisfy " + json);
        }
    }
}
