package dev.otectus.mcaquests.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No objective, condition or reward hands a raw internal id to a player-facing sentence.
 *
 * <p>This is the guard on a bug that shipped in front of players: a quest asking a villager to stay at
 * work rendered as <b>"Keep villager.schedule.currentActivity eq work for 30 seconds"</b>. The query
 * path, the operator id and the enum constant were all passed straight into a translated string, and
 * because the result was long it also overflowed the offer card and was cut off mid-word. Every one of
 * the Townstead descriptions had the same shape; the rest of the mod had used {@code DisplayNames} for
 * this since 0.5.0.
 *
 * <p>The check is deliberately structural rather than a golden-text comparison. What went wrong was not
 * a wording anyone would notice reviewing a diff — it was an <em>argument</em>, several call sites deep,
 * that looked perfectly reasonable next to the sentence it broke. So this reads the call sites and
 * insists that anything handed to {@code Component.translatable} is either a number, a nested
 * {@code Component}, or something that has been through a naming helper.
 */
class NoRawIdsInQuestTextTest {

    private static final Path SOURCE = Path.of("src/main/java/dev/otectus/mcaquests");

    /**
     * The helpers that turn an id into a display name. An id passed <em>to</em> one of these is
     * exactly right, so their whole call is removed before the raw-id scan runs — otherwise the fix
     * would look identical to the bug.
     */
    private static final Pattern NAMING_HELPER =
            Pattern.compile("(?:TownsteadNames|DisplayNames)\\.[A-Za-z]+\\(");

    /**
     * The specific accessors that return an internal id. Naming them explicitly, rather than trying to
     * detect "a String", keeps this from flagging every count and label in the mod.
     */
    private static final Pattern RAW_ID = Pattern.compile(
            "\\b(?:query\\.describe\\(\\)"
                    + "|(?:view|snapshot)\\.professionId\\(\\)"
                    + "|\\.primaryId\\(\\)"
                    + "|\\.currentActivity\\(\\)"
                    + "|\\.plannedActivity\\(\\)"
                    + "|buildingType(?!\\s*\\.)"
                    + "|String\\.join\\(\"\\.\", query\\.path\\(\\)\\))");

    /**
     * The start of a translated-sentence call. Only the opening is matched by regex; the argument list
     * is then taken by counting parentheses, because a regex that stops at the first {@code )} cuts a
     * nested {@code TownsteadNames.building(x)} in half and reports the fix as the bug.
     */
    private static final Pattern TRANSLATABLE =
            Pattern.compile("Component\\.translatable(?:WithFallback)?\\(");

    @Test
    @DisplayName("no player-facing sentence is handed a raw Townstead id")
    void noRawIdsInTranslatableCalls() {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            String body = read(file);
            Matcher call = TRANSLATABLE.matcher(body);
            while (call.find()) {
                int open = call.end() - 1;
                int close = matchingParen(body, open);
                if (close < 0) {
                    continue;
                }
                String arguments = withoutNamingHelpers(body.substring(open + 1, close));
                Matcher raw = RAW_ID.matcher(arguments);
                if (raw.find()) {
                    offenders.add(file.getFileName() + ": " + raw.group().trim()
                            + " passed to a translated sentence");
                }
            }
        }
        assertEquals(List.of(), offenders,
                "these render an internal id into text a player reads. Route it through "
                        + "TownsteadNames or DisplayNames, which fall back to a humanised form for ids "
                        + "nobody has curated so nothing is ever shown raw.");
    }

    /**
     * {@code TownsteadQuery.describe()} renders the raw path for the diagnostic command. It has exactly
     * one legitimate caller, and the bug was a second one.
     */
    @Test
    @DisplayName("the diagnostic query rendering stays out of the quest surface")
    void queryDescribeIsDiagnosticOnly() {
        List<String> callers = new ArrayList<>();
        for (Path file : sources()) {
            if (read(file).contains("query.describe()") || read(file).contains("query().describe()")) {
                callers.add(file.getFileName().toString());
            }
        }
        assertTrue(callers.stream().allMatch(name -> name.contains("Command")
                        || name.equals("TownsteadQuery.java")),
                "TownsteadQuery.describe() renders a raw path for '/mcaquests compat townstead "
                        + "explain'. It must not reach a quest card; use TownsteadNames.clause(query). "
                        + "Found in: " + callers);
    }

    /** Every curated vocabulary key the code asks for exists in the shipped locale. */
    @Test
    @DisplayName("the display vocabulary is actually populated")
    void vocabularyIsPresent() {
        String english = read(Path.of("src/main/resources/assets/mcaquests/lang/en_us.json"));
        List<String> missing = new ArrayList<>();
        for (String key : List.of(
                "mcaquests.townstead.activity.work",
                "mcaquests.townstead.activity.rest",
                "mcaquests.townstead.spirit.nautical",
                "mcaquests.townstead.building.dock",
                "mcaquests.townstead.building.butcher",
                "mcaquests.townstead.operator.gte",
                "mcaquests.townstead.value.villager.needs.hunger",
                "mcaquests.townstead.value.villager.schedule.currentactivity",
                "mcaquests.townstead.clause.doing",
                "mcaquests.townstead.clause.compare",
                "mcaquests.name.minecraft.farmer",
                "mcaquests.name.mca.guard")) {
            if (!english.contains('"' + key + '"')) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing, "the code composes these keys; without them every affected "
                + "line falls back to a humanised id, which is better than raw but is not the "
                + "sentence anyone wrote");
    }

    /**
     * Removes every {@code TownsteadNames.x(...)} / {@code DisplayNames.x(...)} call, balanced
     * parentheses and all, leaving only the arguments that reach the sentence unmediated.
     */
    private static String withoutNamingHelpers(String arguments) {
        StringBuilder out = new StringBuilder(arguments);
        Matcher helper = NAMING_HELPER.matcher(out);
        while (helper.find()) {
            int open = helper.end() - 1;
            int close = matchingParen(out, open);
            if (close < 0) {
                break;
            }
            out.delete(helper.start(), close + 1);
            helper = NAMING_HELPER.matcher(out);
        }
        return out.toString();
    }

    /** Index of the {@code )} closing the {@code (} at {@code open}, or {@code -1} if unbalanced. */
    private static int matchingParen(CharSequence text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<Path> sources() {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
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
