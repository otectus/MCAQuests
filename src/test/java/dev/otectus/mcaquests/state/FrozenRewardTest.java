package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the promise that a randomized currency reward is rolled <b>once</b> and never rerolled.
 *
 * <p>Without this, a player could reopen the quest menu until the payout looked good, and a phase reward
 * collected offline could differ from what everyone online was paid. The invariant is enforced by
 * first-writer-wins storage plus persistence, both of which are checked here.
 */
class FrozenRewardTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static ActiveQuest quest() {
        return ActiveQuest.create(
                new ResourceLocation("mcaquests", "test_quest"),
                UUID.randomUUID(),
                Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"),
                new ResourceLocation("minecraft", "overworld"),
                0L,
                1,
                null);
    }

    private static ProjectState project() {
        return new ProjectState(
                new ResourceLocation("mcaquests", "test_project"),
                ProjectScope.VILLAGE,
                "v:1",
                new ResourceLocation("minecraft", "overworld"),
                BlockPos.ZERO,
                OptionalInt.of(1),
                0L,
                1);
    }

    @Nested
    @DisplayName("per-quest freezing")
    class Quests {

        @Test
        @DisplayName("the first roll wins and later rolls cannot replace it")
        void firstWriterWins() {
            ActiveQuest active = quest();
            assertEquals(7, active.freezeReward(0, 7));
            assertEquals(7, active.freezeReward(0, 99),
                    "a second freeze must return the original amount, not overwrite it");
            assertEquals(OptionalInt.of(7), active.frozenReward(0));
        }

        @Test
        @DisplayName("different reward slots freeze independently")
        void slotsAreIndependent() {
            ActiveQuest active = quest();
            active.freezeReward(0, 3);
            active.freezeReward(2, 11);
            assertEquals(OptionalInt.of(3), active.frozenReward(0));
            assertEquals(OptionalInt.of(11), active.frozenReward(2));
            assertEquals(OptionalInt.empty(), active.frozenReward(1), "an unfrozen slot stays empty");
        }

        @Test
        @DisplayName("a frozen amount survives save and reload")
        void survivesReload() {
            ActiveQuest active = quest();
            active.freezeReward(0, 5);

            ActiveQuest reloaded = ActiveQuest.load(active.save());

            assertEquals(OptionalInt.of(5), reloaded.frozenReward(0),
                    "reconnecting or reloading the world must not reroll the payout");
            assertEquals(5, reloaded.freezeReward(0, 42), "and it still cannot be overwritten afterwards");
        }

        @Test
        @DisplayName("a frozen amount of zero is distinguishable from no frozen amount")
        void zeroIsNotAbsent() {
            // Matters because the grant path falls back to a fresh roll only when NOTHING is frozen; a
            // legitimately-zero payout (currencyRewardMultiplier = 0) must not look like "not yet rolled".
            ActiveQuest active = quest();
            active.freezeReward(0, 0);
            assertTrue(active.frozenReward(0).isPresent());
            assertEquals(0, active.frozenReward(0).getAsInt());
            assertEquals(OptionalInt.of(0), ActiveQuest.load(active.save()).frozenReward(0));
        }

        @Test
        @DisplayName("a quest with no randomized reward writes no extra NBT")
        void nothingFrozenWritesNothing() {
            assertFalse(quest().save().contains("frozen_rewards"),
                    "old saves must round-trip byte-identically when no reward is randomized");
        }

        @Test
        @DisplayName("a pre-1.1.0 save loads with no frozen rewards")
        void legacySaveLoads() {
            CompoundTag legacy = quest().save();
            legacy.remove("frozen_rewards");
            ActiveQuest loaded = ActiveQuest.load(legacy);
            assertEquals(OptionalInt.empty(), loaded.frozenReward(0));
        }
    }

    @Nested
    @DisplayName("per-project-phase freezing")
    class Projects {

        @Test
        @DisplayName("one phase reward freezes once and is shared by every recipient")
        void phaseRewardFreezesOnce() {
            ProjectState state = project();
            assertEquals(6, state.freezeReward(0, 0, 6));
            // A second contributor being paid, or an offline player collecting later, reads the same value.
            assertEquals(6, state.freezeReward(0, 0, 20));
            assertEquals(OptionalInt.of(6), state.frozenReward(0, 0));
        }

        @Test
        @DisplayName("phase and reward index are both part of the key")
        void phaseAndIndexAreDistinct() {
            ProjectState state = project();
            state.freezeReward(0, 0, 2);
            state.freezeReward(0, 1, 5);
            state.freezeReward(1, 0, 9);
            assertEquals(OptionalInt.of(2), state.frozenReward(0, 0));
            assertEquals(OptionalInt.of(5), state.frozenReward(0, 1));
            assertEquals(OptionalInt.of(9), state.frozenReward(1, 0));
            assertEquals(OptionalInt.empty(), state.frozenReward(1, 1));
        }

        @Test
        @DisplayName("frozen phase rewards survive a world save/reload")
        void survivesReload() {
            ProjectState state = project();
            state.freezeReward(0, 0, 4);
            state.freezeReward(1, 2, 8);

            ProjectState reloaded = ProjectState.load(state.save());

            assertEquals(OptionalInt.of(4), reloaded.frozenReward(0, 0));
            assertEquals(OptionalInt.of(8), reloaded.frozenReward(1, 2));
        }

        @Test
        @DisplayName("a project with no randomized reward writes no extra NBT")
        void nothingFrozenWritesNothing() {
            assertFalse(project().save().contains("frozen_rewards"));
        }
    }
}
