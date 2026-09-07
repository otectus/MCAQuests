package dev.otectus.mcaquests.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Bounds expensive client-request work on NeoForge's main-thread payload dispatcher. */
final class PacketRequests {
    private static final Map<ServerPlayer, Budget> BUDGETS = new WeakHashMap<>();

    private PacketRequests() {
    }

    static boolean allow(ServerPlayer sender) {
        synchronized (BUDGETS) {
            return BUDGETS.computeIfAbsent(sender, player -> new Budget())
                    .take(System.nanoTime() / 1_000_000L);
        }
    }

    /** A short UI burst is allowed; sustained spam cannot enqueue unbounded menu/snapshot work. */
    static final class Budget {
        private static final double CAPACITY = 20;
        private double tokens = CAPACITY;
        private long lastMillis;
        private boolean initialized;

        boolean take(long nowMillis) {
            if (initialized) {
                tokens = Math.min(CAPACITY, tokens + Math.max(0L, nowMillis - lastMillis) / 100.0);
            }
            initialized = true;
            lastMillis = nowMillis;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
