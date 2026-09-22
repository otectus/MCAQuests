package dev.otectus.mcaquests.quest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.capitals.CapitalsCapability;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.quest.IntegrationRequirements.Availability;
import dev.otectus.mcaquests.quest.IntegrationRequirements.Integration;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The optional-dependency contract (brief DEP-01, DEP-02, DEP-03, DEP-05): which definitions an
 * installation may load, derived from typed content, never from names.
 */
class IntegrationRequirementsTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final Set<TownsteadCapability> ALL_TOWNSTEAD = EnumSet.allOf(TownsteadCapability.class);
    private static final Set<CapitalsCapability> ALL_CAPITALS = EnumSet.allOf(CapitalsCapability.class);

    private static final Availability NONE = IntegrationRequirements.availabilityOf(false, Set.of(), Set.of());
    private static final Availability TOWNSTEAD_ONLY = IntegrationRequirements.availabilityOf(true, ALL_TOWNSTEAD, Set.of());
    private static final Availability CAPITALS_ONLY = IntegrationRequirements.availabilityOf(false, Set.of(), ALL_CAPITALS);
    private static final Availability BOTH = IntegrationRequirements.availabilityOf(true, ALL_TOWNSTEAD, ALL_CAPITALS);

    private static QuestDefinition quest(String fields) {
        return QuestDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"test:q\",\"giver\":{},\"dialogue\":{}," + fields + "}")).result()
                .orElseThrow(() -> new AssertionError("quest did not parse: " + fields));
    }

    private static ProjectDefinition project(String phases) {
        return ProjectDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":\"test:p\",\"scope\":\"village\",\"sponsor\":{},\"phases\":[" + phases + "]}"))
                .result().orElseThrow(() -> new AssertionError("project did not parse: " + phases));
    }

    private static final String TOWNSTEAD_OBJECTIVE =
            "{\"type\":\"mcaquests:townstead_spirit_progress\",\"points_delta\":5}";
    private static final String DONATE = "{\"key\":\"one\",\"objectives\":[{\"type\":\"mcaquests:donate_item\","
            + "\"item\":\"minecraft:stone\",\"count\":4}]}";
    private static final String TOWNSTEAD_PHASE = "{\"key\":\"two\",\"objectives\":[{\"type\":"
            + "\"mcaquests:townstead_building_project\",\"building_type\":\"inn\"}]}";
    private static final String CAPITAL_TITLE = "{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\"}";

    @Test
    @DisplayName("the installation matrix: each kind of content loads exactly where its mods are usable")
    void installationMatrix() {
        QuestDefinition core = quest("\"objectives\":[{\"type\":\"mcaquests:kill_entity\","
                + "\"entity\":\"minecraft:zombie\",\"count\":1}]");
        QuestDefinition townstead = quest("\"objectives\":[" + TOWNSTEAD_OBJECTIVE + "]");
        QuestDefinition capitals = quest("\"rewards\":[" + CAPITAL_TITLE + "]");
        QuestDefinition both = quest("\"objectives\":[" + TOWNSTEAD_OBJECTIVE + "],\"rewards\":[" + CAPITAL_TITLE + "]");

        Availability[] rows = {NONE, TOWNSTEAD_ONLY, CAPITALS_ONLY, BOTH};
        boolean[][] expected = {
                // core, townstead, capitals, both
                {true, false, false, false},
                {true, true, false, false},
                {true, false, true, false},
                {true, true, true, true}};
        QuestDefinition[] columns = {core, townstead, capitals, both};
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < columns.length; c++) {
                assertEquals(expected[r][c], IntegrationRequirements.unavailable(columns[c], rows[r]).isEmpty(),
                        "row " + r + ", column " + c);
            }
        }
        assertEquals(Integration.TOWNSTEAD,
                IntegrationRequirements.unavailable(both, CAPITALS_ONLY).orElseThrow().integration());
        assertEquals(Integration.CAPITALS,
                IntegrationRequirements.unavailable(both, TOWNSTEAD_ONLY).orElseThrow().integration());
    }

    @Test
    @DisplayName("a project whose Townstead need is only in phase two is still Townstead content")
    void laterPhaseDependencyCounts() {
        ProjectDefinition def = project(DONATE + "," + TOWNSTEAD_PHASE);
        assertTrue(IntegrationRequirements.dependsOn(def, Integration.TOWNSTEAD));
        assertTrue(IntegrationRequirements.unavailable(def, NONE).isPresent(), "must not load on a base install");
        assertTrue(IntegrationRequirements.unavailable(def, TOWNSTEAD_ONLY).isEmpty());
        assertFalse(IntegrationRequirements.dependsOn(project(DONATE), Integration.TOWNSTEAD));
    }

    @Test
    @DisplayName("a Capitals reward in a later phase makes the whole project need Capitals")
    void laterPhaseRewardCounts() {
        String rewarded = "{\"key\":\"two\",\"objectives\":[{\"type\":\"mcaquests:donate_item\",\"item\":"
                + "\"minecraft:stone\",\"count\":4}],\"rewards\":[{\"reward\":" + CAPITAL_TITLE
                + ",\"target\":\"contributors\"}]}";
        ProjectDefinition def = project(DONATE + "," + rewarded);
        assertTrue(IntegrationRequirements.dependsOn(def, Integration.CAPITALS));
        assertFalse(IntegrationRequirements.dependsOn(def, Integration.TOWNSTEAD));
    }

    @Test
    @DisplayName("a mandatory phase unlock needing Townstead makes the project need it")
    void mandatoryProjectConditions() {
        String unlocked = "{\"key\":\"two\",\"unlock\":{\"type\":\"mcaquests:townstead_available\"},"
                + "\"objectives\":[{\"type\":\"mcaquests:donate_item\",\"item\":\"minecraft:stone\",\"count\":4}]}";
        assertTrue(IntegrationRequirements.dependsOn(project(DONATE + "," + unlocked), Integration.TOWNSTEAD));
    }

    @Test
    @DisplayName("an installed Townstead missing the capability a definition reads still excludes it, and says which")
    void capabilityLevelUnavailability() {
        ProjectDefinition def = project(DONATE + "," + TOWNSTEAD_PHASE);
        Availability noBuildings = IntegrationRequirements.availabilityOf(true,
                EnumSet.complementOf(EnumSet.of(TownsteadCapability.READ_BUILDING)), Set.of());
        IntegrationRequirements.Unavailable why = IntegrationRequirements.unavailable(def, noBuildings).orElseThrow();
        assertEquals(Integration.TOWNSTEAD, why.integration());
        assertEquals(Set.of("read_building"), why.missingCapabilities());
    }

    @Test
    @DisplayName("an absent-mod gate and an optional branch are not requirements")
    void notAndAnyOfAreNotRequirements() {
        QuestDefinition absentGate = quest("\"conditions\":{\"not\":{\"type\":\"mcaquests:townstead_available\"}}");
        assertFalse(IntegrationRequirements.dependsOn(absentGate, Integration.TOWNSTEAD));

        QuestDefinition either = quest("\"conditions\":{\"any_of\":[{\"type\":\"mcaquests:townstead_available\"},"
                + "{\"type\":\"mcaquests:has_home\"}]}");
        assertFalse(IntegrationRequirements.dependsOn(either, Integration.TOWNSTEAD),
                "an any_of with a branch that needs nothing needs nothing");

        QuestDefinition gated = quest("\"conditions\":{\"all_of\":[{\"type\":\"mcaquests:townstead_available\"}]}");
        assertTrue(IntegrationRequirements.dependsOn(gated, Integration.TOWNSTEAD));
    }

    @Test
    @DisplayName("a Townstead reward on a core quest is an optional side effect, not a requirement")
    void townsteadRewardIsOptional() {
        QuestDefinition def = quest("\"rewards\":[{\"type\":\"mcaquests:townstead_profession_xp\",\"profession\":\"minecraft:farmer\",\"amount\":10}]");
        assertFalse(IntegrationRequirements.dependsOn(def, Integration.TOWNSTEAD));
    }
}
