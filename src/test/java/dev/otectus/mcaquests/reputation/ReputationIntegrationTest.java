package dev.otectus.mcaquests.reputation;

import dev.otectus.mcaquests.quest.reputation.ReputationDedupe;
import dev.otectus.mcaquests.quest.reputation.ReputationOutcome;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.state.VillageStanding;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MCA: Reputation companion-release regression suite (spec §36.3).
 *
 * <p>Everything here is pure: the per-player standing store, the outcome codecs, the dedupe key
 * shapes, and the source-level assertions that keep the optional-classloading seam honest. The paths
 * that need a live server are covered by the production matrix in §37.
 */
class ReputationIntegrationTest {

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final ResourceLocation LADDER = new ResourceLocation("mcaquests", "default");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    // ------------------------------------------------------------------
    // Per-player, dimension-aware fallback store (§29.2)
    // ------------------------------------------------------------------

    /** The defect this whole release exists to fix: one player's work moved everybody's standing. */
    @Test
    void playersNoLongerShareOneVillageScore() {
        VillageStanding standing = new VillageStanding();
        standing.addScore(ALICE, OVERWORLD, 3, 40);
        assertEquals(40, standing.score(ALICE, OVERWORLD, 3));
        assertEquals(0, standing.score(BOB, OVERWORLD, 3), "Bob did none of that work");
    }

    @Test
    void villageIdsNoLongerCollideAcrossDimensions() {
        VillageStanding standing = new VillageStanding();
        standing.addScore(ALICE, OVERWORLD, 3, 40);
        standing.addScore(ALICE, NETHER, 3, -15);
        assertEquals(40, standing.score(ALICE, OVERWORLD, 3));
        assertEquals(-15, standing.score(ALICE, NETHER, 3));
    }

    @Test
    void standingSurvivesSaveAndLoad() {
        VillageStanding standing = new VillageStanding();
        standing.addScore(ALICE, OVERWORLD, 3, 40);
        standing.setTierHighWater(ALICE, LADDER, OVERWORLD, 3, "acquaintance");
        standing.grantVillageTitle(ALICE, OVERWORLD, 3, new ResourceLocation("mcaquests:honored_of_village"));
        standing.markMigrated(ALICE, "mcaquests:legacy_reputation_v1", "1");

        CompoundTag tag = standing.save();
        VillageStanding loaded = VillageStanding.load(tag);

        assertEquals(40, loaded.score(ALICE, OVERWORLD, 3));
        assertEquals("acquaintance", loaded.tierHighWater(ALICE, LADDER, OVERWORLD, 3).orElseThrow());
        assertTrue(loaded.hasVillageTitle(ALICE, OVERWORLD, 3,
                new ResourceLocation("mcaquests:honored_of_village")));
        assertTrue(loaded.hasMigrated(ALICE, "mcaquests:legacy_reputation_v1"));
    }

    @Test
    void villageScoresAreScopedToOneDimension() {
        VillageStanding standing = new VillageStanding();
        standing.addScore(ALICE, OVERWORLD, 3, 40);
        standing.addScore(ALICE, OVERWORLD, 7, 10);
        standing.addScore(ALICE, NETHER, 3, -15);

        Map<Integer, Integer> overworld = standing.villageScores(ALICE, OVERWORLD);
        assertEquals(2, overworld.size());
        assertEquals(40, overworld.get(3));
        assertEquals(10, overworld.get(7));
        assertEquals(1, standing.villageScores(ALICE, NETHER).size());
    }

    @Test
    void loadingAnEmptyOrAbsentTagIsSafe() {
        assertTrue(VillageStanding.load(null).isEmpty());
        assertTrue(VillageStanding.load(new CompoundTag()).isEmpty());
    }

    // ------------------------------------------------------------------
    // Legacy migration (§32.2)
    // ------------------------------------------------------------------

    @Test
    void legacyKeysAreParsedAndOtherScopesIgnored() {
        assertEquals(3, VillageStanding.parseLegacyVillageId("v:3").orElseThrow());
        assertTrue(VillageStanding.parseLegacyVillageId("f:someFamily").isEmpty());
        assertTrue(VillageStanding.parseLegacyVillageId("v:notanumber").isEmpty());
        assertTrue(VillageStanding.parseLegacyVillageId(null).isEmpty());
    }

    @Test
    void legacyImportCopiesScoresAndHighWaterIntoTheOverworld() {
        VillageStanding standing = new VillageStanding();
        int imported = standing.importLegacy(ALICE,
                Map.of("v:3", 120, "v:7", -20, "f:family", 99),
                Map.of("v:3", "friend"),
                OVERWORLD, LADDER);

        assertEquals(2, imported, "only village identities are standing");
        assertEquals(120, standing.score(ALICE, OVERWORLD, 3));
        assertEquals(-20, standing.score(ALICE, OVERWORLD, 7));
        assertEquals("friend", standing.tierHighWater(ALICE, LADDER, OVERWORLD, 3).orElseThrow());
    }

    // ------------------------------------------------------------------
    // Outcome codecs (§29.3–§29.5)
    // ------------------------------------------------------------------

    /** Every pre-1.1.0 datapack used the bare integer; it must keep meaning exactly what it did. */
    @Test
    void theLegacyIntegerShorthandStillParses() {
        ReputationOutcome outcome = ReputationOutcome.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("10")).result().orElseThrow();
        assertEquals(10, outcome.delta());
        assertEquals(ReputationOutcome.Recipients.NOBODY, outcome.recipients(),
                "the shorthand states no recipients; each call site supplies its own default");
    }

    @Test
    void theObjectFormParsesEveryField() {
        ReputationOutcome outcome = ReputationOutcome.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"delta": 3, "incident": "mcareputation:project_phase_completed",
                 "visibility": "village", "tags": ["Service"], "recipients": "phase_contributors"}"""))
                .result().orElseThrow();
        assertEquals(3, outcome.delta());
        assertEquals("mcareputation:project_phase_completed", outcome.incident().orElseThrow().toString());
        assertEquals("village", outcome.visibility().orElseThrow());
        assertEquals(List.of("service"), outcome.tags(), "tags are normalised to lower case");
        assertEquals(ReputationOutcome.Recipients.PHASE_CONTRIBUTORS, outcome.recipients());
    }

    @Test
    void anUnknownRecipientSetIsAnError() {
        assertTrue(ReputationOutcome.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"delta": 3, "recipients": "everyone_within_shouting_distance"}"""))
                .error().isPresent());
    }

    @Test
    void defaultsAreFilledInOnlyWhereUnstated() {
        ReputationOutcome bare = ReputationOutcome.ofShorthand(5);
        ReputationOutcome defaulted = bare
                .withDefaultRecipients(ReputationOutcome.Recipients.ALL_PARTICIPANTS)
                .withDefaultIncident(QuestReputationBlock.Incidents.PROJECT_COMPLETED);
        assertEquals(ReputationOutcome.Recipients.ALL_PARTICIPANTS, defaulted.recipients());
        assertEquals(QuestReputationBlock.Incidents.PROJECT_COMPLETED, defaulted.incident().orElseThrow());

        ReputationOutcome explicit = new ReputationOutcome(5,
                java.util.Optional.of(new ResourceLocation("example:custom")), java.util.Optional.empty(),
                List.of(), ReputationOutcome.Recipients.RESOLVING_PLAYER);
        assertEquals(ReputationOutcome.Recipients.RESOLVING_PLAYER,
                explicit.withDefaultRecipients(ReputationOutcome.Recipients.ALL_PARTICIPANTS).recipients(),
                "an authored recipient set is never overridden");
        assertEquals("example:custom",
                explicit.withDefaultIncident(QuestReputationBlock.Incidents.PROJECT_COMPLETED)
                        .incident().orElseThrow().toString());
    }

    @Test
    void aZeroOutcomeDoesNothing() {
        assertTrue(ReputationOutcome.NONE.isNoOp());
        assertTrue(ReputationOutcome.ofShorthand(0).isNoOp());
        assertFalse(ReputationOutcome.ofShorthand(-1).isNoOp());
    }

    /** §29.3, §33 rule 6: failure and abandonment cost nothing unless the pack says otherwise. */
    @Test
    void questFailureAndAbandonAreOptInOnly() {
        QuestReputationBlock block = QuestReputationBlock.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {"complete": {"delta": 12}}"""))
                .result().orElseThrow();
        assertTrue(block.completeOutcome().isPresent());
        assertTrue(block.failOutcome().isEmpty());
        assertTrue(block.abandonOutcome().isEmpty());
        assertEquals(QuestReputationBlock.Incidents.QUEST_COMPLETED,
                block.completeOutcome().orElseThrow().incident().orElseThrow());
    }

    @Test
    void anAbsentReputationBlockIsEmptyNotNull() {
        assertTrue(QuestReputationBlock.NONE.isEmpty());
        assertTrue(QuestReputationBlock.NONE.completeOutcome().isEmpty());
    }

    // ------------------------------------------------------------------
    // Dedupe key shapes (§14.2)
    // ------------------------------------------------------------------

    @Test
    void theSameOutcomeProducesTheSameKey() {
        UUID giver = UUID.randomUUID();
        ResourceLocation quest = new ResourceLocation("example:make_amends");
        assertEquals(ReputationDedupe.quest(quest, giver, 100L, "complete"),
                ReputationDedupe.quest(quest, giver, 100L, "complete"));
    }

    @Test
    void differentOutcomesNeverCollide() {
        UUID giverA = UUID.randomUUID();
        UUID giverB = UUID.randomUUID();
        ResourceLocation quest = new ResourceLocation("example:make_amends");

        List<String> keys = List.of(
                ReputationDedupe.quest(quest, giverA, 100L, "complete"),
                ReputationDedupe.quest(quest, giverA, 100L, "fail"),
                ReputationDedupe.quest(quest, giverA, 200L, "complete"),
                ReputationDedupe.quest(quest, giverB, 100L, "complete"),
                ReputationDedupe.quest(new ResourceLocation("example:other"), giverA, 100L, "complete"));
        assertEquals(keys.size(), Set.copyOf(keys).size(), "every one of these is a different outcome");
    }

    @Test
    void perRecipientKeysAreDistinct() {
        ResourceLocation project = new ResourceLocation("example:barn");
        assertNotEquals(ReputationDedupe.projectPhase(project, "v:3", 0, ALICE),
                ReputationDedupe.projectPhase(project, "v:3", 0, BOB));
        assertNotEquals(ReputationDedupe.projectPhase(project, "v:3", 0, ALICE),
                ReputationDedupe.projectPhase(project, "v:3", 1, ALICE));
        assertNotEquals(ReputationDedupe.projectOutcome(project, "v:3", "complete", ALICE),
                ReputationDedupe.projectOutcome(project, "v:3", "fail", ALICE));
    }

    // ------------------------------------------------------------------
    // Source-level assertions (§29.1, §36.3)
    // ------------------------------------------------------------------

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/dev/otectus/mcaquests");

    /**
     * §29.1: no gameplay path may read or write the legacy shared reputation map. Only the store
     * itself, the deprecated compatibility shim, and the migration provider may touch it.
     */
    @Test
    void noGameplayCodeTouchesTheLegacySharedReputationMap() throws IOException {
        List<String> allowed = List.of(
                "project/state/ProjectSavedData.java",   // the store, which still persists the v1 tags
                "quest/reputation/ReputationService.java", // the deprecated shim (parse helper only)
                "compat/reputation/QuestsLegacyImportProvider.java"); // reads v1 exactly once per player

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (allowed.contains(relative)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains(".reputation(\"v:") || source.contains(".addReputation(")
                        || source.contains(".reputationKeys()")) {
                    offenders.add(relative);
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "direct legacy reputation access outside the backend and "
                + "migration:\n  " + String.join("\n  ", offenders));
    }

    /**
     * §29.1: the always-loaded bridge must not name a {@code mcareputation} type, or Quests would fail
     * to start without that mod installed. Only the guarded {@code compat.reputation} package may.
     */
    @Test
    void onlyTheGuardedPackageNamesReputationTypes() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("compat/reputation/")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("import dev.otectus.mcareputation.")) {
                    offenders.add(relative);
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "MCA: Reputation imports outside compat/reputation:\n  "
                + String.join("\n  ", offenders));
    }

    /** The bridge must reach its implementation by name, never by a compile-time reference. */
    @Test
    void theBridgeResolvesItsBackendReflectively() throws IOException {
        String bridge = Files.readString(SOURCE_ROOT.resolve("compat/ReputationBridge.java"),
                StandardCharsets.UTF_8);
        assertTrue(bridge.contains("ModList.get().isLoaded(\"mcareputation\")"),
                "the mod-present check is what makes the whole seam work");
        assertTrue(bridge.contains("Class.forName("),
                "a direct reference would put the guarded class in this class's constant pool");
        assertTrue(bridge.contains("catch (Throwable"),
                "binary drift must disable the integration with one ERROR, never crash the game");
    }

    @Test
    void modsTomlDeclaresReputationAsOptional() throws IOException {
        String toml = Files.readString(Paths.get("src/main/resources/META-INF/mods.toml"),
                StandardCharsets.UTF_8);
        int index = toml.indexOf("modId=\"mcareputation\"");
        assertTrue(index > 0, "the optional dependency entry is missing");
        String block = toml.substring(index, Math.min(toml.length(), index + 200));
        assertTrue(block.contains("mandatory=false"), "MCA: Reputation must never become mandatory");
        assertTrue(block.contains("ordering=\"AFTER\""),
                "Quests must load after Reputation so the bridge sees a ready API");
    }

    private static final class Set {
        static java.util.Set<String> copyOf(List<String> values) {
            return java.util.Set.copyOf(values);
        }
    }
}
