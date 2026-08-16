package dev.otectus.mcaquests.data;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the shipped locales honest.
 *
 * <p>Minecraft silently falls back to {@code en_us} for a missing key, so a half-translated locale looks
 * fine in a smoke test and then shows English mid-sentence to a player using it. These checks make that
 * visible at build time instead: {@code en_us} is the source of truth and must contain every key any other
 * locale defines, every other locale must cover all of {@code en_us}, and the format placeholders must
 * agree — a translation that drops or adds a {@code %s} throws at render time, taking the quest UI with it.
 *
 * <p>It also guards the migration that made translation possible at all: no built-in quest, situation, or
 * project may go back to hard-coding player-facing English via {@code "text"}.
 */
class LocaleParityTest {

    private static final Path LANG = Path.of("src/main/resources/assets/mcaquests/lang");
    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");
    private static final String SOURCE_LOCALE = "en_us";

    /** {@code %s}, {@code %d}, and the positional {@code %1$s} forms Minecraft's formatter accepts. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([sd])");

    @Test
    @DisplayName("en_us exists and is the source of truth")
    void sourceLocaleExists() {
        assertTrue(Files.isRegularFile(LANG.resolve(SOURCE_LOCALE + ".json")),
                "missing source locale at " + LANG.resolve(SOURCE_LOCALE + ".json").toAbsolutePath());
        assertTrue(load(SOURCE_LOCALE).size() > 1000,
                "en_us should carry the whole built-in pack; did the translate-key migration regress?");
    }

    @Test
    @DisplayName("every translated locale covers all of en_us, and adds nothing en_us lacks")
    void localesAreInParity() {
        Map<String, String> source = load(SOURCE_LOCALE);
        List<String> problems = new ArrayList<>();
        for (String locale : translatedLocales()) {
            Map<String, String> target = load(locale);

            TreeSet<String> missing = new TreeSet<>(source.keySet());
            missing.removeAll(target.keySet());
            if (!missing.isEmpty()) {
                problems.add(locale + " is missing " + missing.size() + " key(s) present in en_us, e.g. "
                        + missing.stream().limit(5).toList());
            }

            TreeSet<String> extra = new TreeSet<>(target.keySet());
            extra.removeAll(source.keySet());
            if (!extra.isEmpty()) {
                problems.add(locale + " defines " + extra.size() + " key(s) that do not exist in en_us "
                        + "(a typo, or a key en_us forgot), e.g. " + extra.stream().limit(5).toList());
            }
        }
        assertTrue(problems.isEmpty(), () -> "Locale parity problems:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("placeholders match between en_us and every translation")
    void placeholdersMatch() {
        Map<String, String> source = load(SOURCE_LOCALE);
        List<String> problems = new ArrayList<>();
        for (String locale : translatedLocales()) {
            load(locale).forEach((key, translated) -> {
                String english = source.get(key);
                if (english == null) {
                    return; // reported by the parity test above
                }
                // Compared as multisets of kind, not as ordered lists: a translation is allowed — and for
                // Portuguese word order often required — to reorder arguments using %1$s / %2$s.
                Map<String, Integer> want = placeholders(english);
                Map<String, Integer> got = placeholders(translated);
                if (!want.equals(got)) {
                    problems.add(locale + " key '" + key + "' has placeholders " + got
                            + " but en_us has " + want + "\n      en: " + english + "\n      tr: " + translated);
                }
            });
        }
        assertTrue(problems.isEmpty(), () -> "Placeholder mismatches:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("no built-in data hard-codes player-facing English")
    void builtInDataUsesTranslationKeys() {
        List<String> offenders = new ArrayList<>();
        for (Path file : dataFiles()) {
            String raw = read(file);
            if (raw.contains("\"text\"")) {
                offenders.add(file.toString());
            }
        }
        assertEquals(List.of(), offenders,
                "these built-in files still hard-code English via \"text\"; migrate them to \"translate\" keys "
                        + "so a locale can override them (literal \"text\" stays supported for third-party packs)");
    }

    @Test
    @DisplayName("no locale value is blank or left as an untranslated placeholder marker")
    void translationsAreFilledIn() {
        List<String> problems = new ArrayList<>();
        for (String locale : translatedLocales()) {
            load(locale).forEach((key, value) -> {
                if (value.isBlank()) {
                    problems.add(locale + " key '" + key + "' is blank");
                } else if (value.startsWith("TODO") || value.startsWith("XXX")) {
                    problems.add(locale + " key '" + key + "' is still a translation marker: " + value);
                }
            });
        }
        assertTrue(problems.isEmpty(), () -> "Unfinished translations:\n  " + String.join("\n  ", problems));
    }

    /**
     * Every locale shipped so far is Latin-script. A stray character from another writing system is a
     * slip — a word typed in the wrong language — not a translation, and it is very easy to miss by eye
     * in a file of well over a thousand lines. Delete this test (or scope it to the Latin locales) the
     * day a Cyrillic, Greek, or CJK locale is added.
     */
    @Test
    @DisplayName("no locale mixes in characters from a non-Latin writing system")
    void translationsStayInLatinScript() {
        List<String> problems = new ArrayList<>();
        for (String locale : translatedLocales()) {
            load(locale).forEach((key, value) -> value.codePoints()
                    // Latin-1 Supplement + Latin Extended-A/B cover every accent Portuguese needs; the
                    // allow-list holds the punctuation and symbols the UI strings legitimately use.
                    .filter(cp -> cp > 0x24F && "—–…“”‘’×⚠°ºª".indexOf(cp) < 0)
                    .findFirst()
                    .ifPresent(cp -> problems.add(locale + " key '" + key + "' contains U+"
                            + Integer.toHexString(cp).toUpperCase() + " ('" + Character.toString(cp)
                            + "'), which is not Latin script: " + value)));
        }
        assertTrue(problems.isEmpty(), () -> "Stray non-Latin characters:\n  " + String.join("\n  ", problems));
    }

    /** Every shipped locale except the source one. */
    private static List<String> translatedLocales() {
        try (Stream<Path> files = Files.list(LANG)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .filter(n -> !n.equals(SOURCE_LOCALE))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Placeholder kinds to counts, ignoring argument index so a translation may reorder arguments. */
    private static Map<String, Integer> placeholders(String value) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String kind = "%" + matcher.group(2);
            // A positional %N$s may legitimately repeat to reuse one argument; count distinct indices once.
            String slot = matcher.group(1) == null ? kind + "#" + counts.size() : kind + "@" + matcher.group(1);
            counts.merge(slot.startsWith(kind + "@") ? slot : kind, 1, Integer::sum);
        }
        Map<String, Integer> byKind = new LinkedHashMap<>();
        counts.forEach((slot, n) -> byKind.merge(slot.substring(0, 2), slot.contains("@") ? 1 : n, Integer::sum));
        return byKind;
    }

    private static Map<String, String> load(String locale) {
        JsonObject root = JsonParser.parseString(read(LANG.resolve(locale + ".json"))).getAsJsonObject();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static List<Path> dataFiles() {
        try (Stream<Path> files = Files.walk(DATA)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
