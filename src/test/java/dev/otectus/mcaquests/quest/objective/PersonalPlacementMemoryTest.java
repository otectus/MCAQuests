package dev.otectus.mcaquests.quest.objective;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placing and breaking one block can no longer farm personal objectives (1.7.0; content audit F-D01).
 */
class PersonalPlacementMemoryTest {

    @Test
    @DisplayName("a block the player placed is not progress when they break it, and is forgotten after")
    void selfPlacedBlockIsNotBreakProgress() {
        ObjectiveProgress progress = new ObjectiveProgress();
        BlockPos pos = new BlockPos(10, 64, -3);
        BreakBlockObjective.rememberPlaced(progress, pos);
        assertTrue(BreakBlockObjective.consumePlaced(progress, pos), "breaking the placed block is refused");
        assertFalse(BreakBlockObjective.consumePlaced(progress, pos),
                "once broken, the position is free: a naturally generated block there counts again");
    }

    @Test
    @DisplayName("a place_block position counts once, however often a block is re-placed there")
    void rePlacedPositionCountsOnce() {
        ObjectiveProgress progress = new ObjectiveProgress();
        BlockPos pos = new BlockPos(1, 2, 3);
        assertTrue(progress.addVisited(pos));
        assertFalse(progress.addVisited(pos));
    }

    @Test
    @DisplayName("the self-placed memory is bounded and forgets its oldest entries first")
    void memoryIsBounded() {
        ObjectiveProgress progress = new ObjectiveProgress();
        for (int i = 0; i < BreakBlockObjective.PLACED_MEMORY + 10; i++) {
            BreakBlockObjective.rememberPlaced(progress, new BlockPos(i, 64, 0));
        }
        assertFalse(BreakBlockObjective.consumePlaced(progress, new BlockPos(0, 64, 0)), "the oldest is forgotten");
        assertTrue(BreakBlockObjective.consumePlaced(progress,
                new BlockPos(BreakBlockObjective.PLACED_MEMORY + 9, 64, 0)), "the newest is remembered");
    }
}
