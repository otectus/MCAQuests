package dev.otectus.mcaquests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.RegistryKind;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.quest.template.ResolvedValue;
import dev.otectus.mcaquests.quest.template.TemplateSpec;
import dev.otectus.mcaquests.quest.template.TemplateVariable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage of the quest-template system: variable codec parsing/validation, the {@code kind}
 * dispatch round-trip, whole-token JSON substitution, the {@link TemplateSpec} block shape, and the NBT
 * round-trip that keeps an accepted template's chosen values frozen across restart. Like
 * {@code McaConditionParsingTest}, these exercise codecs directly so no Forge/game bootstrap is needed;
 * the full datapack dispatch + in-world resolution is verified at dev-runtime via {@code /reload}.
 */
class TemplateResolutionTest {

    private static boolean parses(Codec<?> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isPresent();
    }

    @Test
    void variablesParseAndValidate() {
        // Good shapes.
        assertTrue(parses(TemplateVariable.CODEC, "{\"kind\":\"int\",\"min\":2,\"max\":9}"));
        assertTrue(parses(TemplateVariable.CODEC, "{\"kind\":\"int\",\"min\":4,\"max\":4,\"per_player_level\":0.5,\"limit\":40}"));
        assertTrue(parses(TemplateVariable.CODEC, "{\"kind\":\"item\",\"ids\":[\"minecraft:wheat\"]}"));
        assertTrue(parses(TemplateVariable.CODEC, "{\"kind\":\"item\",\"tags\":[\"minecraft:fishes\"]}"));
        assertTrue(parses(TemplateVariable.CODEC, "{\"kind\":\"text\",\"options\":[{\"text\":\"hi\"}]}"));

        // Bad shapes are rejected at parse time.
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"int\",\"min\":9,\"max\":2}"));        // max < min
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"int\",\"min\":5,\"max\":9,\"limit\":3}")); // limit < min
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"text\",\"options\":[]}"));            // empty pool
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"item\"}"));                            // no ids or tags
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"dimension\",\"tags\":[\"a:b\"]}"));    // dimensions have no tags
        assertFalse(parses(TemplateVariable.CODEC, "{\"kind\":\"bogus\"}"));                           // unknown kind
    }

    @Test
    void variableKindRoundTrips() {
        for (String json : new String[]{
                "{\"kind\":\"int\",\"min\":2,\"max\":9}",
                "{\"kind\":\"item\",\"ids\":[\"minecraft:wheat\",\"minecraft:carrot\"]}",
                "{\"kind\":\"text\",\"options\":[{\"text\":\"hi\"}]}"}) {
            TemplateVariable decoded = TemplateVariable.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result().orElseThrow();
            JsonElement encoded = TemplateVariable.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow();
            TemplateVariable reDecoded = TemplateVariable.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
            assertEquals(decoded, reDecoded, () -> "round-trip changed: " + json);
            assertEquals(decoded.kindKey(), reDecoded.kindKey());
        }
    }

    @Test
    void substitutionReplacesWholeTokens() {
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        values.put("crop", new ResolvedValue.IdValue(RegistryKind.ITEM, new ResourceLocation("minecraft:wheat")));
        values.put("count", new ResolvedValue.IntValue(12));
        PlaceholderResolver resolver = new PlaceholderResolver(new ResolvedTemplate(values));

        JsonElement out = resolver.substitute(JsonParser.parseString(
                "{\"type\":\"mcaquests:item_delivery\",\"item\":\"{crop}\",\"count\":\"{count}\"}"));

        assertEquals("minecraft:wheat", out.getAsJsonObject().get("item").getAsString());
        // An int variable substitutes as a JSON number, so the unchanged objective codec parses it.
        assertTrue(out.getAsJsonObject().get("count").getAsJsonPrimitive().isNumber());
        assertEquals(12, out.getAsJsonObject().get("count").getAsInt());
        // An unknown token is left verbatim so the later parse fails loudly (validation catches it).
        JsonElement untouched = resolver.substitute(JsonParser.parseString("{\"item\":\"{missing}\"}"));
        assertEquals("{missing}", untouched.getAsJsonObject().get("item").getAsString());
    }

    @Test
    void reservedPlayerTokenResolvesToMcaName() {
        // A name-only resolver (non-template quest) renders {player} to the supplied MCA name.
        assertEquals("Well met, Aria!",
                PlaceholderResolver.forPlayerName("Aria").substituteLiteral("Well met, {player}!").getString());

        // No player in scope (validation preview): {player} renders verbatim rather than throwing.
        assertEquals("Hi {player}",
                PlaceholderResolver.forPlayerName(null).substituteLiteral("Hi {player}").getString());

        // The reserved token is unshadowable: a template variable literally named "player" does not win.
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        values.put("player", new ResolvedValue.TextValue(QuestText.literal("SHADOW")));
        PlaceholderResolver shadowed = new PlaceholderResolver(new ResolvedTemplate(values), "Aria");
        assertEquals("Aria", shadowed.substituteLiteral("{player}").getString());

        // {player} is dialogue-only: the whole-token JSON path never resolves it (stays verbatim).
        JsonElement out = PlaceholderResolver.forPlayerName("Aria")
                .substitute(JsonParser.parseString("{\"item\":\"{player}\"}"));
        assertEquals("{player}", out.getAsJsonObject().get("item").getAsString());

        // Placeholder-free text is unchanged by the resolver (no regression for hand-authored lines).
        assertEquals("plain text",
                PlaceholderResolver.forPlayerName("Aria").substituteLiteral("plain text").getString());
    }

    @Test
    void templateSpecKeepsRawObjectives() {
        String json = "{\"variables\":{\"count\":{\"kind\":\"int\",\"min\":1,\"max\":3}},"
                + "\"objectives\":[{\"type\":\"mcaquests:obtain_item\",\"item\":\"{crop}\",\"count\":\"{count}\"}]}";
        TemplateSpec spec = TemplateSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        assertTrue(spec.variables().containsKey("count"));
        assertTrue(spec.objectives().isJsonArray());
        assertTrue(spec.rewards().isJsonArray()); // defaults to an empty array when omitted
    }

    @Test
    void resolvedValuesSurviveNbtRoundTrip() {
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        values.put("crop", new ResolvedValue.IdValue(RegistryKind.ITEM, new ResourceLocation("minecraft:carrot")));
        values.put("count", new ResolvedValue.IntValue(7));
        values.put("phrase", new ResolvedValue.TextValue(QuestText.literal("a quiet harvest")));

        CompoundTag tag = new ResolvedTemplate(values).save();
        ResolvedTemplate loaded = ResolvedTemplate.load(tag);

        assertEquals(values.get("crop"), loaded.get("crop").orElseThrow());
        assertEquals(values.get("count"), loaded.get("count").orElseThrow());
        assertEquals(values.get("phrase"), loaded.get("phrase").orElseThrow());
    }
}
