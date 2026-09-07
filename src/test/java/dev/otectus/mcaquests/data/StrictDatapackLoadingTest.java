package dev.otectus.mcaquests.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.project.ProjectPhase;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StrictDatapackLoadingTest {
    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    private static final ResourceLocation FILE = new ResourceLocation("test", "folder/source");
    private static final ResourceLocation ID = new ResourceLocation("test", "declared");
    private static final String BASE = "\"id\":\"test:declared\",\"giver\":{},\"dialogue\":{}";

    @AfterEach
    void reset() {
        McaQuestsConfig.COMMON.strictJsonValidation.set(false);
        QuestRegistry.replaceAll(Map.of(), List.of(), List.of());
    }

    @Test
    void partialListsAreNeverInstalledAsCompleteData() {
        var input = JsonParser.parseString("[1,\"not an integer\",3]");
        var result = Codec.INT.listOf().parse(JsonOps.INSTANCE, input);
        assertTrue(result.resultOrPartial(error -> { }).isPresent(), "fixture must exercise a DFU partial value");
        List<String> errors = new ArrayList<>();
        assertTrue(StrictCodecs.parse(Codec.INT.listOf(), JsonOps.INSTANCE, input, errors::add).isEmpty());
        assertEquals(1, errors.size());
    }

    @Test
    void missingAddonClassesAreIsolatedToTheirDefinition() {
        Codec<Integer> broken = Codec.INT.flatXmap(value -> {
            throw new NoClassDefFoundError("missing.addon.Type");
        }, com.mojang.serialization.DataResult::success);
        List<String> errors = new ArrayList<>();
        assertTrue(StrictCodecs.parse(broken, JsonOps.INSTANCE, JsonParser.parseString("1"), errors::add).isEmpty());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("missing.addon.Type"));
    }

    @Test
    void malformedQuestIsQuarantinedUnderDeclaredAndResourceIds() {
        var input = JsonParser.parseString("{" + BASE + ",\"objectives\":["
                + "{\"type\":\"mcaquests:visit_dimension\",\"dimension\":\"minecraft:the_nether\"},"
                + "{\"type\":\"missingmod:unknown_objective\"}]}");
        new QuestDataLoader().apply(Map.of(FILE, input), null, null);
        assertFalse(QuestRegistry.contains(ID));
        assertTrue(QuestRegistry.isQuarantined(ID));
        assertTrue(QuestRegistry.isQuarantined(FILE));
        assertFalse(QuestRegistry.lastErrors().isEmpty());
    }

    @Test
    void invalidOptionalRulesDoNotBecomePermissiveDefaults() {
        for (String field : List.of("\"repeat\":{\"type\":\"typo\"}",
                "\"repeat\":{\"cooldown_ticks\":-1}", "\"failure\":{\"deadline_ticks\":0}",
                "\"chain\":{\"chain\":\"arc\",\"prerequisites\":42}",
                "\"turn_in\":{\"mode\":\"typo\"}", "\"enabled\":\"typo\"",
                "\"giver\":{\"professions\":42}")) {
            // Replace the giver field rather than creating a duplicate key in that fixture.
            String base = field.startsWith("\"giver\"") ? BASE.replace("\"giver\":{},", "") : BASE;
            var parsed = QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + base + "," + field + "}"));
            assertTrue(parsed.error().isPresent(), field);
        }
        assertTrue(QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{" + BASE + "}")).result().isPresent());
    }

    @Test
    void projectUnlockTyposAreNotDropped() {
        assertTrue(ProjectPhase.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"unlock\":{\"type\":\"unknown:gate\"}}")).error().isPresent());
    }

    @Test
    void strictReloadKeepsPreviousCatalogueOnFailure() {
        new QuestDataLoader().apply(Map.of(FILE, JsonParser.parseString("{" + BASE + "}")), null, null);
        int generation = QuestRegistry.generation();
        McaQuestsConfig.COMMON.strictJsonValidation.set(true);
        assertThrows(QuestValidationException.class, () -> new QuestDataLoader().apply(
                Map.of(FILE, JsonParser.parseString("{" + BASE + ",\"failure\":42}")), null, null));
        assertTrue(QuestRegistry.contains(ID));
        assertEquals(generation, QuestRegistry.generation());
    }

    @Test
    void validDefinitionWinsOverAMalformedDeclaredIdAlias() {
        var malformed = JsonParser.parseString("{" + BASE + ",\"failure\":42}");
        var valid = JsonParser.parseString("{" + BASE + "}");
        var files = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonElement>();
        files.put(FILE, malformed);
        files.put(ID, valid);
        new QuestDataLoader().apply(files, null, null);
        assertTrue(QuestRegistry.contains(ID));
        assertFalse(QuestRegistry.isQuarantined(ID));
        assertTrue(QuestRegistry.isQuarantined(FILE));
    }
}
