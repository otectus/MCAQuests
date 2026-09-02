package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On an install without MCA: Reputation, every award carried a dedupe key and every key was written
 * into the standing store's migration-marker map, where nothing ever removed it: one permanent entry per
 * turn-in, situation, project phase and FTB claim, in {@code mcaquests_projects.dat}, forever. Keys are
 * now a bounded per-player ring, and the markers already written are dropped on first load.
 */
class VillageStandingDedupeTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    @DisplayName("the ring is bounded, evicts oldest first, and round-trips")
    void awardsAreBoundedAndPersist() {
        VillageStanding standing = new VillageStanding();
        for (int i = 0; i < 200; i++) {
            assertTrue(standing.recordAward(PLAYER, "key" + i), "a key never seen before is new");
        }
        assertFalse(standing.recordAward(PLAYER, "key199"), "the most recent key is still remembered");
        assertTrue(standing.recordAward(PLAYER, "key0"),
                "the oldest keys are evicted once the cap is reached — that is what bounds the save");

        VillageStanding reloaded = VillageStanding.load(standing.save());
        assertFalse(reloaded.recordAward(PLAYER, "key199"),
                "the guarantee has to survive save/load, or a restart would double every pending award");
        assertFalse(reloaded.recordAward(PLAYER, "key0"), "including the key that was just re-recorded");
    }

    @Test
    @DisplayName("legacy dedupe markers are dropped on load and real migration markers are kept")
    void legacyMarkersAreStripped() {
        CompoundTag migrations = new CompoundTag();
        migrations.putString("dedupe:minecraft:overworld/3:quest|x", "1");
        migrations.putString("legacy_v1", "1");
        CompoundTag entry = new CompoundTag();
        entry.put("migrations", migrations);
        CompoundTag players = new CompoundTag();
        players.put(PLAYER.toString(), entry);
        CompoundTag root = new CompoundTag();
        root.put("players", players);

        VillageStanding standing = VillageStanding.load(root);

        assertFalse(standing.hasMigrated(PLAYER, "dedupe:minecraft:overworld/3:quest|x"),
                "the unbounded award markers are exactly what this release stops keeping");
        assertTrue(standing.hasMigrated(PLAYER, "legacy_v1"),
                "a 'this import has already run' marker must never be lost");
    }
}
