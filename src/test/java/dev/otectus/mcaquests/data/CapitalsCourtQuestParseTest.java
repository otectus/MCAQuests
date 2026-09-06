package dev.otectus.mcaquests.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the conditional MCA Capitals pack through the real quest and situation codecs, and holds its
 * translation keys to both shipped locales.
 *
 * <p>{@link BuiltinPackParsesTest} is rooted at the shipped datapack and never sees this content: a
 * compat pack lives under {@code compatpacks/} precisely because it is only mounted when the mod it
 * needs is installed. Nothing else in the build would notice a court quest that stopped loading, and
 * the failure would only appear on the installations that have MCA Capitals, which the CI machine
 * does not.
 *
 * <p>The lang half matters as much as the parse half. Every line this pack shows a player is a
 * {@code translate} key, and a key that exists in English but not in Portuguese is not a crash — it is
 * a villager who says {@code mcaquests.quest.capitals_a_lords_due.dialogue.offer} out loud, on a
 * server nobody testing this build is running.
 */
class CapitalsCourtQuestParseTest {

    private static final Path PACK = TestPaths.of("src/main/resources/compatpacks/capitals_court");
    private static final Path LANG = TestPaths.of("src/main/resources/assets/mcaquests/lang");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static List<Path> filesUnder(String folder) {
        try (Stream<Path> files = Files.walk(PACK)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/" + folder + "/"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T parse(Path file, com.mojang.serialization.Codec<T> codec) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, read(file));
        return result.result().orElseThrow(() -> new AssertionError(file + " did not parse: "
                + result.error().map(DataResult.Error::message).orElse("?")));
    }

    /**
     * Every {@code mcaquests.*} string this pack asks the client to render: the {@code translate}
     * fields of titles and dialogue, and the {@code key} of a {@code capital_chronicle} reward, which
     * is rendered server-side but is a translation key all the same.
     */
    private static void collectKeys(JsonElement element, Set<String> into) {
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                boolean named = entry.getKey().equals("translate") || entry.getKey().equals("key");
                if (named && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsString().startsWith("mcaquests.")) {
                    into.add(entry.getValue().getAsString());
                } else {
                    collectKeys(entry.getValue(), into);
                }
            }
        } else if (element instanceof JsonArray array) {
            array.forEach(child -> collectKeys(child, into));
        }
    }

    private static Set<String> localeKeys(String locale) {
        JsonObject json = read(LANG.resolve(locale)).getAsJsonObject();
        return json.keySet();
    }

    @Test
    @DisplayName("every quest in the Capitals pack parses")
    void everyQuestParses() {
        List<Path> quests = filesUnder("quests");
        assertFalse(quests.isEmpty(), "the pack folder should hold quests; check the path");
        for (Path quest : quests) {
            parse(quest, QuestDefinition.CODEC);
        }
    }

    @Test
    @DisplayName("both court situations parse")
    void everySituationParses() {
        List<Path> situations = filesUnder("situations");
        assertFalse(situations.isEmpty(), "the pack folder should hold situations; check the path");
        for (Path situation : situations) {
            parse(situation, SituationDefinition.CODEC);
        }
    }

    @Test
    @DisplayName("every translation key the pack names is in both shipped locales")
    void everyKeyIsTranslated() {
        Set<String> keys = new TreeSet<>();
        for (Path file : filesUnder("quests")) {
            collectKeys(read(file), keys);
        }
        for (Path file : filesUnder("situations")) {
            collectKeys(read(file), keys);
        }
        assertFalse(keys.isEmpty(), "the pack should reference translation keys; check the collector");

        Set<String> english = localeKeys("en_us.json");
        Set<String> portuguese = localeKeys("pt_br.json");
        for (String key : keys) {
            assertTrue(english.contains(key), key + " is missing from en_us.json");
            assertTrue(portuguese.contains(key), key + " is missing from pt_br.json; this pack ships "
                    + "full locale parity and a half-translated court reads worse than none");
        }
    }

    @Test
    @DisplayName("the pack description key is translated too")
    void thePackMetaIsTranslated() {
        Set<String> keys = new TreeSet<>();
        collectKeys(read(PACK.resolve("pack.mcmeta")), keys);
        assertTrue(keys.contains("mcaquests.compatpack.capitals_court"),
                "pack.mcmeta should name the pack with a translation key");
        assertTrue(localeKeys("en_us.json").contains("mcaquests.compatpack.capitals_court"));
        assertTrue(localeKeys("pt_br.json").contains("mcaquests.compatpack.capitals_court"));
    }
}
