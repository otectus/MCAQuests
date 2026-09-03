package dev.otectus.mcaquests;

import net.minecraft.core.RegistryAccess;
import dev.otectus.mcaquests.data.GraphCycles;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectInstanceKey;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for shared project storage: NBT round-trips, idempotency latch, and cycle detection. */
class ProjectStateTest {

    @Test
    void sharedObjectiveProgressRoundTrips() {
        SharedObjectiveProgress progress = new SharedObjectiveProgress();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        progress.add(5);
        progress.addContribution(a, 3);
        progress.addContribution(b, 2);
        assertTrue(progress.markTalkedTo(a));
        assertFalse(progress.markTalkedTo(a)); // dedupe

        SharedObjectiveProgress loaded = SharedObjectiveProgress.load(progress.save());
        assertEquals(5, loaded.count());
        assertEquals(3, loaded.contributionOf(a));
        assertEquals(2, loaded.contributionOf(b));
        assertTrue(loaded.hasTalkedTo(a));
    }

    @Test
    void projectStateRoundTripsAndAdvances() {
        UUID sponsor = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ProjectState state = new ProjectState(ResourceLocation.parse("mcaquests:well_repair"),
                ProjectScope.VILLAGE, "v:12", ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(10, 64, -20), OptionalInt.of(12), 1000L, 2);
        state.addSponsor(sponsor);
        state.addParticipant(player);
        state.progress(0).add(40);
        state.progress(0).addContribution(player, 40);
        assertTrue(state.tryMarkPhaseDistributed(0));
        assertFalse(state.tryMarkPhaseDistributed(0)); // one-shot guard
        state.setStatus(ProjectStatus.PAUSED);

        CompoundTag tag = state.save();
        ProjectState loaded = ProjectState.load(tag);
        assertEquals("v:12", loaded.identity());
        assertEquals(ProjectScope.VILLAGE, loaded.scope());
        assertEquals(OptionalInt.of(12), loaded.villageId());
        assertEquals(new BlockPos(10, 64, -20), loaded.anchorPos());
        assertEquals(ProjectStatus.PAUSED, loaded.status());
        assertEquals(40, loaded.progress(0).count());
        assertEquals(40, loaded.progress(0).contributionOf(player));
        assertTrue(loaded.sponsors().contains(sponsor));
        assertTrue(loaded.participants().contains(player));
        assertFalse(loaded.tryMarkPhaseDistributed(0)); // distributed flag survived the round-trip

        // enterPhase resizes shared progress to the next phase's objective count.
        loaded.enterPhase(1, 1);
        assertEquals(1, loaded.currentPhase());
        assertEquals(1, loaded.progressCount());
        assertEquals(0, loaded.progress(0).count());
    }

    @Test
    void instanceKeyRoundTrips() {
        ProjectInstanceKey key = new ProjectInstanceKey(ResourceLocation.parse("mcaquests:guardhouse_stockpile"),
                ProjectScope.PROFESSION, "p:12:minecraft:librarian");
        Optional<ProjectInstanceKey> parsed = ProjectInstanceKey.parse(key.asString());
        assertTrue(parsed.isPresent());
        assertEquals(key, parsed.get());
        assertEquals("p:12:minecraft:librarian", parsed.get().identity());
    }

    @Test
    void graphCyclesDetectsFollowUpLoops() {
        ResourceLocation a = ResourceLocation.parse("mcaquests:a");
        ResourceLocation b = ResourceLocation.parse("mcaquests:b");
        ResourceLocation c = ResourceLocation.parse("mcaquests:c");
        assertTrue(GraphCycles.findCycle(Map.of(a, List.of(b), b, List.of(c), c, List.of())).isEmpty());
        assertTrue(GraphCycles.findCycle(Map.of(a, List.of(b), b, List.of(a))).isPresent());
    }

    // --- task M3.1: pending-reward tagged union (legacy PROJECT_PHASE + banked FTB-claim kinds) ---

    @Test
    void legacyPendingRewardTagWritesNoKindKeyAndRoundTrips() {
        PendingReward legacy = PendingReward.ofPhase(ResourceLocation.parse("mcaquests:well_repair"), 1, 2);
        CompoundTag tag = legacy.save();

        // Byte-identical to the pre-1.0.0 shape: no "kind" key, exactly the three legacy fields.
        assertFalse(tag.contains("kind"));
        assertEquals("mcaquests:well_repair", tag.getString("project"));
        assertEquals(1, tag.getInt("phase"));
        assertEquals(2, tag.getInt("reward"));

        Optional<PendingReward> loaded = PendingReward.load(tag);
        assertTrue(loaded.isPresent());
        assertEquals(PendingReward.Kind.PROJECT_PHASE, loaded.get().kind());
        assertEquals(legacy.projectId(), loaded.get().projectId());
        assertEquals(legacy.phase(), loaded.get().phase());
        assertEquals(legacy.rewardIndex(), loaded.get().rewardIndex());
    }

    /** A genuinely pre-1.0.0-shaped tag (as an old jar would have written it) still loads as legacy. */
    @Test
    void preExistingLegacyShapedTagLoadsAsProjectPhase() {
        CompoundTag tag = new CompoundTag();
        tag.putString("project", "mcaquests:guardhouse_stockpile");
        tag.putInt("phase", 3);
        tag.putInt("reward", 0);

        Optional<PendingReward> loaded = PendingReward.load(tag);
        assertTrue(loaded.isPresent());
        assertEquals(PendingReward.Kind.PROJECT_PHASE, loaded.get().kind());
        assertEquals(ResourceLocation.parse("mcaquests:guardhouse_stockpile"), loaded.get().projectId());
        assertEquals(3, loaded.get().phase());
        assertEquals(0, loaded.get().rewardIndex());
    }

    @Test
    void bankedReputationRoundTrips() {
        PendingReward pending = PendingReward.ofBanked(BankedReward.reputation(-25));
        CompoundTag tag = pending.save();
        assertEquals("banked", tag.getString("kind"));

        Optional<PendingReward> loaded = PendingReward.load(tag);
        assertTrue(loaded.isPresent());
        assertEquals(PendingReward.Kind.BANKED, loaded.get().kind());
        BankedReward banked = loaded.get().banked();
        assertEquals(BankedReward.Type.REPUTATION, banked.type());
        assertEquals(-25, banked.amount());
        assertNull(banked.titleId());
    }

    @Test
    void bankedHeartsRoundTrips() {
        PendingReward pending = PendingReward.ofBanked(BankedReward.hearts(10, "SPOUSE"));
        Optional<PendingReward> loaded = PendingReward.load(pending.save());
        assertTrue(loaded.isPresent());
        BankedReward banked = loaded.get().banked();
        assertEquals(BankedReward.Type.HEARTS, banked.type());
        assertEquals(10, banked.amount());
        assertEquals("SPOUSE", banked.target());
    }

    @Test
    void bankedTitleRoundTrips() {
        ResourceLocation titleId = ResourceLocation.parse("mcaquests:hero_of_the_village");
        PendingReward pending = PendingReward.ofBanked(BankedReward.title(titleId, "VILLAGE"));
        Optional<PendingReward> loaded = PendingReward.load(pending.save());
        assertTrue(loaded.isPresent());
        BankedReward banked = loaded.get().banked();
        assertEquals(BankedReward.Type.TITLE, banked.type());
        assertEquals(titleId, banked.titleId());
        assertEquals("VILLAGE", banked.titleScope());
    }

    @Test
    void mixedPendingListRoundTripsThroughProjectSavedData() {
        ProjectSavedData data = new ProjectSavedData();
        UUID player = UUID.randomUUID();
        data.addPending(player, PendingReward.ofPhase(ResourceLocation.parse("mcaquests:well_repair"), 0, 1));
        data.addBankedReward(player, BankedReward.reputation(15));
        data.addBankedReward(player, BankedReward.hearts(5, "VILLAGE_RESIDENTS"));

        ProjectSavedData loaded = ProjectSavedData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        List<PendingReward> owed = loaded.drainPending(player);
        assertEquals(3, owed.size());
        assertEquals(PendingReward.Kind.PROJECT_PHASE, owed.get(0).kind());
        assertEquals(PendingReward.Kind.BANKED, owed.get(1).kind());
        assertEquals(BankedReward.Type.REPUTATION, owed.get(1).banked().type());
        assertEquals(PendingReward.Kind.BANKED, owed.get(2).kind());
        assertEquals(BankedReward.Type.HEARTS, owed.get(2).banked().type());

        // Drained once - a second drain for the same player is empty (single-delivery idempotence).
        assertTrue(loaded.drainPending(player).isEmpty());
    }

    /** Forward-compat guard: an unrecognised "kind" is skipped, never corrupting well-formed siblings. */
    @Test
    void unknownKindEntrySkippedWithoutCorruptingSiblings() {
        UUID player = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag pend = new CompoundTag();
        ListTag rewards = new ListTag();
        rewards.add(PendingReward.ofPhase(ResourceLocation.parse("mcaquests:well_repair"), 0, 0).save());

        CompoundTag fromTheFuture = new CompoundTag();
        fromTheFuture.putString("kind", "some_future_kind_this_build_predates");
        rewards.add(fromTheFuture);

        rewards.add(PendingReward.ofBanked(BankedReward.reputation(5)).save());
        pend.put(player.toString(), rewards);
        root.put("pending", pend);

        ProjectSavedData loaded = ProjectSavedData.load(root, RegistryAccess.EMPTY);
        List<PendingReward> owed = loaded.drainPending(player);
        assertEquals(2, owed.size()); // the unknown-kind entry is skipped; both siblings survive
        assertEquals(PendingReward.Kind.PROJECT_PHASE, owed.get(0).kind());
        assertEquals(PendingReward.Kind.BANKED, owed.get(1).kind());
    }

    /** A "banked" tag with an unrecognised {@code type} is skipped the same way (nested forward-compat). */
    @Test
    void unknownBankedTypeSkippedWithoutCorruptingSiblings() {
        UUID player = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag pend = new CompoundTag();
        ListTag rewards = new ListTag();

        CompoundTag futureBanked = new CompoundTag();
        futureBanked.putString("kind", "banked");
        CompoundTag futureBankedPayload = new CompoundTag();
        futureBankedPayload.putString("type", "SOME_FUTURE_TYPE");
        futureBanked.put("banked", futureBankedPayload);
        rewards.add(futureBanked);

        rewards.add(PendingReward.ofBanked(BankedReward.hearts(1, "NEAREST_VILLAGER")).save());
        pend.put(player.toString(), rewards);
        root.put("pending", pend);

        ProjectSavedData loaded = ProjectSavedData.load(root, RegistryAccess.EMPTY);
        List<PendingReward> owed = loaded.drainPending(player);
        assertEquals(1, owed.size());
        assertEquals(BankedReward.Type.HEARTS, owed.get(0).banked().type());
    }

    /**
     * A {@code TITLE} banked entry with no {@code titleId} key is meaningless (nothing to grant) and is
     * skipped rather than round-tripping with a null {@code titleId}, the same forward-compat skip-not-throw
     * pattern as an unrecognised {@code kind}/{@code type}.
     */
    @Test
    void bankedTitleMissingTitleIdSkippedWithoutCorruptingSiblings() {
        UUID player = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag pend = new CompoundTag();
        ListTag rewards = new ListTag();

        CompoundTag titlelessBanked = new CompoundTag();
        titlelessBanked.putString("kind", "banked");
        CompoundTag titlelessBankedPayload = new CompoundTag();
        titlelessBankedPayload.putString("type", "TITLE");
        // deliberately no "titleId" key
        titlelessBanked.put("banked", titlelessBankedPayload);
        rewards.add(titlelessBanked);

        rewards.add(PendingReward.ofBanked(BankedReward.reputation(7)).save());
        pend.put(player.toString(), rewards);
        root.put("pending", pend);

        ProjectSavedData loaded = ProjectSavedData.load(root, RegistryAccess.EMPTY);
        List<PendingReward> owed = loaded.drainPending(player);
        assertEquals(1, owed.size()); // the titleless TITLE entry is skipped; the sibling survives
        assertEquals(BankedReward.Type.REPUTATION, owed.get(0).banked().type());
    }
}
