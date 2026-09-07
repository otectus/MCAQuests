package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.QuestDifficulty;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireRegistryManifest;
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
 * Parses every shipped quest, project, and situation through its real codec.
 *
 * <p>The 1.1.0 pass rewrote all ~220 data files — difficulty bands, rebalanced hearts, semantic currency
 * rewards, and the translate-key migration. A codec is the only thing that decides whether those files
 * actually load in game, and DataFixerUpper is lenient about unknown fields: a mistyped or misplaced key
 * is silently ignored rather than rejected. This catches a file that stopped parsing, and separately
 * asserts that the new fields really landed where the codec reads them rather than being quietly dropped.
 */
class BuiltinPackParsesTest {

    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("every built-in quest parses")
    void questsParse() {
        assertAllParse("quests", QuestDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in project parses")
    void projectsParse() {
        assertAllParse("projects", ProjectDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in situation parses")
    void situationsParse() {
        assertAllParse("situations", SituationDefinition.CODEC);
    }

    @Test
    @DisplayName("every built-in quest declares a difficulty the codec actually reads")
    void everyQuestHasADifficulty() {
        List<String> missing = new ArrayList<>();
        for (Path file : jsonUnder("quests")) {
            QuestDefinition def = parseOrThrow(file, QuestDefinition.CODEC);
            if (def.difficulty().isEmpty()) {
                missing.add(file.toString());
            }
        }
        assertEquals(List.of(), missing, "these built-in quests have no difficulty band, so their currency "
                + "rewards would silently fall back to MEDIUM");
    }

    @Test
    @DisplayName("the rebalanced hearts values are exactly the three difficulty bands")
    void heartsFollowTheDifficultyBands() {
        // Guards the balance pass itself: a stray hand-edit that reintroduces a 35-heart repeatable would
        // put marriage back within a few in-game days.
        List<String> offenders = new ArrayList<>();
        for (Path file : jsonUnder("quests")) {
            String raw = read(file);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"type\"\\s*:\\s*\"mcaquests:hearts\"\\s*,\\s*\"amount\"\\s*:\\s*(\\d+)")
                    .matcher(raw);
            while (m.find()) {
                int amount = Integer.parseInt(m.group(1));
                if (amount != 4 && amount != 8 && amount != 14) {
                    offenders.add(file + " grants " + amount + " hearts");
                }
            }
        }
        assertEquals(List.of(), offenders, "built-in hearts rewards must stay on the 4 / 8 / 14 bands");
    }

    @Test
    @DisplayName("difficulty round-trips through the codec for each band")
    void difficultyRoundTrips() {
        for (QuestDifficulty band : QuestDifficulty.values()) {
            DataResult<QuestDifficulty> parsed = QuestDifficulty.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString('"' + band.lower() + '"'));
            assertEquals(band, parsed.result().orElseThrow(
                    () -> new AssertionError("difficulty '" + band.lower() + "' did not parse")));
        }
        assertTrue(QuestDifficulty.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"impossible\""))
                .error().isPresent(), "an unknown difficulty should be a parse error, not a silent default");
    }

    /**
     * Runs the loader's own {@link AgeEligibilityValidator} over codec-parsed quests — the path the game
     * actually takes.
     *
     * <p>{@code BuiltinAgeEligibilityTest} walks the raw JSON with Gson instead, so it can only prove the
     * files say the right thing; it cannot see a condition tree that parses into something the validator
     * does not recognise. A live game warned that {@code relations_child_treat} had no age_group
     * condition while its JSON plainly carries one, which is exactly the gap between those two views.
     */
    @Test
    @DisplayName("no built-in quest trips the age-eligibility warning after codec parsing")
    void ageEligibilityIsCleanAfterCodecParsing() {
        Map<ResourceLocation, QuestDefinition> loaded = new LinkedHashMap<>();
        for (Path file : jsonUnder("quests")) {
            QuestDefinition def = parseOrThrow(file, QuestDefinition.CODEC);
            loaded.put(def.id(), def);
        }
        assertTrue(!loaded.isEmpty(), "no quests parsed, so this would pass vacuously");

        List<String> warnings = new ArrayList<>();
        AgeEligibilityValidator.validate(loaded, warnings);
        assertEquals(List.of(), warnings,
                "QuestDataLoader logs these verbatim at runtime; the shipped pack must not produce any");
    }

    /**
     * A file that declares {@code conditions} must still have them after parsing.
     *
     * <p>{@code assertAllParse} cannot see this: {@code QuestDefinition} reads the field through
     * {@code optionalFieldOf("conditions")}, and DFU's implementation of that turns a decode failure
     * into {@code Optional.empty()} with no error on the result. The quest then loads looking perfectly
     * healthy, with its entire eligibility gate gone — which is how 36 shipped quests, including ones
     * gated on being the player's own child or spouse, came to be offered by any villager at all.
     *
     * @see dev.otectus.mcaquests.quest.DispatchedCodecInlinesTest for the cause that produced it
     */
    @Test
    @DisplayName("no quest, project or situation silently loses declared conditions")
    void declaredConditionsSurviveParsing() {
        List<String> lost = new ArrayList<>();
        for (String folder : List.of("quests", "projects", "situations")) {
            for (Path file : jsonUnder(folder)) {
                JsonElement root = json(file);
                if (!root.isJsonObject()) {
                    continue;
                }
                checkConditions(file, root.getAsJsonObject(), lost);
                // A situation's gate lives inside its offer, and reading only the top level is how it
                // went unnoticed until 1.4.1 that the offer codec was dropping the whole block.
                JsonElement offer = root.getAsJsonObject().get("offer");
                if (offer != null && offer.isJsonObject()) {
                    checkConditions(file, offer.getAsJsonObject(), lost);
                }
            }
        }
        assertEquals(List.of(), lost, "these files declare conditions that do not parse; at runtime the "
                + "gate is silently dropped rather than reported");
    }

    private static void checkConditions(Path file, com.google.gson.JsonObject owner, List<String> lost) {
        if (!owner.has("conditions")) {
            return;
        }
        DataResult<QuestCondition> parsed = ConditionTypes.CODEC
                .parse(JsonOps.INSTANCE, owner.get("conditions"));
        if (parsed.result().isEmpty()) {
            lost.add(file + ": " + parsed.error().map(DataResult.PartialResult::message).orElse("?"));
        }
    }

    /**
     * A declared gate must survive the codec that owns it, not merely parse in isolation.
     *
     * <p>Situation offers accepted a {@code conditions} block in the JSON and threw it away, so every
     * bundled Townstead situation shipped 1.4.0 with a capability gate that read like a gate and was
     * not one. Round-tripping the whole definition and looking for the block on the other side is the
     * only check that would have caught that.
     */
    @Test
    @DisplayName("a situation offer's condition gate survives its own codec")
    void situationOfferConditionsRoundTrip() {
        List<String> dropped = new ArrayList<>();
        for (Path file : jsonUnder("situations")) {
            JsonElement root = json(file);
            JsonElement offer = root.getAsJsonObject().get("offer");
            if (offer == null || !offer.isJsonObject() || !offer.getAsJsonObject().has("conditions")) {
                continue;
            }
            SituationDefinition parsed = SituationDefinition.CODEC.parse(JsonOps.INSTANCE, root)
                    .result().orElse(null);
            if (parsed == null) {
                dropped.add(file + ": did not decode");
            } else if (parsed.offer().conditions().isEmpty()) {
                dropped.add(file.toString());
            }
        }
        assertEquals(List.of(), dropped, "these situations declare an offer gate that the codec "
                + "discards, so it never runs");
    }

    /**
     * Every objective a file declares must decode on its own.
     *
     * <p>{@code QuestDefinition} reads {@code objectives} through {@code optionalFieldOf} as well, so one
     * malformed entry empties the whole list without an error: the quest loads, is offered, and is
     * instantly turn-in ready with nothing to do. That is how seven shipped {@code defend_location}
     * objectives that wrote the threat flat (a top-level {@code "entity"} instead of a nested
     * {@code "threat"} object) went unnoticed until 1.6.0. Situation offers are checked too, since their
     * objectives ride the same codec. Conditional compat packs are included because they are shipped
     * content just the same.
     */
    @Test
    @DisplayName("no quest or situation offer silently loses declared objectives")
    void declaredObjectivesSurviveParsing() {
        List<String> lost = new ArrayList<>();
        List<Path> roots = new ArrayList<>();
        roots.add(DATA);
        try (Stream<Path> packs = Files.list(Path.of("src/main/resources/compatpacks"))) {
            packs.filter(Files::isDirectory)
                    .map(p -> p.resolve("data/mcaquests/mcaquests"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(roots::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path root : roots) {
            for (String folder : List.of("quests", "situations")) {
                Path dir = root.resolve(folder);
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                for (Path file : jsonUnder(dir)) {
                    JsonElement parsed = json(file);
                    if (!parsed.isJsonObject()) {
                        continue;
                    }
                    checkObjectives(file, parsed.getAsJsonObject(), lost);
                    JsonElement offer = parsed.getAsJsonObject().get("offer");
                    if (offer != null && offer.isJsonObject()) {
                        checkObjectives(file, offer.getAsJsonObject(), lost);
                    }
                }
            }
        }
        assertEquals(List.of(), lost, "these files declare objectives that do not parse; at runtime the "
                + "list is silently emptied rather than reported");
    }

    private static void checkObjectives(Path file, com.google.gson.JsonObject owner, List<String> lost) {
        JsonElement objectives = owner.get("objectives");
        if (objectives == null || !objectives.isJsonArray()) {
            return;
        }
        int index = 0;
        for (JsonElement objective : objectives.getAsJsonArray()) {
            DataResult<dev.otectus.mcaquests.quest.objective.QuestObjective> parsed =
                    dev.otectus.mcaquests.quest.objective.ObjectiveTypes.CODEC.parse(JsonOps.INSTANCE,
                            structuralObjective(file, objective));
            if (parsed.result().isEmpty()) {
                lost.add(file + " objectives[" + index + "]: "
                        + parsed.error().map(DataResult.PartialResult::message).orElse("?"));
            }
            index++;
        }
    }

    /**
     * Exercise all fields of the optional armor objectives with a present registry entry. Only the
     * five exact CE armor IDs, whose declarations and recipe results are checked by the real-jar
     * probe, receive a test-only stand-in. Unknown IDs and malformed fields still fail their codec;
     * production parsing never substitutes an item or falls back to AIR.
     */
    private static JsonElement structuralObjective(Path file, JsonElement objective) {
        if (!file.toString().replace('\\', '/').contains("/compatpacks/iafce_quests/")
                || !objective.isJsonObject()) return objective;
        var object = objective.getAsJsonObject();
        JsonElement item = object.get("item");
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) return objective;
        String id = item.getAsString();
        boolean knownArmor = IceAndFireRegistryManifest.NETHERITE_DRAGON_ARMOR.stream()
                .anyMatch(armor -> armor.toString().equals(id))
                || IceAndFireRegistryManifest.NETHERITE_HIPPOGRYPH_ARMOR.toString().equals(id);
        if (!knownArmor) return objective;
        var copy = object.deepCopy();
        copy.addProperty("item", "minecraft:stone");
        return copy;
    }

    @Test
    void optionalArmorStandInsDoNotHideUnknownIdsOrMalformedObjectiveFields() {
        Path file = Path.of("src/main/resources/compatpacks/iafce_quests/test.json");
        var valid = JsonParser.parseString("{\"type\":\"mcaquests:obtain_item\","
                + "\"item\":\"iceandfire:dragonarmor_netherite_head\",\"count\":1}");
        var codec = dev.otectus.mcaquests.quest.objective.ObjectiveTypes.CODEC;
        assertTrue(codec.parse(JsonOps.INSTANCE, valid).error().isPresent(), "the real registry remains absent");
        assertTrue(codec.parse(JsonOps.INSTANCE, structuralObjective(file, valid)).result().isPresent());
        for (String mutation : List.of("\"count\":0", "\"count\":\"bad\"")) {
            var bad = JsonParser.parseString(valid.toString().replace("\"count\":1", mutation));
            assertTrue(codec.parse(JsonOps.INSTANCE, structuralObjective(file, bad)).error().isPresent());
        }
        var unknown = JsonParser.parseString(valid.toString().replace("dragonarmor_netherite_head", "typo_armor"));
        assertTrue(codec.parse(JsonOps.INSTANCE, structuralObjective(file, unknown)).error().isPresent());
        assertTrue(codec.parse(JsonOps.INSTANCE, structuralObjective(DATA.resolve("quests/test.json"), valid))
                .error().isPresent(), "base content receives no optional-registry substitution");
    }

    private static <T> void assertAllParse(String folder, com.mojang.serialization.Codec<T> codec) {
        List<String> failures = new ArrayList<>();
        List<Path> files = jsonUnder(folder);
        assertTrue(!files.isEmpty(), "no files found under " + DATA.resolve(folder).toAbsolutePath());
        for (Path file : files) {
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json(file));
            result.error().ifPresent(err -> failures.add(file + ": " + err.message()));
        }
        assertEquals(List.of(), failures, "built-in " + folder + " that no longer parse");
    }

    private static <T> T parseOrThrow(Path file, com.mojang.serialization.Codec<T> codec) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json(file));
        return result.result().orElseThrow(() -> new AssertionError(
                file + " did not parse: " + result.error().map(DataResult.PartialResult::message).orElse("?")));
    }

    private static List<Path> jsonUnder(String folder) {
        return jsonUnder(DATA.resolve(folder));
    }

    private static List<Path> jsonUnder(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement json(Path file) {
        return JsonParser.parseString(read(file));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
