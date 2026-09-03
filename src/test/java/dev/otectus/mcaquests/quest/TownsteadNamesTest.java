package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.support.TestPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display vocabulary's code-side lists and its locale entries stay in step.
 *
 * <p>{@code TownsteadNames} decides <em>which</em> phrasing to use from a hard-coded list of paths,
 * not by asking whether a translation exists. That is not a shortcut: these Components are built on the
 * server and resolved on the client, and a dedicated server has only vanilla's language, because mod
 * lang files ship under {@code assets/} and never reach it. Asking the server would have answered "no
 * translation" on every multiplayer world and "yes" in single-player.
 *
 * <p>The cost of that correctness is a list that can drift from the locale, so this is the thing that
 * stops it drifting: every path the code will render must have both its phrasings, in every locale.
 */
class TownsteadNamesTest {

    private static final Path LANG = TestPaths.of("src/main/resources/assets/mcaquests/lang");
    private static final String PREFIX = "mcaquests.townstead.predicate.";

    @Test
    @DisplayName("every predicate path has both phrasings in every shipped locale")
    void predicatePathsAreTranslated() {
        List<String> paths = predicatePaths();
        assertFalse(paths.isEmpty(), "the predicate list is empty; the reflection has drifted");

        List<String> missing = new ArrayList<>();
        for (String locale : List.of("en_us", "pt_br")) {
            JsonObject table = load(locale);
            for (String path : paths) {
                for (String key : List.of(PREFIX + path, PREFIX + path + ".not")) {
                    if (!table.has(key)) {
                        missing.add(locale + " is missing " + key);
                    }
                }
            }
        }
        assertEquals(List.of(), missing, "a path the code will render with no phrasing to render it "
                + "shows the raw translation key on the card");
    }

    /**
     * The reverse direction. A curated phrasing for a path the code never renders is dead weight a
     * translator was asked to write for nothing.
     */
    @Test
    @DisplayName("no phrasing is shipped for a path the code never renders")
    void noOrphanedPhrasings() {
        List<String> paths = predicatePaths();
        List<String> orphans = new ArrayList<>();
        for (String key : load("en_us").keySet()) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String path = key.substring(PREFIX.length());
            if (path.endsWith(".not")) {
                path = path.substring(0, path.length() - ".not".length());
            }
            if (!paths.contains(path)) {
                orphans.add(key);
            }
        }
        assertEquals(List.of(), orphans, "these are translated but unreachable; either add the path to "
                + "TownsteadNames.PREDICATE_PATHS or drop the entries");
    }

    @Test
    @DisplayName("every value vocabulary the code composes has entries to compose from")
    void valueVocabulariesArePopulated() {
        JsonObject english = load("en_us");
        List<String> empty = new ArrayList<>();
        for (String prefix : List.of("mcaquests.townstead.activity.", "mcaquests.townstead.spirit.",
                "mcaquests.townstead.building.", "mcaquests.townstead.season.",
                "mcaquests.townstead.life_stage.", "mcaquests.townstead.classification.",
                "mcaquests.townstead.operator.", "mcaquests.townstead.operator.is.")) {
            if (english.keySet().stream().noneMatch(key -> key.startsWith(prefix))) {
                empty.add(prefix);
            }
        }
        assertEquals(List.of(), empty, "TownsteadNames composes keys under these prefixes; with none "
                + "present every affected value falls back to a humanised id");
    }

    @Test
    @DisplayName("both operator sets cover every operator the query language has")
    void operatorsAreComplete() {
        JsonObject english = load("en_us");
        List<String> missing = new ArrayList<>();
        for (String operator : List.of("eq", "ne", "lt", "lte", "gt", "gte",
                "contains", "in", "matches", "exists")) {
            for (String prefix : List.of("mcaquests.townstead.operator.",
                    "mcaquests.townstead.operator.is.")) {
                if (!english.has(prefix + operator)) {
                    missing.add(prefix + operator);
                }
            }
        }
        assertEquals(List.of(), missing, "an uncovered operator renders as its own id, which is the "
                + "bug this vocabulary exists to fix");
    }

    /** Reads the private list rather than duplicating it, so the two cannot disagree. */
    @SuppressWarnings("unchecked")
    private static List<String> predicatePaths() {
        try {
            Field field = TownsteadNames.class.getDeclaredField("PREDICATE_PATHS");
            field.setAccessible(true);
            return (List<String>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("TownsteadNames.PREDICATE_PATHS is gone or renamed; this test "
                    + "and the vocabulary it guards need to move together", e);
        }
    }

    private static JsonObject load(String locale) {
        try {
            return JsonParser.parseString(
                    Files.readString(LANG.resolve(locale + ".json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("the vocabulary class loads without touching a registry")
    void loadsStandalone() {
        // Static initialisation must not reach BuiltInRegistries: this class is used from objective
        // descriptions built during a reload, before registries are necessarily usable.
        assertTrue(predicatePaths().size() >= 4);
    }
}
