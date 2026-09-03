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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The bundled pack cannot ask Townstead for something Townstead 0.7.6 will never deliver.
 *
 * <p>This is the standing guard on the 1.4.0 defect. Three shipped quests asked a fisherman or a
 * leatherworker for profession XP; Townstead's registry answers a progression query for every
 * profession, so the quests parsed, validated, were offered, were accepted — and then waited forever.
 * A datapack author can fix that in their own pack. A player who hit it in the bundled pack could not.
 *
 * <p>So bundled content is held to a stricter rule than third-party content is: it may only name a
 * profession whose progression Townstead actually ships, and every profession-progress objective must
 * carry the {@code townstead_profession_track} gate that re-checks the claim against the <em>loaded</em>
 * registry at runtime. A pack that supplies a real fisherman track can still use one — that is what the
 * runtime gate is for — but the jar may not assume one exists.
 *
 * <p>Reads the JSON directly rather than through the codecs: this is a question about what the files
 * say, and it must keep working even if a future refactor changes how they are parsed.
 */
class BuiltinTownsteadAchievabilityTest {

    private static final Path DATA = TestPaths.of("src/main/resources/data/mcaquests/mcaquests");

    /**
     * The professions Townstead 0.7.6's {@code ProfessionXpType} supplies progression for. Cook is
     * included for completeness even though MCA has no cook; fisherman and leatherworker are absent
     * because their work tasks award no XP at all in the inspected source.
     */
    private static final Set<String> SUPPORTED_TRACKS = Set.of(
            "minecraft:farmer", "minecraft:butcher", "minecraft:shepherd", "minecraft:cook");

    private static final String PROFESSION_PROGRESS = "mcaquests:townstead_profession_progress";
    private static final String PROFESSION_XP_REWARD = "mcaquests:townstead_profession_xp";
    private static final String TRACK_CONDITION = "mcaquests:townstead_profession_track";

    @Test
    @DisplayName("no bundled profession objective names a trade Townstead cannot advance")
    void objectivesNameOnlySupportedTracks() {
        List<String> offenders = new ArrayList<>();
        forEachDefinition((path, root) -> {
            for (JsonObject objective : objectivesOf(root)) {
                if (!PROFESSION_PROGRESS.equals(stringOr(objective, "type"))) {
                    continue;
                }
                String profession = stringOr(objective, "profession");
                if (!profession.isEmpty() && !SUPPORTED_TRACKS.contains(profession)) {
                    offenders.add(path.getFileName() + " asks " + profession + " to advance");
                }
            }
        });
        assertEquals(List.of(), offenders,
                "Townstead 0.7.6 awards no progression XP for these trades, so the quest would be "
                        + "accepted and then wait forever. Either drop the profession field and gate on "
                        + "townstead_profession_track, or ask for something the villager can actually do.");
    }

    @Test
    @DisplayName("no bundled profession-XP reward names a trade that would silently swallow it")
    void rewardsNameOnlySupportedTracks() {
        List<String> offenders = new ArrayList<>();
        forEachDefinition((path, root) -> {
            for (JsonObject reward : rewardsOf(root)) {
                if (!PROFESSION_XP_REWARD.equals(stringOr(reward, "type"))) {
                    continue;
                }
                String profession = stringOr(reward, "profession");
                if (!profession.isEmpty() && !SUPPORTED_TRACKS.contains(profession)) {
                    offenders.add(path.getFileName() + " pays profession XP to " + profession);
                }
            }
        });
        assertEquals(List.of(), offenders,
                "a reward that quietly does nothing is worse than no reward: the player is told they "
                        + "advanced someone's trade and they did not.");
    }

    @Test
    @DisplayName("every bundled profession objective carries the runtime track gate")
    void objectivesCarryTheTrackGate() {
        List<String> offenders = new ArrayList<>();
        forEachDefinition((path, root) -> {
            boolean needsGate = objectivesOf(root).stream()
                    .anyMatch(o -> PROFESSION_PROGRESS.equals(stringOr(o, "type")));
            if (needsGate && !mentions(root, TRACK_CONDITION)) {
                offenders.add(path.getFileName().toString());
            }
        });
        assertEquals(List.of(), offenders,
                "a profession-progress objective needs " + TRACK_CONDITION + " in its condition gate, "
                        + "so a registry that changes under the world hides the offer instead of "
                        + "stranding it.");
    }

    @Test
    @DisplayName("the three quests that could not be finished under 0.7.6 no longer ask for XP")
    void theKnownBadThreeAreRepaired() {
        for (String file : List.of("deep_water_days.json", "the_master_tanner.json")) {
            JsonObject root = read(DATA.resolve("quests/townstead").resolve(file));
            assertFalse(objectivesOf(root).stream()
                            .anyMatch(o -> PROFESSION_PROGRESS.equals(stringOr(o, "type"))),
                    file + " still asks for profession progress on a trade Townstead cannot advance");
        }
        // master_of_the_trade keeps its objective -- it deliberately names no profession, so it means
        // "whatever this villager does" -- but its giver list must no longer include the two dead trades.
        JsonObject trade = read(DATA.resolve("quests/townstead/master_of_the_trade.json"));
        Set<String> givers = new LinkedHashSet<>();
        for (JsonElement element : trade.getAsJsonObject("giver").getAsJsonArray("professions")) {
            givers.add(element.getAsString());
        }
        assertEquals(Set.of("minecraft:farmer", "minecraft:shepherd", "minecraft:butcher"), givers,
                "master_of_the_trade may only be offered by a trade whose track Townstead ships");
    }

    @Test
    @DisplayName("no bundled definition names a Townstead skill, which 0.7.6 ships none of")
    void nothingNamesASkill() {
        List<String> offenders = new ArrayList<>();
        forEachDefinition((path, root) -> {
            if (mentions(root, "mcaquests:townstead_skill")) {
                offenders.add(path.getFileName().toString());
            }
        });
        assertEquals(List.of(), offenders,
                "Townstead 0.7.6 has a skill registry but bundles no skill definitions, so a bundled "
                        + "skill id would name nothing. The types stay available to datapacks.");
    }

    // --- plumbing --------------------------------------------------------------------------------

    private interface Visitor {
        void accept(Path path, JsonObject root);
    }

    /** Every quest, project and situation in the bundled pack. */
    private static void forEachDefinition(Visitor visitor) {
        for (String directory : List.of("quests", "projects", "situations")) {
            Path root = DATA.resolve(directory);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .sorted()
                        .forEach(path -> visitor.accept(path, read(path)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Objectives of a quest, of a situation's offer, or of every phase of a project — the three places
     * an objective can live, walked together so no shape escapes the check.
     */
    private static List<JsonObject> objectivesOf(JsonObject root) {
        List<JsonObject> out = new ArrayList<>();
        collectObjects(root.getAsJsonArray("objectives"), out);
        JsonObject offer = root.getAsJsonObject("offer");
        if (offer != null) {
            collectObjects(offer.getAsJsonArray("objectives"), out);
        }
        JsonArray phases = root.getAsJsonArray("phases");
        if (phases != null) {
            for (JsonElement phase : phases) {
                collectObjects(phase.getAsJsonObject().getAsJsonArray("objectives"), out);
            }
        }
        return out;
    }

    /** Rewards, including a project's per-phase rewards, which nest the reward under {@code reward}. */
    private static List<JsonObject> rewardsOf(JsonObject root) {
        List<JsonObject> out = new ArrayList<>();
        collectObjects(root.getAsJsonArray("rewards"), out);
        JsonObject offer = root.getAsJsonObject("offer");
        if (offer != null) {
            collectObjects(offer.getAsJsonArray("rewards"), out);
        }
        JsonArray phases = root.getAsJsonArray("phases");
        if (phases != null) {
            for (JsonElement phase : phases) {
                JsonArray rewards = phase.getAsJsonObject().getAsJsonArray("rewards");
                if (rewards == null) {
                    continue;
                }
                for (JsonElement entry : rewards) {
                    JsonObject object = entry.getAsJsonObject();
                    JsonObject nested = object.getAsJsonObject("reward");
                    out.add(nested != null ? nested : object);
                }
            }
        }
        return out;
    }

    private static void collectObjects(JsonArray array, List<JsonObject> out) {
        if (array == null) {
            return;
        }
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                out.add(element.getAsJsonObject());
            }
        }
    }

    private static boolean mentions(JsonObject root, String type) {
        return root.toString().contains('"' + type + '"');
    }

    private static String stringOr(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
