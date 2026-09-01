package dev.otectus.mcaquests.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.VillagerTargeted;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the bundled pack to the strict standard the new family-target gate only warns third parties
 * about.
 *
 * <p>The asymmetry is on purpose and is the same one {@code BuiltinPackValidatesTest} draws: a pack
 * author can read a warning and fix their own file, but a player who hits a broken built-in cannot fix
 * anything. So a missing gate is a warning at reload and a failed build here.
 *
 * <p>This is the executable form of the reported bug. {@code relations_letter_to_brother} asked a player
 * to deliver a letter to a sibling who had died; {@code lost_child/2_deeper} and two situations shipped
 * with a family target and no gate at all. If any of that comes back, this test is what says so.
 */
class BuiltinFamilyGateTest {

    private static final Path DATA = Path.of("src/main/resources/data/mcaquests/mcaquests");

    static {
        TestBootstrap.ensureBootstrapped();
        TestConfig.ensureCommonLoaded();
    }

    @Test
    @DisplayName("no built-in quest names a villager it has not established exists")
    void questsGateTheirFamilyTargets() {
        Map<ResourceLocation, QuestDefinition> quests =
                loadAll("quests", QuestDefinition.CODEC, QuestDefinition::id);
        assertTrue(quests.size() > 200, "the quest scan found almost nothing; the path has drifted");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        TargetGateValidator.validate(quests, errors, warnings);

        assertEquals(List.of(), errors, "a bundled quest names a relative nothing proves exists");
    }

    @Test
    @DisplayName("no built-in situation names a villager it has not established exists")
    void situationsGateTheirFamilyTargets() {
        Map<ResourceLocation, SituationDefinition> situations =
                loadAll("situations", SituationDefinition.CODEC, SituationDefinition::id);
        assertTrue(situations.size() >= 20, "the situation scan found almost nothing; the path has drifted");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        TargetGateValidator.validateSituations(situations.values(), errors, warnings);

        assertEquals(List.of(), errors, "a bundled situation names a relative nothing proves exists");
    }

    /**
     * Every family target states its {@code require} outright.
     *
     * <p>The default is the safe one, so this is not a correctness requirement — it is a legibility one.
     * A reader of the JSON should be able to see which villagers a quest is willing to be about without
     * knowing what the code defaults to, and shipping content that exercises every value is what keeps the
     * validator honest.
     */
    @Test
    @DisplayName("every built-in family target says which villagers it is willing to name")
    void familyTargetsDeclareRequireExplicitly() {
        List<String> implicit = new ArrayList<>();
        forEachFamilyTarget((where, target) -> {
            if (target.require().isEmpty()) {
                implicit.add(where);
            }
        });
        assertEquals(List.of(), implicit, "these bundled family targets rely on the default 'require'");
    }

    @Test
    @DisplayName("the shipped pack exercises the deliberately-unsafe requires, not just the safe default")
    void requireValuesAreExercised() {
        List<String> used = new ArrayList<>();
        forEachFamilyTarget((where, target) -> used.add(target.effectiveRequire()));

        assertTrue(used.contains("missing"),
                "no bundled quest targets a missing relative any more; the missing-kin path is untested");
        assertTrue(used.contains("same_village"), "no bundled quest targets a same-village relative");
        assertTrue(used.contains("nearby"), "no bundled quest targets a nearby relative");
        for (String require : used) {
            assertTrue(RelativeCandidate.STATUSES.contains(require),
                    "a bundled quest requires an unknown status: " + require);
        }
    }

    @Test
    @DisplayName("the disjointness table only names statuses that exist")
    void disjointTableIsWellFormed() {
        for (String a : RelativeCandidate.STATUSES) {
            for (String b : RelativeCandidate.STATUSES) {
                if (TargetGateValidator.disjoint(a, b)) {
                    assertTrue(TargetGateValidator.disjoint(b, a),
                            "disjointness must be symmetric, but " + a + "/" + b + " is not");
                }
            }
        }
        // A missing relative is alive; that is the whole reason a missing-kin quest can be about them.
        assertFalse(TargetGateValidator.disjoint("missing", "alive"));
        assertFalse(TargetGateValidator.disjoint("nearby", "reachable"));
        // The pair that produced the reported bug, and the pair widow_memorial would have hit.
        assertTrue(TargetGateValidator.disjoint("reachable", "dead"));
        assertTrue(TargetGateValidator.disjoint("same_village", "missing"));
    }

    private interface TargetVisitor {
        void accept(String where, VillagerTarget target);
    }

    private static void forEachFamilyTarget(TargetVisitor visitor) {
        loadAll("quests", QuestDefinition.CODEC, QuestDefinition::id)
                .forEach((id, quest) -> visit("Quest '" + id + "'", quest.objectives(), visitor));
        loadAll("situations", SituationDefinition.CODEC, SituationDefinition::id)
                .forEach((id, situation) ->
                        visit("Situation '" + id + "'", situation.offer().objectives(), visitor));
    }

    private static void visit(String label, List<QuestObjective> objectives, TargetVisitor visitor) {
        for (int index = 0; index < objectives.size(); index++) {
            if (objectives.get(index) instanceof VillagerTargeted targeted
                    && targeted.targetSelector().mode() == VillagerTarget.Mode.FAMILY) {
                visitor.accept(label + ": objective[" + index + "]", targeted.targetSelector());
            }
        }
    }

    private static <T> Map<ResourceLocation, T> loadAll(String directory, Codec<T> codec,
                                                        Function<T, ResourceLocation> id) {
        Map<ResourceLocation, T> loaded = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(DATA.resolve(directory))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                Path source = path;
                T parsed = codec.parse(JsonOps.INSTANCE, json).resultOrPartial(error -> {
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
