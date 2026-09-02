package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.dialogue.VoiceLine;
import dev.otectus.mcaquests.quest.dialogue.VoicePool;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped villager voice parses, says something for every state, and names only keys that exist.
 *
 * <p>A mistyped translation key does not throw: Minecraft renders the key itself, so the villager
 * says {@code mcaquests.voice.cooldown.brusqe} to the player's face and the build stays green. That
 * is the failure this guards, and it is worth guarding here in particular because these pools are the
 * <em>fallback</em> path — the one that only runs when a villager has nothing else to say, which is
 * exactly when nobody is watching closely.
 *
 * <p>It also insists every declared state ships content. {@code cooldown} and {@code locked} were
 * fully implemented in code and authored by not one of the 262 bundled quests, and the result was
 * that every busy villager in the game said "I do not need anything right now." A state with a
 * loader, a selector, a render site and no lines is that same bug wearing a new coat.
 */
class BuiltinVoicePoolsTest {

    static {
        TestBootstrap.ensureBootstrapped();
        // Touch a codec that pulls the registries up in the order the loader does; see
        // DispatchedCodecInlinesTest for why a *Types class must not be the first thing initialised.
        assertTrue(QuestDefinition.CODEC != null);
    }

    private static final Path POOLS = Path.of("src/main/resources/data/mcaquests/mcaquests/dialogue");
    private static final Path LANG = Path.of("src/main/resources/assets/mcaquests/lang");

    private static Map<String, VoicePool> loadPools() {
        Map<String, VoicePool> pools = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(POOLS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                VoicePool pool = VoicePool.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(false, message -> {
                            throw new AssertionError(file.getFileName() + ": " + message);
                        });
                pools.put(file.getFileName().toString(), pool);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return pools;
    }

    private static Map<String, String> locale(String name) {
        try {
            JsonObject json = JsonParser.parseString(
                    Files.readString(LANG.resolve(name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> keys = new LinkedHashMap<>();
            json.entrySet().forEach(entry -> keys.put(entry.getKey(), entry.getValue().getAsString()));
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every shipped pool parses")
    void poolsParse() {
        assertTrue(Files.isDirectory(POOLS), "the built-in dialogue pools should ship at " + POOLS);
        assertTrue(loadPools().size() >= VoicePool.STATES.size(),
                "expected at least one pool per declared state");
    }

    @Test
    @DisplayName("every declared state ships lines, so no state is a loader with nothing behind it")
    void everyStateHasContent() {
        Set<String> covered = loadPools().values().stream()
                .map(VoicePool::state)
                .collect(java.util.stream.Collectors.toSet());

        List<String> missing = VoicePool.STATES.stream().filter(state -> !covered.contains(state)).sorted().toList();
        assertTrue(missing.isEmpty(),
                "these states have a selector and a render site but no shipped lines: " + missing);
    }

    @Test
    @DisplayName("every line names a translation key that exists in both locales")
    void everyKeyExists() {
        Map<String, String> en = locale("en_us");
        Map<String, String> pt = locale("pt_br");
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, VoicePool> entry : loadPools().entrySet()) {
            for (VoiceLine line : entry.getValue().lines()) {
                // Built-in content is always a translation key: an inline literal would pin the
                // shipped voice to English, which is the migration LocaleParityTest guards.
                String key = line.text().translate().orElse(null);
                if (key == null) {
                    problems.add(entry.getKey() + ": a line has no 'translate' key");
                    continue;
                }
                if (!en.containsKey(key)) {
                    problems.add(entry.getKey() + ": '" + key + "' is missing from en_us");
                }
                if (!pt.containsKey(key)) {
                    problems.add(entry.getKey() + ": '" + key + "' is missing from pt_br");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n  ", problems));
    }

    @Test
    @DisplayName("every pool ends in an unconditioned line, so no villager is left silent")
    void everyPoolHasAFallback() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, VoicePool> entry : loadPools().entrySet()) {
            boolean hasFallback = entry.getValue().lines().stream().anyMatch(VoiceLine::isFallback);
            if (!hasFallback) {
                // Not a correctness bug -- the caller falls back to its own static line -- but a
                // shipped pool that only covers some personalities means MCA adding a fourteenth
                // silently drops those villagers back to the flat text this feature exists to replace.
                problems.add(entry.getKey() + " has no unconditioned line");
            }
        }
        assertEquals(List.of(), problems);
    }
}
