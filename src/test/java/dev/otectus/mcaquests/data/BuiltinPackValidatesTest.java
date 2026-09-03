package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.data.ProjectValidator;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import net.minecraft.resources.ResourceLocation;
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
 * Runs the bundled pack through the same validators a datapack reload runs it through.
 *
 * <p>{@code BuiltinPackParsesTest} answers "does this file decode", which is a lower bar than it
 * sounds: DataFixerUpper is happy to build a quest whose chain names a stage that does not exist, or
 * whose escort objective has a destination it can never resolve. Those are caught at reload by the
 * validators in this package — <b>for a player, in their log, after the jar shipped</b>.
 *
 * <p>Spec §5.11 draws the line the other way round for bundled content: an author can fix their own
 * pack, so a warning is enough for them, but a broken built-in is unfixable by the person who hits it.
 * So every validator error over the shipped pack fails the build here instead.
 */
class BuiltinPackValidatesTest {

    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    @Test
    @DisplayName("every built-in quest passes the reload-time validators")
    void questsValidate() {
        Map<ResourceLocation, QuestDefinition> quests = loadAll("quests", QuestDefinition.CODEC,
                QuestDefinition::id);
        assertTrue(quests.size() > 200, "the quest scan found almost nothing; the path has drifted");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        QuestChainValidator.validate(quests, errors, warnings);
        TemplateValidator.validate(quests, errors);
        FailureValidator.validate(quests, errors);
        ObjectiveValidator.validate(quests, errors, warnings);

        assertEquals(List.of(), realProblems(errors),
                "the bundled quest pack does not survive its own validators");
    }

    @Test
    @DisplayName("every built-in project passes the reload-time validators")
    void projectsValidate() {
        Map<ResourceLocation, ProjectDefinition> projects = loadAll("projects", ProjectDefinition.CODEC,
                ProjectDefinition::id);
        assertTrue(projects.size() >= 13, "the project scan found almost nothing; the path has drifted");

        List<String> errors = new ArrayList<>();
        ProjectValidator.validate(projects, errors);
        // ProjectValidator reports advisory findings into the same list behind its own prefix, and
        // every one of them here is "MCA is not loaded" -- which is true of a unit test and of nothing
        // else. Its own predicate is the documented way to tell the two apart.
        assertEquals(List.of(), errors.stream().filter(e -> !ProjectValidator.isWarning(e)).toList(),
                "the bundled project pack does not survive its own validators");
    }

    /**
     * Chains are asserted separately from the validator because the validator is permissive about a
     * chain whose stages a datapack means to extend later. A <em>bundled</em> chain is complete by
     * construction, so every stage from 1 to its declared total must exist and lead to the next.
     */
    @Test
    @DisplayName("every built-in chain runs from stage one to its declared total without a gap")
    void chainsAreContiguous() {
        Map<ResourceLocation, QuestDefinition> quests = loadAll("quests", QuestDefinition.CODEC,
                QuestDefinition::id);
        Map<String, List<QuestDefinition>> byChain = new LinkedHashMap<>();
        quests.values().forEach(def -> def.chain().ifPresent(chain ->
                byChain.computeIfAbsent(chain.chain(), key -> new ArrayList<>()).add(def)));
        assertTrue(byChain.size() >= 9, "expected at least the nine Life of the Town arcs plus the "
                + "pre-existing chains; found " + byChain.size());

        List<String> problems = new ArrayList<>();
        byChain.forEach((chain, stages) -> {
            int total = stages.stream()
                    .mapToInt(def -> def.chain().orElseThrow().stageTotal().orElse(0))
                    .max().orElse(0);
            for (int stage = 1; stage <= total; stage++) {
                int wanted = stage;
                long found = stages.stream()
                        .filter(def -> def.chain().orElseThrow().stage() == wanted)
                        .count();
                if (found == 0) {
                    problems.add(chain + " has no stage " + wanted + " of " + total);
                }
            }
        });
        assertEquals(List.of(), problems, "a chain with a missing stage strands the player on the one "
                + "before it, with no way to tell that the next never existed");
    }

    /**
     * Drops the one class of finding this environment cannot answer honestly.
     *
     * <p>Tag contents come from a loaded datapack, and no datapack is loaded here, so every tag reads
     * as empty and {@code TemplateValidator} reports each one as "empty or unknown". Keeping those
     * would mean this test failed on the shipped pack from the day it was written, which trains people
     * to ignore it. The trade-off is real and worth naming: a genuinely mistyped tag id in a template
     * is not caught here. It is caught at reload, where the registry is real.
     */
    private static List<String> realProblems(List<String> errors) {
        return errors.stream().filter(error -> !error.contains("which is empty or unknown")).toList();
    }

    private static <T> Map<ResourceLocation, T> loadAll(String directory,
                                                        com.mojang.serialization.Codec<T> codec,
                                                        java.util.function.Function<T, ResourceLocation> id) {
        Map<ResourceLocation, T> loaded = new LinkedHashMap<>();
        Path root = DATA.resolve(directory);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                Path source = path;
                T parsed = codec.parse(JsonOps.INSTANCE, json).resultOrPartial(
                        error -> {
                            throw new AssertionError(source + ": " + error);
                        }).orElseThrow(() -> new AssertionError(source + ": did not decode"));
                loaded.put(id.apply(parsed), parsed);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return loaded;
    }
}
