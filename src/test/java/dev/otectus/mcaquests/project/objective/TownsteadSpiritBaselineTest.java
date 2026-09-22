package dev.otectus.mcaquests.project.objective;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectPhases;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.support.FakeTownstead;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spirit baselines (brief C04b, SPIRIT-01..04): taken at the boundary they describe, kept apart from the
 * current reading and the credited progress, never reset by a reload, and never fabricated for a
 * record from before they existed.
 */
class TownsteadSpiritBaselineTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** The village's Tourism, as the fake Townstead reports it; empty means unreadable. */
    private OptionalInt tourism = OptionalInt.of(0);

    @BeforeEach
    void install() {
        FakeTownstead.install(EnumSet.of(TownsteadCapability.READ_SPIRIT));
        ProjectPhases.setSpiritReaderForTest((level, village) -> tourism.isPresent()
                ? Optional.of(new TownsteadSpiritView(village, Map.of("tourism", tourism.getAsInt()),
                        tourism.getAsInt(), 1, 0, "single", "tourism", ""))
                : Optional.empty());
    }

    @AfterEach
    void uninstall() {
        ProjectPhases.setSpiritReaderForTest(null);
        FakeTownstead.uninstall();
    }

    private static ProjectDefinition project(String id, String baseline) {
        String json = "{\"id\":\"" + id + "\",\"scope\":\"village\",\"sponsor\":{},\"phases\":["
                + "{\"key\":\"welcome_fund\",\"objectives\":[{\"type\":\"mcaquests:donate_item\",\"item\":\"minecraft:cake\",\"count\":1}]},"
                + "{\"key\":\"open_our_doors\",\"objectives\":[{\"type\":\"mcaquests:townstead_building_project\",\"building_type\":\"inn\"},"
                + "{\"type\":\"mcaquests:townstead_spirit_project\",\"spirit\":\"tourism\",\"points_delta\":2"
                + (baseline == null ? "" : ",\"baseline\":\"" + baseline + "\"") + "}]},"
                + "{\"key\":\"music_and_market\",\"objectives\":[{\"type\":\"mcaquests:townstead_building_project\",\"building_type\":\"music_store\"},"
                + "{\"type\":\"mcaquests:townstead_spirit_project\","
                + "\"spirit\":\"tourism\",\"points_delta\":10" + (baseline == null ? "" : ",\"baseline\":\"" + baseline + "\"") + "}]}]}";
        return ProjectDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static ProjectState newState(ProjectDefinition def) {
        return new ProjectState(def.id(), ProjectScope.VILLAGE, "v:1", ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                BlockPos.ZERO, OptionalInt.of(1), 0L, def.phase(0).objectives().size());
    }

    private static TownsteadSpiritProjectObjective spirit(ProjectDefinition def, int phase, int index) {
        return (TownsteadSpiritProjectObjective) def.phase(phase).objectives().get(index);
    }

    private static boolean poll(ProjectDefinition def, ProjectState state, int index) {
        return spirit(def, state.currentPhase(), index).poll(null, null, def, state, state.progress(index));
    }

    @Test
    @DisplayName("SPIRIT-01: +2 earned before the first poll counts, because the baseline is taken as the phase opens")
    void baselineIsTakenAtPhaseEntryNotFirstPoll() {
        ProjectDefinition def = project("test:phase", null);
        ProjectState state = newState(def);
        ProjectPhases.begin(null, null, def, state);
        tourism = OptionalInt.of(4);
        ProjectPhases.enter(null, null, def, state, 1);
        tourism = OptionalInt.of(6); // the inn completes before the sweep ever runs
        assertTrue(poll(def, state, 1));
        assertEquals(2, state.progress(1).count());
        assertTrue(spirit(def, 1, 1).isSatisfied(state.progress(1)));
    }

    @Test
    @DisplayName("project baseline: an inn built during phase one satisfies the Tourism growth of phase two")
    void projectBaselineCountsGrowthSinceTheProjectBegan() {
        ProjectDefinition def = project("test:project", "project");
        ProjectState state = newState(def);
        tourism = OptionalInt.of(0);
        ProjectPhases.begin(null, null, def, state);
        tourism = OptionalInt.of(2); // inn finished while donations were still being collected
        ProjectPhases.enter(null, null, def, state, 1);
        poll(def, state, 1);
        assertEquals(2, state.progress(1).count());
    }

    @Test
    @DisplayName("an inn that predates the project is not a new +2: existing spirit is not growth")
    void preexistingSpiritIsNotGrowth() {
        ProjectDefinition def = project("test:project", "project");
        ProjectState state = newState(def);
        tourism = OptionalInt.of(2);
        ProjectPhases.begin(null, null, def, state);
        ProjectPhases.enter(null, null, def, state, 1);
        poll(def, state, 1);
        assertEquals(0, state.progress(1).count());
        tourism = OptionalInt.of(4); // a second inn
        poll(def, state, 1);
        assertEquals(2, state.progress(1).count());
    }

    @Test
    @DisplayName("SPIRIT-03: an unreadable reading pauses the phase; it is never read as zero")
    void unreadableReadingIsPendingNotZero() {
        ProjectDefinition def = project("test:phase", null);
        ProjectState state = newState(def);
        ProjectPhases.begin(null, null, def, state);
        tourism = OptionalInt.empty();
        ProjectPhases.enter(null, null, def, state, 1);
        SharedObjectiveProgress progress = state.progress(1);
        assertTrue(spirit(def, 1, 1).isPending(state, progress));
        assertFalse(poll(def, state, 1), "nothing moves while the source is unreadable");
        tourism = OptionalInt.of(7);
        assertTrue(spirit(def, 1, 1).resolvePending(null, null, def, state, progress));
        assertFalse(spirit(def, 1, 1).isPending(state, progress));
        assertEquals(OptionalInt.of(7), spirit(def, 1, 1).effectiveBaseline(state, progress));
        assertEquals("phase:late", spirit(def, 1, 1).baselineSource(state, progress));
    }

    @Test
    @DisplayName("SPIRIT-02: the baseline survives save and load, and credit is a high-water mark")
    void baselineSurvivesReloadAndProgressNeverFalls() {
        ProjectDefinition def = project("test:phase", null);
        ProjectState state = newState(def);
        ProjectPhases.begin(null, null, def, state);
        tourism = OptionalInt.of(10);
        ProjectPhases.enter(null, null, def, state, 1);
        tourism = OptionalInt.of(11);
        poll(def, state, 1);
        ProjectState reloaded = ProjectState.load(state.save());
        assertEquals(OptionalInt.of(10), spirit(def, 1, 1).effectiveBaseline(reloaded, reloaded.progress(1)));
        tourism = OptionalInt.of(12);
        poll(def, reloaded, 1);
        assertEquals(2, reloaded.progress(1).count());
        tourism = OptionalInt.of(9); // a building was lost
        assertFalse(poll(def, reloaded, 1));
        assertEquals(2, reloaded.progress(1).count());
    }

    @Test
    @DisplayName("SPIRIT-04: a pre-1.7.0 record keeps its phase rule and its old number, and is not given a fake start")
    void legacyRecordKeepsItsRule() {
        ProjectDefinition def = project("mcaquests:townstead_known_far_and_wide", "project");
        // Built the way an older version built it: no project-start reading, phase entered, no baseline yet.
        ProjectState state = newState(def);
        state.enterPhase(2, 2);
        tourism = OptionalInt.of(20);
        assertTrue(poll(def, state, 1), "the first reading is taken, labelled as the legacy first poll");
        SharedObjectiveProgress progress = state.progress(1);
        assertEquals(5, spirit(def, 2, 1).requiredFor(progress), "the old +5 from the migration table");
        assertEquals("legacy-phase:legacy_first_poll", spirit(def, 2, 1).baselineSource(state, progress));
        assertTrue(ProjectPhases.spiritAtStart(state, Optional.empty()).isEmpty(), "no project start is invented");
        tourism = OptionalInt.of(25);
        poll(def, state, 1);
        assertTrue(spirit(def, 2, 1).isSatisfied(progress));
    }

    @Test
    @DisplayName("an operator baseline outranks every other reading")
    void operatorBaselineWins() {
        ProjectDefinition def = project("test:phase", null);
        ProjectState state = newState(def);
        ProjectPhases.begin(null, null, def, state);
        tourism = OptionalInt.of(8);
        ProjectPhases.enter(null, null, def, state, 1);
        state.progress(1).extra().putInt(TownsteadSpiritProjectObjective.K_BASELINE_OVERRIDE, 6);
        poll(def, state, 1);
        assertEquals(2, state.progress(1).count());
        assertEquals("operator", spirit(def, 1, 1).baselineSource(state, state.progress(1)));
    }
}
