package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.support.TestPaths;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
import dev.otectus.mcaquests.quest.IntegrationRequirements.Integration;
import dev.otectus.mcaquests.quest.QuestDefinition;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped definition declares its optional dependencies through its typed content (brief C05,
 * DEP-01, DEP-10): everything under a {@code townstead/} folder needs Townstead, everything in the
 * {@code capitals_court} pack needs MCA Capitals, and nothing else needs either. A Townstead project
 * whose first phase is a plain donation is the case this exists for.
 */
class BuiltinIntegrationRequirementsTest {

    private static final Path DATA = TestPaths.of("src/main/resources/data/mcaquests/mcaquests");
    private static final Path CAPITALS = TestPaths.of("src/main/resources/compatpacks/capitals_court/data/mcaquests/mcaquests");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("bundled content needs exactly the integrations its folder says it does")
    void everyBundledDefinitionDeclaresItsDependencies() {
        List<String> wrong = new ArrayList<>();
        check(DATA.resolve("quests"), QuestDefinition.CODEC,
                def -> IntegrationRequirements.dependencies(def), wrong, false);
        check(DATA.resolve("projects"), ProjectDefinition.CODEC,
                def -> IntegrationRequirements.dependencies(def), wrong, false);
        check(DATA.resolve("situations"), SituationDefinition.CODEC,
                def -> IntegrationRequirements.dependencies(def), wrong, false);
        check(CAPITALS.resolve("quests"), QuestDefinition.CODEC,
                def -> IntegrationRequirements.dependencies(def), wrong, true);
        check(CAPITALS.resolve("situations"), SituationDefinition.CODEC,
                def -> IntegrationRequirements.dependencies(def), wrong, true);
        assertEquals(List.of(), wrong, "bundled definitions whose derived dependencies disagree with their location");
    }

    @Test
    @DisplayName("the two reported Townstead projects are excluded on a base installation")
    void reportedProjectsExcludedWithoutTownstead() {
        IntegrationRequirements.Availability none = IntegrationRequirements.availabilityOf(false, Set.of(), Set.of());
        for (String file : List.of("townstead/a_working_village.json", "townstead/townstead_known_far_and_wide.json")) {
            ProjectDefinition def = parse(DATA.resolve("projects").resolve(file), ProjectDefinition.CODEC);
            assertTrue(IntegrationRequirements.unavailable(def, none).isPresent(), file + " would load without Townstead");
        }
    }

    @Test
    @DisplayName("no definition id is shipped twice")
    void noDuplicateIds() {
        Map<String, String> seen = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (Path root : List.of(DATA.resolve("quests"), DATA.resolve("projects"), DATA.resolve("situations"),
                CAPITALS.resolve("quests"), CAPITALS.resolve("situations"))) {
            for (Path file : jsonUnder(root)) {
                String id = JsonParser.parseString(read(file)).getAsJsonObject().get("id").getAsString();
                String kind = root.getFileName().toString();
                String previous = seen.put(kind + ":" + id, file.toString());
                if (previous != null) {
                    duplicates.add(id + " in " + previous + " and " + file);
                }
            }
        }
        assertEquals(List.of(), duplicates);
    }

    private static <T> void check(Path root, Codec<T> codec, Function<T, List<Integration>> dependencies,
                                  List<String> wrong, boolean capitalsPack) {
        List<Path> files = jsonUnder(root);
        assertTrue(!files.isEmpty(), "no files under " + root);
        for (Path file : files) {
            T def = parse(file, codec);
            List<Integration> actual = dependencies.apply(def);
            List<Integration> expected = capitalsPack ? List.of(Integration.CAPITALS)
                    : file.toString().replace('\\', '/').contains("/townstead/") ? List.of(Integration.TOWNSTEAD)
                    : List.of();
            if (!actual.equals(expected)) {
                wrong.add(file + ": derived " + actual + ", expected " + expected);
            }
        }
    }

    private static <T> T parse(Path file, Codec<T> codec) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(read(file))).result()
                .orElseThrow(() -> new AssertionError(file + " did not parse"));
    }

    private static List<Path> jsonUnder(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
