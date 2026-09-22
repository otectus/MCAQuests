package dev.otectus.mcaquests.quest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fingerprints of authored content, and the drift rule built on them (1.7.0; F01). */
class DefinitionFingerprintTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestObjective objective(String json) {
        return ObjectiveTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    @Test
    @DisplayName("key order and formatting do not change a fingerprint; a changed value does")
    void canonical() {
        String a = DefinitionFingerprint.of(ObjectiveTypes.CODEC,
                objective("{\"type\":\"mcaquests:kill_entity\",\"entity\":\"minecraft:zombie\",\"count\":5}")).orElseThrow();
        String b = DefinitionFingerprint.of(ObjectiveTypes.CODEC,
                objective("{ \"count\": 5, \"entity\": \"minecraft:zombie\", \"type\": \"mcaquests:kill_entity\" }")).orElseThrow();
        String c = DefinitionFingerprint.of(ObjectiveTypes.CODEC,
                objective("{\"type\":\"mcaquests:kill_entity\",\"entity\":\"minecraft:zombie\",\"count\":6}")).orElseThrow();
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(DefinitionFingerprint.LENGTH, a.length());
    }

    @Test
    @DisplayName("appending objectives is compatible; inserting, reordering, removing or editing is drift")
    void driftRule() {
        List<String> accepted = List.of("aaa", "bbb");
        assertTrue(QuestDrift.compatible(accepted, List.of("aaa", "bbb", "ccc")), "append");
        assertFalse(QuestDrift.compatible(accepted, List.of("ccc", "aaa", "bbb")), "insert before");
        assertFalse(QuestDrift.compatible(accepted, List.of("bbb", "aaa")), "reorder");
        assertFalse(QuestDrift.compatible(accepted, List.of("aaa")), "remove");
        assertFalse(QuestDrift.compatible(accepted, List.of("aaa", "bbx")), "edit");
        assertTrue(QuestDrift.compatible(List.of("", "bbb"), List.of("zzz", "bbb")),
                "an entry that could not be fingerprinted is taken on trust, never paused over");
    }

    @Test
    @DisplayName("every bundled quest's objectives can be fingerprinted")
    void bundledObjectivesEncode() throws Exception {
        java.nio.file.Path root = dev.otectus.mcaquests.support.TestPaths.of("src/main/resources/data/mcaquests/mcaquests/quests");
        List<java.nio.file.Path> files;
        try (var walk = java.nio.file.Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).toList();
        }
        int unencodable = 0;
        for (java.nio.file.Path file : files) {
            var json = JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            if (!json.has("objectives")) {
                continue;
            }
            for (var element : json.getAsJsonArray("objectives")) {
                var parsed = ObjectiveTypes.CODEC.parse(JsonOps.INSTANCE, element).result();
                if (parsed.isPresent() && DefinitionFingerprint.of(ObjectiveTypes.CODEC, parsed.get()).isEmpty()) {
                    unencodable++;
                }
            }
        }
        assertEquals(0, unencodable, "objectives whose codec cannot encode would be taken on trust");
    }
}
