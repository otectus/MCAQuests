package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McaProximityTest {
    @Test
    void higherScoringFriendsDoNotHideASpouseOrChangeNegativeHeartSemantics() {
        record Villager(boolean spouse, int hearts) {}
        List<Villager> nearby = List.of(new Villager(false, 200), new Villager(true, 100));
        assertEquals(100, McaCompat.maxMatchingHearts(nearby, Villager::spouse, Villager::hearts).orElseThrow());
        assertEquals(200, McaCompat.maxMatchingHearts(nearby, villager -> true, Villager::hearts).orElseThrow());
        assertEquals(-20, McaCompat.maxMatchingHearts(List.of(new Villager(true, -20)),
                Villager::spouse, Villager::hearts).orElseThrow());
        assertTrue(McaCompat.maxMatchingHearts(List.of(new Villager(false, 200)),
                Villager::spouse, Villager::hearts).isEmpty());
    }

    @Test
    void proximityUsesTheSphereAndIncludesItsBoundary() {
        assertTrue(McaCompat.withinRadius(0, 8));
        assertTrue(McaCompat.withinRadius(64, 8));
        assertFalse(McaCompat.withinRadius(64.01, 8));
        assertFalse(McaCompat.withinRadius(8 * 8 * 3, 8),
                "the far corner of the query AABB is outside the configured scan radius");
    }

    @Test
    void invalidDistancesAndRadiiDoNotSelectVillagers() {
        assertFalse(McaCompat.withinRadius(0, -1));
        assertFalse(McaCompat.withinRadius(0, Double.NaN));
        assertFalse(McaCompat.withinRadius(0, Double.POSITIVE_INFINITY));
        assertFalse(McaCompat.withinRadius(Double.NaN, 8));
        assertFalse(McaCompat.withinRadius(-1, 8));
    }
}
