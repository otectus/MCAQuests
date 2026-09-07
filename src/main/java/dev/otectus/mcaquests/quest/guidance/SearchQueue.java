package dev.otectus.mcaquests.quest.guidance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A bounded, server-thread-only queue. A step must yield instead of waiting for unfinished work. */
final class SearchQueue<K, V> {
    interface Task<V> {
        Step<V> step();
    }

    record Step<V>(boolean done, Optional<V> result) {
        static <V> Step<V> pending() { return new Step<>(false, Optional.empty()); }
        static <V> Step<V> finished(Optional<V> result) { return new Step<>(true, result); }
    }

    private final class Entry {
        final K key;
        final Supplier<Task<V>> factory;
        final CompletableFuture<Optional<V>> result = new CompletableFuture<>();
        Task<V> task;
        long touched;
        long completed;

        Entry(K key, long now, Supplier<Task<V>> factory) {
            this.key = key;
            this.touched = now;
            this.factory = factory;
        }
    }

    private final int capacity;
    private final long idleTicks;
    private final long hitTicks;
    private final long missTicks;
    private final Map<K, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<Entry> pending = new ArrayDeque<>();

    SearchQueue(int capacity, long idleTicks, long hitTicks, long missTicks) {
        this.capacity = capacity;
        this.idleTicks = idleTicks;
        this.hitTicks = hitTicks;
        this.missTicks = missTicks;
    }

    CompletableFuture<Optional<V>> request(K key, long now, Supplier<Task<V>> factory) {
        Entry entry = entries.get(key);
        if (entry != null && expired(entry, now)) {
            remove(entry);
            entry = null;
        }
        if (entry != null) {
            entry.touched = now;
            return entry.result;
        }
        // Never displace a live player's pending search to admit another one.
        if (entries.size() >= capacity) {
            for (Entry old : new ArrayList<>(entries.values())) {
                if (old.result.isDone() || expired(old, now)) {
                    remove(old);
                    break;
                }
            }
        }
        if (entries.size() >= capacity) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        entry = new Entry(key, now, factory);
        entries.put(key, entry);
        pending.addLast(entry);
        return entry.result;
    }

    void tick(long now, int steps, long nanos) {
        long started = System.nanoTime();
        int count = Math.min(steps, pending.size());
        for (int i = 0; i < count && System.nanoTime() - started < nanos; i++) {
            // Completing a public future runs its callbacks inline. A callback may clear the
            // queue (for example during a reload), so the original size is only an upper bound.
            Entry entry = pending.pollFirst();
            if (entry == null) break;
            if (expired(entry, now)) {
                remove(entry);
                continue;
            }
            try {
                if (entry.task == null) entry.task = entry.factory.get();
                Step<V> step = entry.task.step();
                if (step.done()) {
                    entry.completed = now;
                    entry.result.complete(step.result());
                    entry.task = null;
                } else {
                    pending.addLast(entry);
                }
            } catch (RuntimeException exception) {
                entry.completed = now;
                entry.result.completeExceptionally(exception);
                entry.task = null;
            }
        }
        entries.values().removeIf(entry -> entry.result.isDone() && expired(entry, now));
    }

    private boolean expired(Entry entry, long now) {
        if (!entry.result.isDone()) return now - entry.touched >= idleTicks;
        boolean hit = !entry.result.isCompletedExceptionally()
                && entry.result.getNow(Optional.empty()).isPresent();
        return now - entry.completed >= (hit ? hitTicks : missTicks);
    }

    private void remove(Entry entry) {
        entries.remove(entry.key);
        pending.remove(entry);
        entry.result.complete(Optional.empty());
    }

    void clear() {
        // Detach the old generation before completing anything: callbacks may submit fresh work.
        var removed = new ArrayList<>(entries.values());
        entries.clear();
        pending.clear();
        removed.forEach(entry -> entry.result.complete(Optional.empty()));
    }
}
