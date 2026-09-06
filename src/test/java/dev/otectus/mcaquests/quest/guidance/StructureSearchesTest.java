package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructureSearchesTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void placementRegionsAreVisitedOnceIncludingNegativeCoordinates() {
        Set<ChunkPos> visited = new HashSet<>();
        for (int radius = 0; radius <= 8; radius++) {
            for (int i = 0; i < Math.max(1, radius * 8); i++) {
                ChunkPos offset = StructureSearches.offset(radius, i);
                assertEquals(radius, Math.max(Math.abs(offset.x), Math.abs(offset.z)));
                assertTrue(visited.add(offset), "must never request the same region twice");
            }
        }
        assertEquals(17 * 17, visited.size());
        assertTrue(visited.contains(new ChunkPos(-8, -8)));
        assertTrue(visited.contains(new ChunkPos(8, 8)));
    }
}
