package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AsyncLocateCacheTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void pendingIsNotAMissAndCompletionIsVisibleOnNextPoll() {
        ObjectiveProgress progress = new ObjectiveProgress();
        CompletableFuture<Optional<BlockPos>> future = new CompletableFuture<>();
        assertTrue(LocateCache.resolveAsync(progress, "structure", "nether", 1, 200, () -> future).isEmpty());
        assertFalse(progress.extra().contains("structureTried"));
        BlockPos fortress = new BlockPos(200, 64, 300);
        future.complete(Optional.of(fortress));
        assertEquals(Optional.of(fortress), LocateCache.resolveAsync(progress, "structure", "nether", 2,
                200, () -> future));
        assertEquals(Optional.of(fortress), LocateCache.resolveAsync(progress, "structure", "nether", 3,
                200, () -> { throw new AssertionError("cached hits do not search"); }));
    }

    @Test
    void oldDimensionCompletionCannotWriteToProgress() {
        ObjectiveProgress progress = new ObjectiveProgress();
        CompletableFuture<Optional<BlockPos>> old = new CompletableFuture<>();
        LocateCache.resolveAsync(progress, "structure", "overworld", 1, 200, () -> old);
        CompletableFuture<Optional<BlockPos>> current = new CompletableFuture<>();
        LocateCache.resolveAsync(progress, "structure", "nether", 2, 200, () -> current);
        old.complete(Optional.of(new BlockPos(1, 2, 3)));
        assertFalse(progress.extra().contains("structureX"));
        current.complete(Optional.of(new BlockPos(4, 5, 6)));
        assertEquals(Optional.of(new BlockPos(4, 5, 6)), LocateCache.resolveAsync(progress, "structure",
                "nether", 3, 200, () -> current));
        assertEquals("nether", progress.extra().getString("structureDim"));
    }

    @Test
    void completedMissStartsRetryDelayAtCompletion() {
        ObjectiveProgress progress = new ObjectiveProgress();
        LocateCache.resolveAsync(progress, "structure", "nether", 10, 200,
                () -> CompletableFuture.completedFuture(Optional.empty()));
        LocateCache.resolveAsync(progress, "structure", "nether", 209, 200,
                () -> { throw new AssertionError("miss still throttled"); });
        BlockPos found = new BlockPos(5, 6, 7);
        assertEquals(Optional.of(found), LocateCache.resolveAsync(progress, "structure", "nether", 210,
                200, () -> CompletableFuture.completedFuture(Optional.of(found))));
    }
}
