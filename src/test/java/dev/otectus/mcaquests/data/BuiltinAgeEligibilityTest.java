package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.support.TestPaths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the built-in quest pack's age eligibility.
 *
 * <p>A quest whose giver sets {@code "adult_only": false} opts out of the only age gate MCA: Quests
 * applies by default, so <em>every</em> MCA age state can offer it — including {@code baby} and
 * {@code toddler}. That is how a babbling infant ended up offering a written-out errand with adult
 * prose. Any such quest must therefore also carry an {@code age_group} condition that names the ages
 * it is actually written for, and that list must exclude {@code baby} and {@code toddler}.
 *
 * <p>This is enforced as a test rather than a loader error on purpose: third-party packs stay free to
 * do as they like (the loader only warns), but the shipped pack cannot regress.
 */
class BuiltinAgeEligibilityTest {

    private static final Path QUESTS = TestPaths.of("src/main/resources/data/mcaquests/mcaquests/quests");

    /** Ages that cannot deliver a written quest line in MCA. */
    private static final Set<String> NON_SPEAKING = Set.of("baby", "toddler");

    @Test
    @DisplayName("every built-in quest with adult_only:false constrains age_group and excludes baby/toddler")
    void nonAdultQuestsConstrainAge() {
        List<String> problems = new ArrayList<>();
        int nonAdult = 0;
        for (Path file : questFiles()) {
            JsonObject root = parse(file);
            JsonObject giver = root.getAsJsonObject("giver");
            // adult_only defaults to true (GiverSpec.CODEC), so an absent field is already safe.
            boolean adultOnly = giver == null || !giver.has("adult_only") || giver.get("adult_only").getAsBoolean();
            if (adultOnly) {
                continue;
            }
            nonAdult++;
            List<String> groups = collectAgeGroups(root.get("conditions"));
            if (groups.isEmpty()) {
                problems.add(file + ": sets adult_only:false but has no age_group condition, so babies "
                        + "and toddlers can offer it.");
                continue;
            }
            for (String age : groups) {
                if (NON_SPEAKING.contains(age)) {
                    problems.add(file + ": age_group allows '" + age + "', which cannot speak a quest line.");
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> "Built-in age eligibility problems:\n  " + String.join("\n  ", problems));
        assertTrue(nonAdult > 0, "Expected at least one built-in non-adult quest to actually exercise this rule; "
                + "if the last one was removed, delete this assertion too.");
    }

    @Test
    @DisplayName("the child treat quest is offerable by a child or teen, never a baby or toddler")
    void childTreatAllowsOnlySpeakingChildren() {
        JsonObject root = parse(QUESTS.resolve("relations/child_treat.json"));
        List<String> groups = collectAgeGroups(root.get("conditions"));
        assertTrue(groups.contains("child"), "child_treat should be offerable by a child");
        assertTrue(groups.contains("teen"), "child_treat should be offerable by a teen");
        assertFalse(groups.contains("baby"), "child_treat must not be offerable by a baby");
        assertFalse(groups.contains("toddler"), "child_treat must not be offerable by a toddler");
        // The family relationship gate must survive alongside the new age gate.
        assertTrue(root.get("conditions").toString().contains("mcaquests:is_family_member"),
                "child_treat must keep its is_family_member condition");
    }

    /**
     * Every {@code age_group} list anywhere in a condition tree, walking {@code all_of} / {@code any_of} /
     * {@code not} composites. Composites are bare-keyed ({@code {"all_of": [...]}}), not typed objects.
     */
    private static List<String> collectAgeGroups(JsonElement conditions) {
        List<String> found = new ArrayList<>();
        walk(conditions, found);
        return found;
    }

    private static void walk(JsonElement element, List<String> found) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> walk(child, found));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("type") && "mcaquests:age_group".equals(object.get("type").getAsString())) {
            JsonArray groups = object.getAsJsonArray("groups");
            if (groups != null) {
                groups.forEach(g -> found.add(g.getAsString()));
            }
        }
        for (String composite : List.of("all_of", "any_of", "not")) {
            if (object.has(composite)) {
                walk(object.get(composite), found);
            }
        }
    }

    private static List<Path> questFiles() {
        assertTrue(Files.isDirectory(QUESTS), "Built-in quest directory not found at " + QUESTS.toAbsolutePath()
                + " — this test expects the Gradle working directory to be the project root.");
        try (Stream<Path> files = Files.walk(QUESTS)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject parse(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
