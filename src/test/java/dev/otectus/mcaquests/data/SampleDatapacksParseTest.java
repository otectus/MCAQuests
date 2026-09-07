package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.data.ProjectValidator;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.dialogue.VoicePool;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.title.TitleDefinition;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestConfig;
import dev.otectus.mcaquests.support.TestPaths;
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
 * Parses and validates the sample datapacks under {@code datapack_samples/}.
 *
 * <p>The samples are teaching material: ten packs whose whole value is that every field in them is
 * real and every claim their READMEs make about the format is true. That makes them exactly the kind
 * of content that rots silently — they are not loaded by any test fixture, they are not in the jar,
 * and nothing about editing an objective codec would otherwise notice that a documented example
 * stopped parsing. The bundled pack is held to this bar by {@code BuiltinPackParsesTest} and
 * {@code BuiltinPackValidatesTest}; the samples we hand to pack authors deserve the same.
 *
 * <p>It runs the same two passes a datapack reload runs: the codecs decide whether a file loads at
 * all, and the reload-time validators decide whether what loaded is coherent — a chain naming a
 * stage that does not exist, a family target with no gate establishing the relative exists, a
 * {@code failure} block with no trigger.
 *
 * <p>Objectives, conditions and rewards are additionally parsed <em>individually</em>. That is not
 * redundant: several of those fields are read through optional codecs, so one malformed entry can
 * empty a whole list without failing the enclosing definition, and the quest then loads looking
 * healthy with its entire eligibility gate gone.
 */
class SampleDatapacksParseTest {

    private static final Path ROOT = TestPaths.of("datapack_samples");

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    @Test
    @DisplayName("every sample datapack definition parses and validates")
    void samplesParse() {
        List<String> problems = new ArrayList<>();

        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        for (Path file : jsonUnder("quests")) {
            QuestDefinition def = parse(file, QuestDefinition.CODEC, problems);
            if (def != null) {
                quests.put(def.id(), def);
                checkParts(file, problems);
            }
        }

        Map<ResourceLocation, ProjectDefinition> projects = new LinkedHashMap<>();
        for (Path file : jsonUnder("projects")) {
            ProjectDefinition def = parse(file, ProjectDefinition.CODEC, problems);
            if (def != null) {
                projects.put(def.id(), def);
            }
        }

        List<SituationDefinition> situations = new ArrayList<>();
        for (Path file : jsonUnder("situations")) {
            SituationDefinition def = parse(file, SituationDefinition.CODEC, problems);
            if (def != null) {
                situations.add(def);
                JsonElement offer = json(file).getAsJsonObject().get("offer");
                if (offer != null && offer.isJsonObject()) {
                    checkObjectAndConditions(file, offer.getAsJsonObject(), problems);
                }
            }
        }

        for (Path file : jsonUnder("reputation_tiers")) {
            parse(file, ReputationTierSet.CODEC, problems);
        }
        for (Path file : jsonUnder("titles")) {
            parse(file, TitleDefinition.CODEC, problems);
        }
        for (Path file : jsonUnder("dialogue")) {
            parse(file, VoicePool.CODEC, problems);
        }

        assertTrue(quests.size() > 20,
                "the sample scan found almost nothing (" + quests.size() + "); the path has drifted");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        QuestChainValidator.validate(quests, errors, warnings);
        TemplateValidator.validate(quests, errors);
        FailureValidator.validate(quests, errors);
        ObjectiveValidator.validate(quests, errors, warnings);
        TargetGateValidator.validate(quests, errors, warnings);
        TargetGateValidator.validateSituations(situations, errors, warnings);
        AgeEligibilityValidator.validate(quests, warnings);
        ProjectValidator.validate(projects, errors);

        // ProjectValidator reports advisory findings into the same list behind its own prefix, and every
        // one of them here is "MCA is not loaded", which is true of a unit test and of nothing else.
        problems.addAll(errors.stream()
                .filter(e -> !ProjectValidator.isWarning(e))
                // A quest using an FTB Quests objective is skipped at load when FTB Quests is absent,
                // which it always is here. That is the documented behaviour of ftbq_complete_quest and
                // the sample's own README explains it.
                .filter(e -> !e.contains("ftbq_complete_quest"))
                .toList());

        // Warnings count too, which is the whole point of holding *samples* to a stricter bar than a
        // datapack in the wild. An empty tag, a family target with no gate, or adult_only without an
        // age_group are all warning-level for a third-party pack — its author can read the message and
        // decide. Here they would be a worked example quietly teaching the wrong thing, and
        // AgeEligibilityValidator emits nothing else, so without this the changelog would be naming a
        // validator that cannot fail.
        problems.addAll(warnings);

        assertEquals(List.of(), problems, "the sample datapacks do not survive their own validators");
    }

    private static void checkParts(Path file, List<String> problems) {
        JsonElement root = json(file);
        if (!root.isJsonObject()) {
            return;
        }
        checkObjectAndConditions(file, root.getAsJsonObject(), problems);
    }

    private static void checkObjectAndConditions(Path file, JsonObject owner, List<String> problems) {
        if (owner.has("conditions")) {
            DataResult<?> parsed = ConditionTypes.CODEC.parse(JsonOps.INSTANCE, owner.get("conditions"));
            parsed.error().ifPresent(e -> problems.add(file + " conditions: " + e.message()));
        }
        if (owner.has("objectives")) {
            int i = 0;
            for (JsonElement objective : owner.getAsJsonArray("objectives")) {
                DataResult<?> parsed = ObjectiveTypes.CODEC.parse(JsonOps.INSTANCE, objective);
                int index = i++;
                parsed.error().ifPresent(e -> problems.add(file + " objective[" + index + "]: " + e.message()));
            }
        }
        if (owner.has("rewards")) {
            int i = 0;
            for (JsonElement reward : owner.getAsJsonArray("rewards")) {
                DataResult<?> parsed = RewardTypes.CODEC.parse(JsonOps.INSTANCE, reward);
                int index = i++;
                parsed.error().ifPresent(e -> problems.add(file + " reward[" + index + "]: " + e.message()));
            }
        }
        // A template's objective/reward JSON still holds its {placeholders} at rest, so it cannot be
        // parsed raw; TemplateValidator is what substitutes the pools and parses the result.
    }

    private static <A> A parse(Path file, com.mojang.serialization.Codec<A> codec, List<String> problems) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, json(file));
        result.error().ifPresent(e -> problems.add(file + ": " + e.message()));
        return result.result().orElse(null);
    }

    private static JsonElement json(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> jsonUnder(String folder) {
        if (!Files.isDirectory(ROOT)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/mcaquests/" + folder + "/"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
