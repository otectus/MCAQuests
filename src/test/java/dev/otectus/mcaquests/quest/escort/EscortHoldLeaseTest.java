package dev.otectus.mcaquests.quest.escort;

import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escort leases survive a restart (1.7.0): what the registry held is what a reload of its saved data
 * restores, so abandoning an escort after a restart still finds the villager to release.
 */
class EscortHoldLeaseTest {

    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @AfterEach
    void reset() {
        EscortHoldRegistry.clear();
    }

    @Test
    @DisplayName("held and pending leases round-trip through the saved data")
    void leasesSurviveARestart() {
        EscortHoldRegistry.hold(VILLAGER, PLAYER);
        EscortHoldRegistry.enqueueRelease(OTHER, PLAYER);
        net.minecraft.nbt.CompoundTag tag = new EscortHoldSavedData().save(new net.minecraft.nbt.CompoundTag(), RegistryAccess.EMPTY);

        EscortHoldRegistry.clear();
        assertTrue(EscortHoldRegistry.heldBy(PLAYER).isEmpty(), "the restart forgot everything in memory");

        EscortHoldSavedData.load(tag, RegistryAccess.EMPTY);
        assertEquals(Set.of(VILLAGER), EscortHoldRegistry.heldBy(PLAYER));
        assertTrue(EscortHoldRegistry.isReleasePending(OTHER));
        assertEquals(PLAYER, EscortHoldRegistry.claimRelease(OTHER).orElseThrow());
        assertFalse(EscortHoldRegistry.isReleasePending(OTHER), "a release is paid out exactly once");
    }

    @Test
    @DisplayName("a re-asserted hold converts a queued release back into a hold")
    void reassertedHoldCancelsTheQueuedRelease() {
        EscortHoldRegistry.enqueueRelease(VILLAGER, PLAYER);
        EscortHoldRegistry.hold(VILLAGER, PLAYER);
        assertFalse(EscortHoldRegistry.isReleasePending(VILLAGER));
        assertEquals(Map.of(VILLAGER, new EscortHoldRegistry.Lease(PLAYER, false)), EscortHoldRegistry.snapshot());
    }

    @Test
    @DisplayName("releasing forgets the lease, and heldBy only lists live holds")
    void releaseForgets() {
        EscortHoldRegistry.hold(VILLAGER, PLAYER);
        EscortHoldRegistry.enqueueRelease(OTHER, PLAYER);
        assertEquals(Set.of(VILLAGER), EscortHoldRegistry.heldBy(PLAYER), "a pending release is not a hold");
        EscortHoldRegistry.release(VILLAGER);
        assertTrue(EscortHoldRegistry.leaseOf(VILLAGER).isEmpty());
    }
}
