package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code mcaquests:} type id named in the documentation actually exists.
 *
 * <p>Documentation rots in a particular way: a field gets renamed in code, the reference table keeps
 * the old name, and the first person to find out is an author whose datapack will not load, with an
 * error naming a type they copied from the manual. Nobody proofreads a 1,400-line reference on every
 * refactor, so this asserts the machine-checkable half instead.
 *
 * <h2>Why this reads source instead of asking the registries</h2>
 *
 * <p>It would be more direct to call {@code ObjectiveTypes.exists(...)}. It is also a trap. Loading
 * those classes drags in {@code BuiltInRegistries}, which in this test environment is only
 * half-initialised by {@code TestBootstrap} — and a class whose static initialiser fails stays failed
 * for the whole JVM. Because Gradle shares one worker across the suite, a single test that trips that
 * ordering takes dozens of unrelated tests down with it, all reporting
 * {@code NoClassDefFoundError} for classes they never touched. Reading the registration call sites is
 * immune to that, and it is the same "inspect the file, do not load the class" technique the
 * static-link tripwires already use.
 */
class DocumentedTypesExistTest {

    /**
     * Only the places a doc actually <em>promises a type</em>: a {@code "type"} value in a JSON sample,
     * and the leading backticked cell of a reference-table row.
     *
     * <p>Matching every {@code mcaquests:} mention would be wrong, and noisily so. The docs are full of
     * example content ids -- {@code mcaquests:example_quest}, chain ids like
     * {@code mcaquests:farmer_family}, category names -- which are illustrations, not registrations,
     * and flagging them would train everyone to ignore this test.
     */
    private static final Pattern TYPE_PROMISE = Pattern.compile(
            "\"type\"\\s*:\\s*\"mcaquests:([a-z0-9_]+)\""
                    + "|^\\|\\s*`mcaquests:([a-z0-9_]+)`", Pattern.MULTILINE);

    /** Any mention at all, for the "is this documented" direction, where a prose mention counts. */
    private static final Pattern ANY_MENTION = Pattern.compile("mcaquests:([a-z0-9_]+)");

    /**
     * The three shapes a registration is written in: {@code register("x",},
     * {@code register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "x")}, and the FTB bridge's
     * {@code register(id("x")}.
     */
    private static final Pattern REGISTRATION = Pattern.compile(
            "register\\(\\s*(?:ResourceLocation\\.fromNamespaceAndPath\\(\\s*McaQuests\\.MOD_ID\\s*,\\s*|id\\(\\s*)?"
                    + "\"([a-z0-9_]+)\"");

    private static final List<Path> DOCS = List.of(
            TestPaths.of("TOWNSTEAD.md"), TestPaths.of("DATAPACK.md"), TestPaths.of("FTBQUESTS.md"),
            TestPaths.of("CONFIG.md"), TestPaths.of("README.md"));

    private static final List<Path> REGISTRIES = List.of(
            TestPaths.of("src/main/java/dev/otectus/mcaquests/quest/condition/ConditionTypes.java"),
            TestPaths.of("src/main/java/dev/otectus/mcaquests/quest/objective/ObjectiveTypes.java"),
            TestPaths.of("src/main/java/dev/otectus/mcaquests/quest/reward/RewardTypes.java"),
            TestPaths.of("src/main/java/dev/otectus/mcaquests/project/objective/ProjectObjectiveTypes.java"),
            TestPaths.of("src/main/java/dev/otectus/mcaquests/quest/situation/SituationTriggerTypes.java"),
            // FTB tasks and rewards carry mcaquests: ids too, and FTBQUESTS.md documents them in the
            // same table shape, so leaving these out would report every one of them as missing.
            TestPaths.of("src/main/java/dev/otectus/mcaquests/compat/ftbq/FtbqTaskTypes.java"),
            TestPaths.of("src/main/java/dev/otectus/mcaquests/compat/ftbq/FtbqRewardTypes.java"));

    /**
     * Ids that are legitimately not registry types: the network channel, built-in reputation tiers and
     * titles. Listed explicitly rather than matched by a loose pattern, so a genuinely mistyped type id
     * cannot hide behind the exemption.
     */
    private static final Set<String> NOT_TYPE_IDS = Set.of(
            "main", "default", "honored_of_village", "revered_of_village");

    @Test
    @DisplayName("every type id the docs name is one the code registers")
    void documentedTypesResolve() {
        Set<String> registered = registeredIds();
        assertFalse(registered.isEmpty(), "found no registrations at all; the scan pattern has drifted");
        assertTrue(registered.contains("item_delivery") && registered.contains("townstead_state"),
                "the registration scan is missing known types, so a stale doc id could slip past it");

        Set<String> content = contentIds();
        List<String> unknown = new ArrayList<>();
        for (Path doc : DOCS) {
            if (!Files.isRegularFile(doc)) {
                continue;
            }
            for (String id : promisedIn(doc)) {
                if (registered.contains(id) || content.contains(id) || NOT_TYPE_IDS.contains(id)) {
                    continue;
                }
                unknown.add(doc + " documents mcaquests:" + id + " as a type");
            }
        }
        assertEquals(List.of(), unknown,
                "the documentation promises type ids that nothing registers. Either the doc is stale or "
                        + "the type was renamed; an author copying from the manual would hit a datapack "
                        + "load error naming a type they were told to use.");
    }

    /**
     * The Townstead reference was written alongside the code rather than after it, so it is the one
     * most likely to fall behind. Assert it covers what actually shipped, so a type added later without
     * a doc entry is caught here rather than by an author who cannot find it.
     */
    @Test
    @DisplayName("the Townstead reference documents every Townstead type that shipped")
    void townsteadReferenceIsComplete() {
        Set<String> documented = idsIn(TestPaths.of("TOWNSTEAD.md"));
        List<String> undocumented = new ArrayList<>();

        for (String id : registeredIds()) {
            if (id.startsWith("townstead_") && !documented.contains(id)) {
                undocumented.add("mcaquests:" + id);
            }
        }
        assertEquals(List.of(), undocumented, "TOWNSTEAD.md does not mention these shipped types");
    }

    @Test
    @DisplayName("the reference names the storage destination that is not implemented, as unimplemented")
    void villageStorageIsNotPromised() {
        String doc = read(TestPaths.of("TOWNSTEAD.md"));

        assertTrue(doc.contains("townstead_village_storage"),
                "an author who reaches for it should find out here rather than from a parse error");
        assertTrue(doc.contains("not** implemented") || doc.contains("not implemented"),
                "and it must be described as unimplemented rather than documented as if it worked");
        assertFalse(doc.contains("\"townstead_village_storage\""),
                "it must never appear as a copyable JSON value");
    }

    private static Set<String> registeredIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Path registry : REGISTRIES) {
            Matcher matcher = REGISTRATION.matcher(read(registry));
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    /** Ids a doc presents as a usable type, from a JSON {@code "type"} or a reference-table row. */
    private static Set<String> promisedIn(Path doc) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = TYPE_PROMISE.matcher(read(doc));
        while (matcher.find()) {
            ids.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return ids;
    }

    /** Every id a doc mentions anywhere, used for "did we forget to document this". */
    private static Set<String> idsIn(Path doc) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = ANY_MENTION.matcher(read(doc));
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    /** Ids of the built-in pack's own quests, projects and situations, which are content, not types. */
    private static Set<String> contentIds() {
        Set<String> ids = new LinkedHashSet<>();
        Path data = TestPaths.of("src/main/resources/data/mcaquests/mcaquests");
        if (!Files.isDirectory(data)) {
            return ids;
        }
        try (var paths = Files.walk(data)) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"mcaquests:([a-z0-9_/]+)\"")
                        .matcher(read(p));
                while (matcher.find()) {
                    ids.add(matcher.group(1));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ids;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
