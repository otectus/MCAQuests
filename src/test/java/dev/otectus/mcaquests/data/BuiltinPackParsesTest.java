package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestDifficulty;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses every shipped quest, project, and situation through its real codec.
 *
 * <p>The 1.1.0 pass rewrote all ~220 data files — difficulty bands, rebalanced hearts, semantic currency
 * rewards, and the translate-key migration. A codec is the only thing that decides whether those files
 * actually load in game, and DataFixerUpper is lenient about unknown fields: a mistyped or misplaced key
 * is silently ignored rather than rejected. This catches a file that stopped parsing, and separately
 * asserts that the new fields really landed where the codec reads them rather than being quietly dropped.
 */
class BuiltinPackParsesTest {

    private static final Path DATA = dev.otectus.mcaquests.support.TestPaths.resolve("src/main/resources/data/mcaquests/mcaquests");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("every built-in quest parses")
    void questsParse() {
        assertAllParse("quests", QuestDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in project parses")
    void projectsParse() {
        assertAllParse("projects", ProjectDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in situation parses")
    void situationsParse() {
        assertAllParse("situations", SituationDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in quest declares a difficulty the codec actually reads")
    void everyQuestHasADifficulty() {
        List<String> missing = new ArrayList<>();
        for (Path file : jsonUnder("quests")) {
            QuestDefinition def = parseOrThrow(file, QuestDefinition.CODEC);
            if (def.difficulty().isEmpty()) {
                missing.add(file.toString());
            }
        }
        assertEquals(List.of(), missing, "these built-in quests have no difficulty band, so their currency "
                + "rewards would silently fall back to MEDIUM");
    }

    @Test
    @DisplayName("the rebalanced hearts values are exactly the three difficulty bands")
    void heartsFollowTheDifficultyBands() {
        // Guards the balance pass itself: a stray hand-edit that reintroduces a 35-heart repeatable would
        // put marriage back within a few in-game days.
        List<String> offenders = new ArrayList<>();
        for (Path file : jsonUnder("quests")) {
            String raw = read(file);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"type\"\\s*:\\s*\"mcaquests:hearts\"\\s*,\\s*\"amount\"\\s*:\\s*(\\d+)")
                    .matcher(raw);
            while (m.find()) {
                int amount = Integer.parseInt(m.group(1));
                if (amount != 4 && amount != 8 && amount != 14) {
                    offenders.add(file + " grants " + amount + " hearts");
                }
            }
        }
        assertEquals(List.of(), offenders, "built-in hearts rewards must stay on the 4 / 8 / 14 bands");
    }

    @Test
    @DisplayName("difficulty round-trips through the codec for each band")
    void difficultyRoundTrips() {
        for (QuestDifficulty band : QuestDifficulty.values()) {
            DataResult<QuestDifficulty> parsed = QuestDifficulty.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString('"' + band.lower() + '"'));
            assertEquals(band, parsed.result().orElseThrow(
                    () -> new AssertionError("difficulty '" + band.lower() + "' did not parse")));
        }
        assertTrue(QuestDifficulty.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"impossible\""))
                .error().isPresent(), "an unknown difficulty should be a parse error, not a silent default");
    }

    private static <T> void assertAllParse(String folder, com.mojang.serialization.Codec<T> codec) {
        List<String> failures = new ArrayList<>();
        List<Path> files = jsonUnder(folder);
        assertTrue(!files.isEmpty(), "no files found under " + DATA.resolve(folder).toAbsolutePath());
        for (Path file : files) {
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json(file));
            result.error().ifPresent(err -> failures.add(file + ": " + err.message()));
        }
        assertEquals(List.of(), failures, "built-in " + folder + " that no longer parse");
    }

    private static <T> T parseOrThrow(Path file, com.mojang.serialization.Codec<T> codec) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json(file));
        return result.result().orElseThrow(() -> new AssertionError(
                file + " did not parse: " + result.error().map(DataResult.Error::message).orElse("?")));
    }

    private static List<Path> jsonUnder(String folder) {
        try (Stream<Path> files = Files.walk(DATA.resolve(folder))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement json(Path file) {
        return JsonParser.parseString(read(file));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
