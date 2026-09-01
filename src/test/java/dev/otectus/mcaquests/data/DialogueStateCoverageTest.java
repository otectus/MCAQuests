package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every line a quest can write must be a line the game will say.
 *
 * <p>{@code decline}, {@code cooldown} and {@code locked} were not. All three were declared constants,
 * all three were advertised by the in-game schema help, every shipped quest authored a {@code decline}
 * line — and none of the three was ever passed to {@code dialogueOr}, so the villager never said any of
 * them. The {@code decline} one is the sharpest: a player turned an offer down and the villager, who had
 * a written response to exactly that, said nothing at all.
 *
 * <p>Two directions, because the mistake can be made both ways:
 * <ul>
 *   <li>a state the code declares but never asks for is a promise to pack authors that is not kept;</li>
 *   <li>a state the shipped JSON writes but the code has never heard of is a line nobody will ever read.</li>
 * </ul>
 */
class DialogueStateCoverageTest {

    static {
        // Reading a static field forces QuestDefinition's <clinit>, which builds its codec and reaches
        // EntityType/BuiltInRegistries. Those assert the game is bootstrapped.
        TestBootstrap.ensureBootstrapped();
    }

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");

    @Test
    @DisplayName("every declared dialogue state is requested somewhere in the mod")
    void everyDeclaredStateIsUsed() {
        Set<String> declared = declaredStates();
        assertTrue(declared.size() >= 8,
                "the scan for dialogue-state constants found almost nothing; QuestDefinition has changed shape");

        String sources = allSources();
        List<String> unused = new ArrayList<>();
        for (String constant : declaredConstantNames()) {
            // The declaration itself lives in QuestDefinition, so a constant used nowhere else appears
            // exactly once across the whole of main.
            if (countOccurrences(sources, "QuestDefinition." + constant) == 0
                    && countOccurrences(sources, "dialogueOr(" + constant) == 0) {
                unused.add(constant);
            }
        }
        assertEquals(List.of(), unused, "these dialogue states are declared and never shown to a player; "
                + "either display them or delete the constant and the JSON that writes them");
    }

    @Test
    @DisplayName("every dialogue key the shipped content writes is a state the code knows")
    void everyAuthoredStateIsDeclared() {
        Set<String> declared = declaredStates();
        Set<String> authored = new TreeSet<>();
        collectAuthoredStates(authored);

        assertTrue(authored.contains("offer"), "the content scan found no dialogue at all; the path drifted");
        List<String> unknown = authored.stream().filter(state -> !declared.contains(state)).toList();
        assertEquals(List.of(), unknown, "the bundled content writes dialogue under keys nothing reads: "
                + "a line no player will ever see");
    }

    /** The values of {@code QuestDefinition}'s {@code public static final String} state constants. */
    private static Set<String> declaredStates() {
        Set<String> states = new LinkedHashSet<>();
        for (Field field : QuestDefinition.class.getDeclaredFields()) {
            if (isStateConstant(field)) {
                try {
                    states.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return states;
    }

    private static List<String> declaredConstantNames() {
        List<String> names = new ArrayList<>();
        for (Field field : QuestDefinition.class.getDeclaredFields()) {
            if (isStateConstant(field)) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /**
     * A dialogue-state constant: a public static final String on {@code QuestDefinition}.
     *
     * <p>Every one of them is a state name and nothing else on that record is a bare String constant, so
     * the shape is enough — and deliberately so: a new state added tomorrow is caught without anyone
     * remembering to add it to a list here.
     */
    private static boolean isStateConstant(Field field) {
        int modifiers = field.getModifiers();
        return field.getType() == String.class
                && Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers);
    }

    /** Every {@code dialogue} key used by a shipped quest or situation offer. */
    private static void collectAuthoredStates(Set<String> into) {
        walkJson(DATA, json -> {
            if (!json.isJsonObject()) {
                return;
            }
            JsonObject object = json.getAsJsonObject();
            collectDialogueKeys(object, into);
            if (object.has("offer") && object.get("offer").isJsonObject()) {
                collectDialogueKeys(object.getAsJsonObject("offer"), into);
            }
        });
    }

    private static void collectDialogueKeys(JsonObject object, Set<String> into) {
        if (object.has("dialogue") && object.get("dialogue").isJsonObject()) {
            into.addAll(object.getAsJsonObject("dialogue").keySet());
        }
    }

    private static void walkJson(Path root, java.util.function.Consumer<JsonElement> visitor) {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                visitor.accept(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String allSources() {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (path.endsWith("QuestDefinition.java")) {
                    continue; // where they are declared, which is not where they are used
                }
                all.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return all.toString();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
