package dev.otectus.mcaquests.data;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GraphCyclesDepthTest {
    @Test
    void longChainsDoNotOverflowTheReloadThreadStack() {
        Map<Integer, List<Integer>> graph = new LinkedHashMap<>();
        for (int i = 0; i < 25000; i++) graph.put(i, List.of(i + 1));
        assertTrue(GraphCycles.findCycle(graph).isEmpty());
        graph.put(25000, List.of(24998));
        assertEquals(List.of(24998, 24999, 25000, 24998), GraphCycles.findCycle(graph).orElseThrow());
    }

    @Test
    void sharedBranchesAndSelfCyclesAreDistinguished() {
        assertTrue(GraphCycles.findCycle(Map.of(1, List.of(2, 3), 2, List.of(4), 3, List.of(4))).isEmpty());
        assertEquals(List.of(1, 1), GraphCycles.findCycle(Map.of(1, List.of(1))).orElseThrow());
    }
}
