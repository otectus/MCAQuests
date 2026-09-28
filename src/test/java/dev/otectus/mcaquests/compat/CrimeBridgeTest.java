package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure refusal rule behind the wanted-player gate (1.7.1). */
class CrimeBridgeTest {

    @Test
    void nobodyRefusesAPlayerWhoIsNotWanted() {
        assertFalse(CrimeBridge.refuses(false, true, true, true));
        assertFalse(CrimeBridge.refuses(false, false, true, true));
    }

    @Test
    void theLawRefusesUnderResponderRefusesWanted() {
        assertTrue(CrimeBridge.refuses(true, true, true, false));
        assertFalse(CrimeBridge.refuses(true, true, false, false), "switched off: a guard still deals");
        assertFalse(CrimeBridge.refuses(true, false, true, false), "a farmer is not the law");
    }

    @Test
    void everyGiverRefusesUnderAllGiversRefuseWanted() {
        assertTrue(CrimeBridge.refuses(true, false, false, true));
        assertTrue(CrimeBridge.refuses(true, true, false, true));
    }
}
