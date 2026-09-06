package dev.otectus.mcaquests.quest.guidance;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueueTest {
    private final SearchQueue<String, Integer> queue = new SearchQueue<>(2, 20, 100, 10);

    @Test
    void submissionIsDeferredAndShared() {
        AtomicInteger initialized = new AtomicInteger();
        var first = queue.request("fortress", 0, () -> {
            initialized.incrementAndGet();
            return () -> SearchQueue.Step.finished(Optional.of(42));
        });
        assertSame(first, queue.request("fortress", 0, () -> { throw new AssertionError(); }));
        assertEquals(0, initialized.get());
        assertFalse(first.isDone());
        queue.tick(1, 8, Long.MAX_VALUE);
        assertEquals(Optional.of(42), first.join());
        assertEquals(1, initialized.get());
    }

    @Test
    void unfinishedIoYieldsAndOtherSearchesGetATurn() {
        CompletableFuture<Integer> io = new CompletableFuture<>();
        var slow = queue.request("slow", 0, () -> () -> io.isDone()
                ? SearchQueue.Step.finished(Optional.of(io.getNow(0))) : SearchQueue.Step.pending());
        var fast = queue.request("fast", 0, () -> () -> SearchQueue.Step.finished(Optional.of(7)));
        queue.tick(1, 1, Long.MAX_VALUE);
        assertFalse(slow.isDone());
        assertFalse(fast.isDone());
        queue.tick(2, 1, Long.MAX_VALUE);
        assertEquals(Optional.of(7), fast.join());
        io.complete(12);
        queue.tick(3, 1, Long.MAX_VALUE);
        assertEquals(Optional.of(12), slow.join());
    }

    @Test
    void admissionCannotEvictPendingWork() {
        var one = queue.request("one", 0, () -> SearchQueue.Step::pending);
        var two = queue.request("two", 0, () -> SearchQueue.Step::pending);
        var excess = queue.request("three", 0, () -> { throw new AssertionError(); });
        assertEquals(Optional.empty(), excess.join());
        assertFalse(one.isDone());
        assertFalse(two.isDone());
        queue.clear();
        assertEquals(Optional.empty(), one.join());
        assertEquals(Optional.empty(), two.join());
    }

    @Test
    void abandonedWorkExpiresButPollingKeepsItAlive() {
        var abandoned = queue.request("abandoned", 0, () -> SearchQueue.Step::pending);
        var live = queue.request("live", 0, () -> SearchQueue.Step::pending);
        assertSame(live, queue.request("live", 19, () -> { throw new AssertionError(); }));
        queue.tick(20, 8, Long.MAX_VALUE);
        assertEquals(Optional.empty(), abandoned.join());
        assertFalse(live.isDone());
    }

    @Test
    void cachedMissRetriesAndOneFailureDoesNotStopQueue() {
        var bad = queue.request("bad", 0, () -> () -> { throw new IllegalStateException("bad generator"); });
        var miss = queue.request("miss", 0, () -> () -> SearchQueue.Step.finished(Optional.empty()));
        queue.tick(1, 8, Long.MAX_VALUE);
        assertTrue(bad.isCompletedExceptionally());
        assertEquals(Optional.empty(), miss.join());
        assertSame(miss, queue.request("miss", 10, () -> { throw new AssertionError(); }));
        var retry = queue.request("miss", 11, () -> () -> SearchQueue.Step.finished(Optional.of(3)));
        assertNotSame(miss, retry);
        queue.tick(12, 8, Long.MAX_VALUE);
        assertEquals(Optional.of(3), retry.join());
    }

    @Test
    void zeroTimeBudgetDoesNoWork() {
        var result = queue.request("queued", 0, () -> { throw new AssertionError(); });
        queue.tick(1, 8, 0);
        assertFalse(result.isDone());
    }
}
