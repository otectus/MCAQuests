package dev.otectus.mcaquests.state;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outage ledger (1.7.0): time a definition this world knew was not loaded is credited to any clock
 * that missed it, whether anyone was online or not.
 */
class ContentOutageLedgerTest {

    private static final String QUEST = "quest:test:harvest";
    private static final String OTHER = "quest:test:other";

    @Test
    @DisplayName("an outage entirely while the owner was offline is credited on their return")
    void offlineOutageIsCredited() {
        ContentOutageData ledger = new ContentOutageData();
        ledger.sample(1_000L, Set.of(QUEST, OTHER));          // both loaded; the owner logs off at 1,500
        ledger.sample(2_000L, Set.of(OTHER));                 // restart without the mod
        ledger.sample(5_000L, Set.of(QUEST, OTHER));          // restart with it
        assertEquals(3_000L, ledger.overlap(QUEST, 1_500L, 9_000L), "the owner returns at 9,000");
        assertEquals(0L, ledger.overlap(OTHER, 1_500L, 9_000L));
    }

    @Test
    @DisplayName("an outage still open is credited up to now, and is reported as tracked")
    void openOutageRunsToNow() {
        ContentOutageData ledger = new ContentOutageData();
        ledger.sample(0L, Set.of(QUEST));
        ledger.sample(100L, Set.of());
        assertTrue(ledger.covers(QUEST));
        assertEquals(400L, ledger.overlap(QUEST, 0L, 500L));
        assertEquals(150L, ledger.overlap(QUEST, 350L, 500L), "only the unaccounted part is credited");
    }

    @Test
    @DisplayName("crediting in steps never counts the same outage twice")
    void steppedCreditIsExact() {
        ContentOutageData ledger = new ContentOutageData();
        ledger.sample(0L, Set.of(QUEST));
        ledger.sample(1_000L, Set.of());
        ledger.sample(3_000L, Set.of(QUEST));
        long total = 0L;
        for (long t = 500L; t < 4_000L; t += 250L) {
            total += ledger.overlap(QUEST, t, t + 250L);
        }
        assertEquals(2_000L, total);
    }

    @Test
    @DisplayName("a definition the world never loaded is not tracked, so its record keeps the old accrual")
    void neverLoadedIsNotTracked() {
        ContentOutageData ledger = new ContentOutageData();
        ledger.sample(0L, Set.of(OTHER));
        assertFalse(ledger.covers(QUEST));
        assertEquals(0L, ledger.overlap(QUEST, 0L, 1_000L));
    }

    @Test
    @DisplayName("the ledger round-trips, open and closed intervals included, and stays bounded")
    void persistsAndStaysBounded() {
        ContentOutageData ledger = new ContentOutageData();
        long t = 0L;
        ledger.sample(t, Set.of(QUEST));
        for (int i = 0; i < ContentOutageData.MAX_INTERVALS + 5; i++) {
            ledger.sample(t += 10L, Set.of());
            ledger.sample(t += 10L, Set.of(QUEST));
        }
        ledger.sample(t += 10L, Set.of());
        long before = ledger.overlap(QUEST, 0L, t + 50L);
        ContentOutageData reloaded = ContentOutageData.load(ledger.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(before, reloaded.overlap(QUEST, 0L, t + 50L));
        assertTrue(reloaded.covers(QUEST));
        assertTrue(before >= (ContentOutageData.MAX_INTERVALS + 5) * 10L,
                "merging old intervals may over-credit, never under-credit");
    }
}
