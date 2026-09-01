package dev.otectus.mcaquests.data;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped pack is exactly the size it is documented to be.
 *
 * <p>These numbers are quoted in the README, the changelog and the store page, and they are the kind
 * of fact that goes quietly wrong: a file renamed and its old copy left behind, a definition dropped
 * during a rebase, a generator run twice. None of those break a build, and none of them are visible
 * in a diff of two hundred files. Asserting the totals makes the count something the build knows
 * rather than something a human last counted by hand.
 *
 * <p>They are meant to be edited. Adding content and updating the number here is the intended
 * workflow — what is not intended is the number changing without anyone noticing.
 */
class ContentTotalsTest {

    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");
    private static final Path TAGS = Path.of("src/main/resources/data/mcaquests/tags");

    /** 190 before Life of the Town, plus the 72 that release added. */
    private static final int QUESTS = 262;
    /** 13 before, plus 8. */
    private static final int PROJECTS = 21;
    /** 15 before, plus 10. */
    private static final int SITUATIONS = 25;
    /** 2 before, plus the 7 the new major arcs grant. */
    private static final int TITLES = 9;
    /** 25 before, plus 48. Counted separately because Townstead content must degrade as a set. */
    private static final int TOWNSTEAD_QUESTS = 73;

    @Test
    @DisplayName("the pack ships exactly the documented number of definitions")
    void totalsMatch() {
        Map<String, Integer> actual = new LinkedHashMap<>();
        actual.put("quests", count(DATA.resolve("quests")));
        actual.put("projects", count(DATA.resolve("projects")));
        actual.put("situations", count(DATA.resolve("situations")));
        actual.put("titles", count(DATA.resolve("titles")));
        actual.put("townstead quests", count(DATA.resolve("quests/townstead")));

        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("quests", QUESTS);
        expected.put("projects", PROJECTS);
        expected.put("situations", SITUATIONS);
        expected.put("titles", TITLES);
        expected.put("townstead quests", TOWNSTEAD_QUESTS);

        assertEquals(expected, actual, "the pack is not the size the docs say it is; either a "
                + "definition was lost or one was added without updating the counts here, the README "
                + "and the changelog");
    }

    @Test
    @DisplayName("every new four-stage arc ships all four of its stages")
    void arcsAreComplete() {
        List<String> arcs = List.of(
                "seasons_of_the_soil", "harbor_of_hands", "wool_and_winter", "smokehouse_legacy",
                "apprenticeship_pact", "village_with_a_name",
                "the_broken_road", "the_ashen_remedy", "the_bell_at_dawn");

        List<String> problems = new ArrayList<>();
        for (String arc : arcs) {
            long stages = questFiles()
                    .filter(body -> body.contains("\"chain\": \"" + arc + "\""))
                    .count();
            if (stages != 4) {
                problems.add(arc + " has " + stages + " stages, expected 4");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("the custom tags the new content depends on all exist and are non-empty")
    void customTagsExist() {
        List<String> problems = new ArrayList<>();
        for (String tag : List.of("worldgen/structure/trail_ruins", "worldgen/structure/ocean_ruins",
                "items/pottery_sherds", "entity_types/common_undead")) {
            Path path = TAGS.resolve(tag + ".json");
            if (!Files.isRegularFile(path)) {
                problems.add(tag + " does not exist");
                continue;
            }
            // A tag file with no values parses and loads and then silently matches nothing, which
            // reads to a player as a quest objective that can never be completed.
            if (!read(path).contains("minecraft:")) {
                problems.add(tag + " has no members");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("no Townstead definition escaped the townstead folder")
    void townsteadContentIsFoldered() {
        List<String> strays = new ArrayList<>();
        for (String directory : List.of("quests", "projects", "situations")) {
            Path root = DATA.resolve(directory);
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/townstead/"))
                        .filter(p -> read(p).contains("mcaquests:townstead_available"))
                        .forEach(p -> strays.add(p.toString()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertEquals(List.of(), strays, "a Townstead-gated definition outside the townstead folder is "
                + "invisible to anyone auditing what the integration ships");
    }

    @Test
    @DisplayName("no core definition reaches for Townstead")
    void coreContentIsTownsteadFree() {
        List<String> offenders = new ArrayList<>();
        for (String directory : List.of("quests", "projects", "situations")) {
            Path root = DATA.resolve(directory);
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/townstead/"))
                        .filter(p -> read(p).contains("mcaquests:townstead"))
                        .forEach(p -> offenders.add(p.toString()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertEquals(List.of(), offenders, "core content must load and play identically on an install "
                + "that has never had Townstead, so it may not name a Townstead type at all");
    }

    private static Stream<String> questFiles() {
        try (Stream<Path> files = Files.walk(DATA.resolve("quests"))) {
            return files.filter(p -> p.toString().endsWith(".json")).map(ContentTotalsTest::read)
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int count(Path root) {
        assertTrue(Files.isDirectory(root), root + " is not a directory");
        try (Stream<Path> files = Files.walk(root)) {
            return (int) files.filter(p -> p.toString().endsWith(".json")).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
