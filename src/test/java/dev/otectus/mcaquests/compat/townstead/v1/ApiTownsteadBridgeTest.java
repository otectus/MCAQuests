package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.model.NeedLevel;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadNeedsView;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.compat.TownsteadResidentRecordView;
import dev.otectus.mcaquests.compat.TownsteadStatus;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The typed bridge against the real {@code api.v1} records: every conversion and every status
 * mapping, checked for the one property nothing downstream can afford to lose -- that a result
 * from this bridge means the same thing as the same result from the reflective one.
 */
class ApiTownsteadBridgeTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation SKILL = new ResourceLocation("townstead", "fishing_nets");

    private final FakeTownsteadApi api = new FakeTownsteadApi();
    private final ApiTownsteadBridge bridge = new ApiTownsteadBridge(api);

    /**
     * Any entity will do: the fake ignores it, and the bridge only refuses null. Allocated without
     * running a constructor, because a Forge entity's constructor reaches into registries that no
     * unit test has.
     */
    private static Entity entity() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return (Entity) unsafe.allocateInstance(Marker.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NeedResult applied(String need, int before, int after) {
        return new NeedResult(NeedResult.Status.APPLIED, need, before, after, 0, 20, "");
    }

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("generation 1 binds everything and says so")
        void full() {
            assertEquals(TownsteadStatus.FULL, bridge.status());
            assertEquals(TownsteadCapability.values().length, bridge.capabilities().size());
            assertEquals("api-v1", bridge.bindingPath());
            assertEquals(Optional.of("api-v1-r1"), bridge.variant());
            assertEquals("0.8.0+1.20.1", bridge.detectedVersion());
            assertTrue(bridge.unresolvedMembers().isEmpty());
        }

        @Test
        @DisplayName("another generation binds nothing, refuses writes, and names the reason")
        void unsupportedGeneration() {
            api.apiVersion = 2;
            ApiTownsteadBridge future = new ApiTownsteadBridge(api);
            assertEquals(TownsteadStatus.DISABLED, future.status());
            assertTrue(future.capabilities().isEmpty());
            assertTrue(future.bindingPath().startsWith("disabled:"));
            assertEquals(TownsteadMutationResult.Reason.CAPABILITY_MISSING,
                    future.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, 5)).reason());
            assertEquals(TownsteadMutationResult.Reason.CAPABILITY_MISSING,
                    future.awardProfessionXp(entity(), "minecraft:farmer", 5, true).reason());
            assertTrue(future.villager(entity()).isEmpty());
            assertNull(api.villagers.lastNeedId, "nothing reached the API");
        }
    }

    @Nested
    @DisplayName("need mutations")
    class Needs {

        @Test
        @DisplayName("a fatigue delta becomes an energy delta of the opposite sign, reported back as fatigue")
        void fatigueDelta() {
            api.villagers.nextNeedResult = applied(NeedsSnapshot.ENERGY, 15, 10);
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.FATIGUE, 5));
            assertEquals(NeedsSnapshot.ENERGY, api.villagers.lastNeedId);
            assertEquals(-5, api.villagers.lastValue);
            assertEquals(true, api.villagers.lastRelative);
            assertEquals(ApiTownsteadBridge.SOURCE, api.villagers.lastSource);
            assertEquals(TownsteadMutationResult.Reason.SUCCESS, result.reason());
            assertEquals(5, result.requested());
            assertEquals(5, result.applied());
            assertEquals(5.0D, result.before(), "fatigue 5 is energy 15");
            assertEquals(10.0D, result.after(), "fatigue 10 is energy 10");
        }

        @Test
        @DisplayName("a fatigue target is inverted, and 'requested' is the distance from where it was")
        void fatigueTarget() {
            api.villagers.nextNeedResult = applied(NeedsSnapshot.ENERGY, 10, 16);
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.target(NeedMutation.Need.FATIGUE, 4));
            assertEquals(16, api.villagers.lastValue, "fatigue 4 is energy 16");
            assertEquals(false, api.villagers.lastRelative);
            assertEquals(6, result.requested(), "from fatigue 10 to fatigue 4");
            assertEquals(6, result.applied());
            assertEquals(10.0D, result.before());
            assertEquals(4.0D, result.after());
        }

        @Test
        @DisplayName("energy speaks Townstead's own axis and is passed through untouched")
        void energyIsNotInverted() {
            api.villagers.nextNeedResult = applied(NeedsSnapshot.ENERGY, 10, 13);
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.ENERGY, 3));
            assertEquals(3, api.villagers.lastValue);
            assertEquals(10.0D, result.before());
            assertEquals(13.0D, result.after());
        }

        @Test
        @DisplayName("a clamped write reports what landed, not what was asked")
        void clampedIsPartial() {
            api.villagers.nextNeedResult = applied(NeedsSnapshot.HUNGER, 90, 100);
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, 40));
            assertEquals(40, result.requested());
            assertEquals(10, result.applied());
            assertTrue(result.capped());
        }

        @Test
        @DisplayName("a denied write is a gate, never a success")
        void policyDenial() {
            api.villagers.nextNeedResult = NeedResult.failed(NeedResult.Status.DISABLED, NeedsSnapshot.HUNGER, "denied");
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, 5));
            assertEquals(TownsteadMutationResult.Reason.FEATURE_GATED, result.reason());
            assertFalse(result.succeeded());
        }

        @Test
        @DisplayName("every other status maps to the reason the reflective bridge would give")
        void statusTable() {
            assertReason(NeedResult.Status.GATED, TownsteadMutationResult.Reason.FEATURE_GATED);
            assertReason(NeedResult.Status.UNKNOWN_NEED, TownsteadMutationResult.Reason.INVALID_VALUE);
            assertReason(NeedResult.Status.NOT_A_VILLAGER, TownsteadMutationResult.Reason.TARGET_MISSING);
            assertReason(NeedResult.Status.ERROR, TownsteadMutationResult.Reason.INTERNAL_ERROR);
            api.villagers.nextNeedResult = new NeedResult(NeedResult.Status.NO_CHANGE, NeedsSnapshot.ENERGY, 20, 20, 0, 20, "");
            TownsteadMutationResult noChange = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.FATIGUE, -5));
            assertEquals(TownsteadMutationResult.Reason.NO_CHANGE, noChange.reason());
            assertEquals(0.0D, noChange.before(), "energy 20 is fatigue 0");
            assertTrue(noChange.succeeded());
        }

        private void assertReason(NeedResult.Status status, TownsteadMutationResult.Reason expected) {
            api.villagers.nextNeedResult = NeedResult.failed(status, NeedsSnapshot.HUNGER, "");
            assertEquals(expected, bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, 1)).reason());
        }

        @Test
        @DisplayName("a malformed request or a missing target never reaches the API")
        void guards() {
            assertEquals(TownsteadMutationResult.Reason.INVALID_VALUE,
                    bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, Double.NaN)).reason());
            assertEquals(TownsteadMutationResult.Reason.TARGET_MISSING,
                    bridge.changeNeeds(null, NeedMutation.delta(NeedMutation.Need.HUNGER, 1)).reason());
            assertNull(api.villagers.lastNeedId);
        }
    }

    @Nested
    @DisplayName("profession XP")
    class Xp {

        @Test
        @DisplayName("a partial award under the daily cap is a capped success with tiers before and after")
        void partial() {
            api.professions.nextXpResult = new XpResult(XpResult.Status.APPLIED, "minecraft:farmer", 120, 40, 60, 100, 1, 2, "");
            TownsteadMutationResult result = bridge.awardProfessionXp(entity(), "minecraft:farmer", 120, true);
            assertEquals(TownsteadMutationResult.Reason.SUCCESS, result.reason());
            assertEquals(120, result.requested());
            assertEquals(40, result.applied());
            assertTrue(result.capped());
            assertEquals(60.0D, result.before());
            assertEquals(100.0D, result.after());
            assertEquals(1, result.oldTier());
            assertEquals(2, result.newTier());
            assertEquals(true, api.professions.lastRespectCap);
            assertEquals(ApiTownsteadBridge.SOURCE, api.professions.lastSource);
        }

        @Test
        @DisplayName("statuses map to the reflective bridge's reasons; denial is a gate")
        void statusTable() {
            assertXpReason(XpResult.Status.DAILY_CAP, TownsteadMutationResult.Reason.DAILY_CAP);
            assertXpReason(XpResult.Status.AT_MAX, TownsteadMutationResult.Reason.INVALID_VALUE);
            assertXpReason(XpResult.Status.NO_PROGRESSION, TownsteadMutationResult.Reason.INVALID_VALUE);
            assertXpReason(XpResult.Status.INVALID, TownsteadMutationResult.Reason.INVALID_VALUE);
            assertXpReason(XpResult.Status.NOT_A_VILLAGER, TownsteadMutationResult.Reason.TARGET_MISSING);
            assertXpReason(XpResult.Status.DISABLED, TownsteadMutationResult.Reason.FEATURE_GATED);
            assertXpReason(XpResult.Status.ERROR, TownsteadMutationResult.Reason.INTERNAL_ERROR);
        }

        private void assertXpReason(XpResult.Status status, TownsteadMutationResult.Reason expected) {
            api.professions.nextXpResult = XpResult.failed(status, "minecraft:farmer", 10, "");
            TownsteadMutationResult result = bridge.awardProfessionXp(entity(), "minecraft:farmer", 10, true);
            assertEquals(expected, result.reason());
            assertFalse(result.succeeded());
        }

        @Test
        @DisplayName("a non-positive request is refused before the API is asked")
        void guards() {
            assertEquals(TownsteadMutationResult.Reason.INVALID_VALUE,
                    bridge.awardProfessionXp(entity(), "minecraft:farmer", 0, true).reason());
            assertEquals(TownsteadMutationResult.Reason.INVALID_VALUE,
                    bridge.awardProfessionXp(entity(), " ", 5, true).reason());
            assertNull(api.professions.lastXpRequested);
        }
    }

    @Nested
    @DisplayName("skills")
    class Skills {

        @Test
        @DisplayName("learning is idempotent, and prerequisites, retraining locks and denial are gates")
        void table() {
            api.professions.nextSkillResult = new SkillResult(SkillResult.Status.ALREADY, SKILL, Set.of(), "");
            TownsteadMutationResult already = bridge.learnSkill(entity(), SKILL, false);
            assertEquals(TownsteadMutationResult.Reason.NO_CHANGE, already.reason());
            assertTrue(already.succeeded());

            api.professions.nextSkillResult = new SkillResult(SkillResult.Status.APPLIED, SKILL, Set.of(), "");
            TownsteadMutationResult learned = bridge.learnSkill(entity(), SKILL, true);
            assertEquals(TownsteadMutationResult.Reason.SUCCESS, learned.reason());
            assertEquals(true, api.professions.lastForce);

            for (SkillResult.Status gate : List.of(SkillResult.Status.PREREQUISITES_UNMET, SkillResult.Status.LOCKED,
                    SkillResult.Status.DISABLED)) {
                api.professions.nextSkillResult = SkillResult.failed(gate, SKILL, "");
                assertEquals(TownsteadMutationResult.Reason.FEATURE_GATED, bridge.forgetSkill(entity(), SKILL).reason(), gate.name());
            }
            assertEquals(false, api.professions.lastForce, "forgetting never forces past a retraining lock");
            api.professions.nextSkillResult = SkillResult.failed(SkillResult.Status.UNKNOWN_SKILL, SKILL, "");
            assertEquals(TownsteadMutationResult.Reason.INVALID_VALUE, bridge.learnSkill(entity(), SKILL, false).reason());
        }
    }

    @Nested
    @DisplayName("profession tracks")
    class Tracks {

        @Test
        @DisplayName("no track means non-progressive, under the id the caller asked with")
        void none() {
            TownsteadProfessionTrackView view = bridge.professionTrack("minecraft:fisherman");
            assertFalse(view.progressive());
            assertEquals("minecraft:fisherman", view.professionId());
        }

        @Test
        @DisplayName("a track keeps one threshold per tier and Townstead's own ceiling and cap")
        void present() {
            api.professions.track = new ProgressionTrackSnapshot("minecraft:farmer", List.of(0, 100, 300), 3, 600, 50);
            TownsteadProfessionTrackView view = bridge.professionTrack("farmer");
            assertTrue(view.progressive());
            assertEquals("farmer", view.professionId());
            assertEquals(3, view.maxTier());
            assertEquals(600, view.maxXp());
            assertEquals(50, view.dailyCap());
            assertEquals(100, view.thresholdFor(2).orElseThrow());
            assertTrue(view.supportsTier(3));
            assertFalse(view.supportsTier(4));
            assertTrue(view.dataDriven());
        }
    }

    @Nested
    @DisplayName("API drift")
    class Drift {

        @org.junit.jupiter.api.AfterEach
        void tearDown() {
            ApiTownsteadBridge.resetDriftForTest();
        }

        @Test
        @DisplayName("a moved signature disables that call, names it once, and never escapes the bridge")
        void linkageErrorIsContained() {
            api.villagers.drift = new NoSuchMethodError("com.aetherianartificer.townstead.api.v1.VillagersApi.snapshot");
            assertTrue(bridge.villager(entity()).isEmpty());
            TownsteadMutationResult result = bridge.changeNeeds(entity(), NeedMutation.delta(NeedMutation.Need.HUNGER, 5));
            assertEquals(TownsteadMutationResult.Reason.INTERNAL_ERROR, result.reason());
            assertFalse(result.succeeded());
            assertEquals(TownsteadStatus.FULL, bridge.status(), "one drifted member does not unbind the rest");
            assertEquals(List.of("villagers.adjustNeed", "villagers.snapshot"),
                    bridge.unresolvedMembers().stream().sorted().toList());
            // The unaffected facade still answers.
            api.professions.track = new ProgressionTrackSnapshot("minecraft:farmer", List.of(0, 100), 2, 200, 50);
            assertTrue(bridge.professionTrack("minecraft:farmer").progressive());
        }
    }

    @Nested
    @DisplayName("readings")
    class Readings {

        @Test
        @DisplayName("energy on Townstead's rising scale becomes fatigue on this mod's falling one")
        void needsInversion() {
            NeedsSnapshot snapshot = new NeedsSnapshot(80, 5f, 0.5f, 12, 3, 0.1f, 14, false, true, 370, 200, Map.of());
            TownsteadNeedsView view = ApiTownsteadBridge.needsView(snapshot);
            assertEquals(80, view.hunger());
            assertEquals(6, view.fatigue(), "energy 14 is fatigue 6");
            assertEquals(14, view.energy());
            assertTrue(view.thirstActive());
            NeedsSnapshot gated = new NeedsSnapshot(80, 5f, 0.5f, 0, 0, 0f, 20, true, false, 370, 200, Map.of());
            TownsteadNeedsView gatedView = ApiTownsteadBridge.needsView(gated);
            assertTrue(gatedView.gated());
            assertTrue(gatedView.collapsed());
            assertEquals(0, gatedView.fatigue());
        }

        @Test
        @DisplayName("a resident record keeps its age, membership and the needs the register stores")
        void record() {
            UUID uuid = UUID.randomUUID();
            VillageId village = new VillageId(new ResourceLocation("minecraft", "the_nether"), 7);
            Map<String, NeedLevel> levels = Map.of(
                    NeedsSnapshot.HUNGER, new NeedLevel(NeedsSnapshot.HUNGER, 65, 0, 100, 80, "fed", false, true),
                    NeedsSnapshot.THIRST, new NeedLevel(NeedsSnapshot.THIRST, 9, 0, 20, 16, "thirsty", false, false),
                    NeedsSnapshot.ENERGY, new NeedLevel(NeedsSnapshot.ENERGY, 4, 0, 20, 17, "exhausted", true, true));
            VillagerRecord record = new VillagerRecord(uuid, "Bo", Optional.of(village), "minecraft:farmer", 2, levels,
                    true, false, 12345L, 41L, true);
            TownsteadResidentRecordView view = ApiTownsteadBridge.recordView(record);
            assertEquals(uuid, view.uuid());
            assertTrue(view.belongsTo(new ResourceLocation("minecraft", "the_nether"), 7));
            assertFalse(view.belongsTo(new ResourceLocation("minecraft", "overworld"), 7));
            assertEquals(41L, view.lastSeenWorldDay());
            assertFalse(view.loaded());
            assertTrue(view.alive());
            assertEquals(65, view.needs().hunger());
            assertEquals(9, view.needs().thirst());
            assertTrue(view.needs().gated(), "thirst not simulated reads as gated");
            assertEquals(16, view.needs().fatigue(), "energy 4 is fatigue 16");
            assertTrue(view.needs().collapsed());
            api.villagers.record = record;
            assertTrue(bridge.lastKnownResident(null, uuid).isEmpty(), "a null server is refused before the API is asked");
        }

        @Test
        @DisplayName("a record with no levels reads as unknown needs, not as healthy ones")
        void emptyRecord() {
            VillagerRecord record = new VillagerRecord(UUID.randomUUID(), "", Optional.empty(), "", 0, Map.of(),
                    false, false, 0L, 0L, true);
            TownsteadResidentRecordView view = ApiTownsteadBridge.recordView(record);
            assertEquals(0, view.needs().hunger());
            assertTrue(view.village().isEmpty());
            assertFalse(view.belongsTo(new ResourceLocation("minecraft", "overworld"), 0));
        }
    }
}
