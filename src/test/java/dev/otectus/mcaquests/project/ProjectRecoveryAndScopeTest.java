package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import dev.otectus.mcaquests.project.scope.ScopeResolver;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
import dev.otectus.mcaquests.support.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Build-area geometry (brief C03a, BUILD-04), dimension-safe instance keys (BUILD-05), deferred rewards
 * for unavailable content (DEP-07/08) and the recovery token rules (REPAIR-01..03).
 */
class ProjectRecoveryAndScopeTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation NETHER = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
    private static final ResourceLocation WALLS = ResourceLocation.fromNamespaceAndPath("mcaquests", "walls_before_winter");

    @AfterEach
    void reset() {
        UnavailableContent.clearForTest();
        ProjectRecovery.clearSessionState();
    }

    @Test
    @DisplayName("BUILD-04: a village box with a 32-block margin admits a perimeter wall the bare box refuses")
    void marginWidensTheVillageBox() {
        BoundingBox buildings = new BoundingBox(0, 60, 0, 40, 70, 40);
        ScopeGeometry bare = ScopeGeometry.villageBox(OVERWORLD, new BlockPos(20, 64, 20), buildings, 0);
        ScopeGeometry walled = ScopeGeometry.villageBox(OVERWORLD, new BlockPos(20, 64, 20), buildings, 32);
        BlockPos perimeter = new BlockPos(60, 64, 20);
        assertFalse(bare.contains(OVERWORLD, perimeter));
        assertEquals(20, bare.blocksOutside(perimeter));
        assertTrue(walled.contains(OVERWORLD, perimeter));
        assertFalse(walled.contains(NETHER, perimeter), "another dimension is never inside");
        assertFalse(walled.contains(OVERWORLD, new BlockPos(80, 64, 20)));
    }

    @Test
    @DisplayName("geometry survives the network round trip exactly")
    void geometryRoundTrips() {
        ScopeGeometry original = ScopeGeometry.villageBox(OVERWORLD, new BlockPos(1, 2, 3),
                new BoundingBox(-5, 60, -5, 5, 70, 5), 32);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);
        assertEquals(original, ScopeGeometry.decode(buf));
        ScopeGeometry anchor = ScopeGeometry.anchorRadius(NETHER, new BlockPos(7, 8, 9), 48);
        FriendlyByteBuf second = new FriendlyByteBuf(Unpooled.buffer());
        anchor.encode(second);
        assertEquals(anchor, ScopeGeometry.decode(second));
    }

    @Test
    @DisplayName("BUILD-05: village ids are per dimension, so a Nether village never shares an Overworld key")
    void identitiesAreDimensionQualified() {
        assertEquals("v:3", ScopeResolver.villageIdentity(3, OVERWORLD), "Overworld keys keep their spelling");
        assertEquals("v:3@minecraft:the_nether", ScopeResolver.villageIdentity(3, NETHER));
        assertEquals("p:3@minecraft:the_nether:minecraft:farmer",
                ScopeResolver.professionIdentity(3, NETHER, "minecraft:farmer"));
        assertEquals("v:3@minecraft:the_nether", ScopeResolver.dimensionQualified("v:3", NETHER));
        assertEquals("v:3", ScopeResolver.dimensionQualified("v:3", OVERWORLD));
        assertEquals("anchor:x", ScopeResolver.dimensionQualified("anchor:x", NETHER));
    }

    @Test
    @DisplayName("BUILD-05: loading a pre-1.7.0 Nether instance re-keys it and the rewards it owes, without merging")
    void legacyNetherInstanceIsRekeyedWithItsRewards() {
        ProjectState nether = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:3", NETHER, BlockPos.ZERO,
                OptionalInt.of(3), 0L, 1);
        nether.progress(0).setCount(9);
        // A real save holds at most one instance per key, so the Overworld neighbour is a different village.
        ProjectState overworld = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:4", OVERWORLD, BlockPos.ZERO,
                OptionalInt.of(4), 0L, 1);
        UUID player = UUID.randomUUID();
        PendingReward owed = PendingReward.ofPhase(nether, 0, 0);

        CompoundTag tag = new CompoundTag();
        ListTag instances = new ListTag();
        instances.add(nether.save());
        instances.add(overworld.save());
        tag.put("instances", instances);
        CompoundTag pending = new CompoundTag();
        ListTag rewards = new ListTag();
        rewards.add(owed.save());
        pending.put(player.toString(), rewards);
        tag.put("pending", pending);

        ProjectSavedData data = ProjectSavedData.load(tag, net.minecraft.core.RegistryAccess.EMPTY);
        assertEquals(2, data.allInstances().size(), "nothing merged or lost");
        assertTrue(data.getInstance(overworld.key().asString()).isPresent(), "the Overworld key is untouched");
        ProjectState moved = data.allInstances().stream()
                .filter(state -> state.anchorDimension().equals(NETHER)).findFirst().orElseThrow();
        assertEquals("v:3@minecraft:the_nether", moved.identity());
        assertEquals(9, moved.progress(0).count(), "progress carried across");
        PendingReward rekeyed = data.pendingOf(player).get(0);
        assertTrue(rekeyed.matchesInstance(moved, player), "the owed reward follows its instance");
        assertEquals(moved.key().asString(), rekeyed.instanceKey());
    }

    @Test
    @DisplayName("new instance fields persist: frozen radius, revision, project reading and deferred follow-ups")
    void newStateFieldsPersist() {
        ProjectState state = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:1", OVERWORLD, BlockPos.ZERO,
                OptionalInt.of(1), 0L, 1);
        assertTrue(state.freezeAnchorRadius(96));
        assertFalse(state.freezeAnchorRadius(64), "frozen once");
        state.bumpRevision();
        state.extra().putBoolean(ProjectPhases.K_SPIRIT_START_PENDING, true);
        state.deferredFollowUps().add(ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_known_far_and_wide"));
        ProjectState loaded = ProjectState.load(state.save());
        assertEquals(OptionalInt.of(96), loaded.anchorRadius());
        assertEquals(state.revision(), loaded.revision());
        assertTrue(ProjectPhases.spiritAtStartPending(loaded));
        assertEquals(state.deferredFollowUps(), loaded.deferredFollowUps());
    }

    @Test
    @DisplayName("DEP-07: a reward owed by a project whose mod is missing waits; it is not failed toward being held")
    void unavailableProjectRewardWaits() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_known_far_and_wide");
        PendingReward owed = PendingReward.ofPhase(id, 1, 0);
        assertFalse(ProjectManager.waitsForOptionalMod(owed));
        UnavailableContent.replace(UnavailableContent.Kind.PROJECT, Map.of(id, new UnavailableContent.Entry(
                UnavailableContent.Kind.PROJECT, id,
                new IntegrationRequirements.Unavailable(IntegrationRequirements.Integration.TOWNSTEAD, Set.of()),
                "test", Component.literal("Known Far and Wide"))));
        assertTrue(ProjectManager.waitsForOptionalMod(owed));

        List<PendingReward> kept = ProjectManager.drainPass(UUID.randomUUID(), List.of(owed), true,
                reward -> ProjectManager.waitsForOptionalMod(reward)
                        ? ProjectRewardDistributor.DeliveryOutcome.DEFERRED
                        : ProjectRewardDistributor.DeliveryOutcome.DELIVERED);
        assertEquals(1, kept.size());
        assertEquals(0, kept.get(0).attempts(), "waiting is not a failed attempt");
        assertFalse(ProjectManager.isHeld(kept.get(0)));
    }

    @Test
    @DisplayName("REPAIR-03: a token is single-use, belongs to one operator, expires, and refuses a changed instance")
    void tokenRules() {
        ProjectRecovery.Pending pending = new ProjectRecovery.Pending("Alex", WALLS + "|village|v:1", 4L,
                ProjectRecovery.Operation.SKIP_NO_REWARDS, -1, 0, 1_000L);
        ProjectRecovery.putForTest("abc234", pending);
        assertTrue(ProjectRecovery.refusal(null, "zzz", "Alex", 0L).isPresent(), "unknown token");
        assertTrue(ProjectRecovery.refusal(pending, "abc234", "Sam", 10L).isPresent(), "another operator");
        assertTrue(ProjectRecovery.refusal(pending, "abc234", "Alex", 2_000L).isPresent(), "expired");
        assertTrue(ProjectRecovery.refusal(pending, "abc234", "Alex", 10L).isEmpty());
        assertTrue(ProjectRecovery.stale(pending, 5L), "the instance moved on since the preview");
        assertFalse(ProjectRecovery.stale(pending, 4L));
        assertEquals(pending, ProjectRecovery.take("abc234"));
        assertEquals(null, ProjectRecovery.take("abc234"), "a second confirmation finds nothing");
    }
}
