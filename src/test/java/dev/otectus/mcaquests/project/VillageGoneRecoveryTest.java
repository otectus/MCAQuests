package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An instance whose MCA village was deleted or merged can be moved by an operator, carrying everything it
 * owns (1.7.0; content audit F-D09).
 */
class VillageGoneRecoveryTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation WALLS = ResourceLocation.fromNamespaceAndPath("mcaquests", "walls_before_winter");

    private static ProjectSavedData withOwedReward(ProjectState state, UUID player) {
        CompoundTag tag = new CompoundTag();
        ListTag instances = new ListTag();
        instances.add(state.save());
        tag.put("instances", instances);
        CompoundTag pending = new CompoundTag();
        ListTag rewards = new ListTag();
        rewards.add(PendingReward.ofPhase(state, 0, 0).save());
        pending.put(player.toString(), rewards);
        tag.put("pending", pending);
        return ProjectSavedData.load(tag, RegistryAccess.EMPTY);
    }

    @Test
    @DisplayName("rebinding to the anchor keeps progress and moves the owed reward with the instance")
    void rebindToAnchorCarriesEverything() {
        ProjectState state = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:7", OVERWORLD, new BlockPos(5, 64, 5),
                OptionalInt.of(7), 0L, 1);
        state.progress(0).setCount(40);
        UUID player = UUID.randomUUID();
        ProjectSavedData data = withOwedReward(state, player);
        ProjectState stored = data.allInstances().iterator().next();

        ProjectState moved = data.rebind(stored, "anchor:rebound:v:7", OptionalInt.empty(), stored.anchorPos())
                .orElseThrow();
        assertEquals(1, data.allInstances().size(), "moved, not copied");
        assertTrue(moved.villageId().isEmpty(), "anchor-bound after the move");
        assertEquals(40, moved.progress(0).count());
        assertTrue(moved.revision() > stored.revision(), "a stale repair token for the old instance is refused");
        PendingReward owed = data.pendingOf(player).get(0);
        assertEquals(moved.key().asString(), owed.instanceKey());
    }

    @Test
    @DisplayName("a rebind onto a key another instance holds changes nothing")
    void rebindNeverMerges() {
        ProjectState gone = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:7", OVERWORLD, BlockPos.ZERO,
                OptionalInt.of(7), 0L, 1);
        ProjectState neighbour = new ProjectState(WALLS, ProjectScope.VILLAGE, "v:8", OVERWORLD, BlockPos.ZERO,
                OptionalInt.of(8), 0L, 1);
        CompoundTag tag = new CompoundTag();
        ListTag instances = new ListTag();
        instances.add(gone.save());
        instances.add(neighbour.save());
        tag.put("instances", instances);
        ProjectSavedData data = ProjectSavedData.load(tag, RegistryAccess.EMPTY);
        ProjectState stored = data.getInstance(gone.key().asString()).orElseThrow();

        assertTrue(data.rebind(stored, "v:8", OptionalInt.of(8), BlockPos.ZERO).isEmpty());
        assertEquals(2, data.allInstances().size());
        assertTrue(data.getInstance(gone.key().asString()).isPresent(), "the original is untouched");
    }

    @Test
    @DisplayName("a profession identity yields its profession, dimension or not; others yield nothing")
    void professionIdentityParses() {
        assertEquals(Optional.of("minecraft:librarian"), ProjectRecovery.professionOf("p:3:minecraft:librarian"));
        assertEquals(Optional.of("minecraft:librarian"),
                ProjectRecovery.professionOf("p:3@minecraft:the_nether:minecraft:librarian"));
        assertTrue(ProjectRecovery.professionOf("v:3").isEmpty());
        assertTrue(ProjectRecovery.professionOf("anchor:abc").isEmpty());
    }
}
