package dev.otectus.mcaquests.project.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.support.FakeTownstead;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building state (brief C04a, INN-01..03) and the workforce trade policy (C02, WORK-01..03), at the
 * level a unit test can reach: the decisions, not the world reads.
 */
class TownsteadBuildingAndWorkforceTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @AfterEach
    void reset() {
        TownsteadBuildingProjectObjective.setCensusForTest(null);
        FakeTownstead.uninstall();
    }

    private static ProjectState state() {
        return new ProjectState(new ResourceLocation("test", "p"), ProjectScope.VILLAGE, "v:1",
                new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO, OptionalInt.of(1), 0L, 1);
    }

    @Test
    @DisplayName("INN-01/02: a registered inn counts whenever it was built; extra inns do not churn every sweep")
    void buildingIsStateAndComparesClampedCount() {
        FakeTownstead.install(EnumSet.of(TownsteadCapability.READ_BUILDING));
        TownsteadBuildingProjectObjective inn = new TownsteadBuildingProjectObjective("inn", 1, 1);
        ProjectState state = state();
        SharedObjectiveProgress progress = state.progress(0);

        TownsteadBuildingProjectObjective.setCensusForTest((level, village, type, min) ->
                new TownsteadEvaluation.BuildingCensus(true, 0, 0));
        assertFalse(inn.poll(null, null, null, state, progress));
        TownsteadBuildingProjectObjective.setCensusForTest((level, village, type, min) ->
                new TownsteadEvaluation.BuildingCensus(true, 3, 0));
        assertTrue(inn.poll(null, null, null, state, progress), "an inn registered after the phase began counts");
        assertEquals(1, progress.count());
        assertFalse(inn.poll(null, null, null, state, progress), "three inns for a count of one is not a change");
    }

    @Test
    @DisplayName("INN-03: an unreadable village is unknown, not zero, and an incomplete inn does not count")
    void unreadableIsUnknownAndIncompleteDoesNotCount() {
        FakeTownstead.install(EnumSet.of(TownsteadCapability.READ_BUILDING));
        TownsteadBuildingProjectObjective inn = new TownsteadBuildingProjectObjective("inn", 1, 1);
        ProjectState state = state();
        SharedObjectiveProgress progress = state.progress(0);
        progress.setCount(1);
        TownsteadBuildingProjectObjective.setCensusForTest((level, village, type, min) ->
                TownsteadEvaluation.BuildingCensus.UNREADABLE);
        assertFalse(inn.poll(null, null, null, state, progress));
        assertEquals(1, progress.count(), "a failed lookup must not zero progress");

        TownsteadBuildingProjectObjective.setCensusForTest((level, village, type, min) ->
                new TownsteadEvaluation.BuildingCensus(true, 0, 1));
        assertTrue(inn.poll(null, null, null, state, progress));
        assertEquals(0, progress.count(), "a registered but incomplete inn is not an inn yet");
        assertEquals(1, progress.extra().getInt(TownsteadBuildingProjectObjective.K_INCOMPLETE));
    }

    @Test
    @DisplayName("WORK-03: listed stays listed; any_progressive admits every trade, the track check still applies")
    void professionPolicy() {
        TownsteadWorkforceProjectObjective listed = new TownsteadWorkforceProjectObjective(
                List.of("minecraft:farmer"), 2, 3);
        TownsteadWorkforceProjectObjective any = new TownsteadWorkforceProjectObjective(
                List.of("minecraft:farmer"), 2, 3, TownsteadWorkforceProjectObjective.ProfessionPolicy.ANY_PROGRESSIVE);
        assertTrue(listed.eligibleTrade("minecraft:farmer"));
        assertFalse(listed.eligibleTrade("minecraft:fisherman"), "an explicit list is not broadened");
        assertTrue(any.eligibleTrade("minecraft:fisherman"));
        assertEquals(TownsteadWorkforceProjectObjective.ProfessionPolicy.LISTED,
                TownsteadWorkforceProjectObjective.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"professions\":[\"minecraft:farmer\"]}")).result().orElseThrow().professionPolicy(),
                "omitting the field keeps every existing datapack's behaviour");
    }

    @Test
    @DisplayName("the bundled workforce projects opt into any progressive trade")
    void bundledWorkforceOptsIn() throws Exception {
        for (String file : List.of("a_working_village.json", "townstead_apprentices_guild.json")) {
            ProjectDefinition def = ProjectDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/data/mcaquests/mcaquests/projects/townstead", file)))).result().orElseThrow();
            def.phases().stream().flatMap(phase -> phase.objectives().stream())
                    .filter(objective -> objective instanceof TownsteadWorkforceProjectObjective)
                    .forEach(objective -> assertEquals(TownsteadWorkforceProjectObjective.ProfessionPolicy.ANY_PROGRESSIVE,
                            ((TownsteadWorkforceProjectObjective) objective).professionPolicy(), file));
        }
    }
}
