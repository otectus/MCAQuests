package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.quest.situation.state.SituationStatus;
import dev.otectus.mcaquests.quest.situation.state.TownsteadSignalStateSavedData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §16: the five world-scoped stores survive the 1.21 {@code SavedData} signature change.
 *
 * <p>Two things changed at once in the port, and only one of them is visible to a compiler.
 * {@code save}/{@code load} gained a {@code HolderLookup.Provider}, and the store is now looked up
 * through a {@code SavedData.Factory} rather than a pair of method references — so a store whose
 * factory was wired to the wrong constructor, or whose {@code DATA_NAME} drifted, compiles perfectly
 * and quietly reads an empty file. A renamed {@code DATA_NAME} in particular is silent data loss of
 * the worst kind: the world still loads, everything is simply gone, and the old file sits beside the
 * new one.
 *
 * <p>Each store is therefore asserted three ways: its {@code DATA_NAME} is the literal string the
 * 1.20.1 build wrote (hard-coded here rather than read off the class, since the point is that it must
 * not change), its {@code FACTORY} exists, and a populated instance is save/load/save-stable — the
 * second save must be byte-equal to the first, which is what catches a field written but not read.
 */
class SavedDataRoundTripTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /**
     * save -> load -> save, asserting the two tags match.
     *
     * <p>Comparing the two <em>written</em> tags rather than the two objects is deliberate: it needs
     * no {@code equals} on stores that have none, and it fails on precisely the asymmetry that matters
     * — anything {@code save} writes that {@code load} does not read comes back missing the second
     * time round.
     */
    private static <T extends SavedData> void assertStable(T store,
                                                           BiFunction<T, CompoundTag, CompoundTag> save,
                                                           java.util.function.Function<CompoundTag, T> load,
                                                           String what) {
        CompoundTag first = save.apply(store, new CompoundTag());
        T reloaded = load.apply(first);
        CompoundTag second = save.apply(reloaded, new CompoundTag());
        assertEquals(first, second, what + ": save/load/save must be stable");
    }

    private static void assertName(String expected, String actual, Supplier<Object> factory, String what) {
        assertEquals(expected, actual, what + ": DATA_NAME names the world file and must never change");
        assertNotNull(factory.get(), what + ": FACTORY must exist for computeIfAbsent");
    }

    // ---------------------------------------------------------------- projects

    @Test
    @DisplayName("ProjectSavedData: name, factory, and a populated save/load/save")
    void projectSavedData() {
        assertName("mcaquests_projects", ProjectSavedData.DATA_NAME, () -> ProjectSavedData.FACTORY, "projects");

        UUID sponsor = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID player = UUID.fromString("00000000-0000-4000-8000-000000000002");
        ProjectSavedData data = new ProjectSavedData();

        ProjectState state = new ProjectState(ResourceLocation.parse("mcaquests:well_repair"),
                ProjectScope.VILLAGE, "v:12", ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(10, 64, -20), OptionalInt.of(12), 1000L, 2);
        state.addSponsor(sponsor);
        state.addParticipant(player);
        state.progress(0).add(40);
        state.progress(0).addContribution(player, 40);
        state.tryMarkPhaseDistributed(0);
        state.setStatus(ProjectStatus.PAUSED);
        data.putInstance(state);

        data.addReputation("v:12", 25);
        data.setTierHighWater("v:12", "friend");
        data.addPending(player, PendingReward.ofPhase(ResourceLocation.parse("mcaquests:well_repair"), 0, 1));
        data.addBankedReward(player, BankedReward.reputation(15));

        assertStable(data, (store, tag) -> store.save(tag, RegistryAccess.EMPTY),
                tag -> ProjectSavedData.load(tag, RegistryAccess.EMPTY), "projects");
    }

    // ---------------------------------------------------------------- situations

    @Test
    @DisplayName("SituationSavedData: name, factory, and a populated save/load/save")
    void situationSavedData() {
        assertName("mcaquests_situations", SituationSavedData.DATA_NAME, () -> SituationSavedData.FACTORY,
                "situations");

        SituationSavedData data = new SituationSavedData();
        UUID instanceId = UUID.fromString("00000000-0000-4000-8000-00000000000a");
        SituationInstance instance = new SituationInstance(instanceId,
                ResourceLocation.parse("mcaquests:after_raid"), 12,
                UUID.fromString("00000000-0000-4000-8000-00000000000b"),
                UUID.fromString("00000000-0000-4000-8000-00000000000c"), 1000L, 5000L, 42L,
                SituationStatus.OPEN);
        instance.addParticipant(UUID.fromString("00000000-0000-4000-8000-00000000000d"));
        data.putInstance(instance);
        data.setCooldownUntil(12, ResourceLocation.parse("mcaquests:after_raid"), 8000L);
        data.setGlobalCooldownUntil(12, 9000L);

        assertStable(data, (store, tag) -> store.save(tag, RegistryAccess.EMPTY),
                tag -> SituationSavedData.load(tag, RegistryAccess.EMPTY), "situations");
    }

    // ---------------------------------------------------------------- townstead signals

    @Test
    @DisplayName("TownsteadSignalStateSavedData: name, factory, and a populated save/load/save")
    void townsteadSignalStateSavedData() {
        assertName("mcaquests_townstead_signals", TownsteadSignalStateSavedData.DATA_NAME,
                () -> TownsteadSignalStateSavedData.FACTORY, "townstead signals");

        TownsteadSignalStateSavedData data = new TownsteadSignalStateSavedData();
        // The whole point of this store is the *previous* observation, so a round trip that lost it
        // would re-fire every edge-triggered situation on the next world load.
        data.observeChanged("v:12:population", 40);
        data.observeIncrease("v:12:buildings", 7);
        data.observeRisingEdge("v:12:under_attack", true);

        assertStable(data, (store, tag) -> store.save(tag, RegistryAccess.EMPTY),
                tag -> TownsteadSignalStateSavedData.load(tag, RegistryAccess.EMPTY), "townstead signals");
    }

    // ---------------------------------------------------------------- dead givers

    @Test
    @DisplayName("DeadGiversData: name, factory, and a populated save/load/save")
    void deadGiversData() {
        assertName("mcaquests_dead_givers", DeadGiversData.DATA_NAME, () -> DeadGiversData.FACTORY,
                "dead givers");

        DeadGiversData data = new DeadGiversData();
        UUID giver = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        data.record(giver, 12345L);
        data.record(UUID.fromString("00000000-0000-4000-8000-0000000000a2"), 67890L);
        assertTrue(data.isDead(giver), "precondition: the store remembers what it was told");

        assertStable(data, (store, tag) -> store.save(tag, RegistryAccess.EMPTY),
                tag -> DeadGiversData.load(tag, RegistryAccess.EMPTY), "dead givers");
    }

    // ---------------------------------------------------------------- pending hearts

    @Test
    @DisplayName("PendingHeartsData: name, factory, and a populated save/load/save")
    void pendingHeartsData() {
        assertName("mcaquests_pending_hearts", PendingHeartsData.DATA_NAME, () -> PendingHeartsData.FACTORY,
                "pending hearts");

        PendingHeartsData data = new PendingHeartsData();
        UUID villager = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
        data.queue(villager, UUID.fromString("00000000-0000-4000-8000-0000000000b2"), 5);
        data.queue(villager, UUID.fromString("00000000-0000-4000-8000-0000000000b3"), -2);

        assertStable(data, (store, tag) -> store.save(tag, RegistryAccess.EMPTY),
                tag -> PendingHeartsData.load(tag, RegistryAccess.EMPTY), "pending hearts");
    }
}
