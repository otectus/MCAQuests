package dev.otectus.mcaquests.compat;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * What the Townstead integration has actually been doing, for checking it against the performance
 * budget (Townstead spec §17).
 *
 * <p><b>Counted always, reported never.</b> Incrementing a {@code long} costs nothing measurable and
 * allocates nothing, so there is no switch to forget to turn on before reproducing a problem — but
 * nothing is ever logged. The numbers surface only when somebody asks for them, through
 * {@code /mcaquests compat townstead status} with {@code compat.townstead.debugBindingLogs} on. That is
 * what the spec means by "debug counters, not INFO spam": a server that is running fine says nothing,
 * and a server that is not can be asked.
 *
 * <p>Plain fields rather than atomics on purpose. Every call site is on the server thread — the
 * objective pass, the situation sweep, the project sweep, reward grants — so the synchronisation would
 * buy nothing but contention on a hot path. A stale read from the command thread would at worst show a
 * count one behind, which is not worth paying for on every villager.
 */
public final class TownsteadCounters {

    private static long villagerReads;
    private static long cacheHits;
    private static long villagesScanned;
    private static long residentsObserved;
    private static long signalsFired;
    private static long projectPolls;
    private static long capabilityMisses;
    private static long mutationFailures;

    private static long scans;
    private static long scanNanosTotal;
    private static long scanNanosMax;

    private TownsteadCounters() {
    }

    public static void villagerRead() {
        villagerReads++;
    }

    /** A read answered from the per-pass cache instead of going to Townstead. */
    public static void cacheHit() {
        cacheHits++;
    }

    public static void residentObserved() {
        residentsObserved++;
    }

    public static void signalFired() {
        signalsFired++;
    }

    public static void projectPoll() {
        projectPolls++;
    }

    /** A feature asked for a capability this Townstead build does not provide. */
    public static void capabilityMiss() {
        capabilityMisses++;
    }

    public static void mutationFailure() {
        mutationFailures++;
    }

    /** Records one village scan and how long it took, keeping the worst case as well as the mean. */
    public static void villageScanned(long nanos) {
        villagesScanned++;
        scans++;
        scanNanosTotal += nanos;
        scanNanosMax = Math.max(scanNanosMax, nanos);
    }

    /**
     * One line for the status command. Deliberately includes the worst scan alongside the average: the
     * §17 budget is an average under 1 ms with no spike above 5 ms, and an average alone would hide
     * exactly the spike the budget is about.
     */
    public static String describe() {
        double averageMs = scans == 0 ? 0.0D
                : (double) scanNanosTotal / scans / TimeUnit.MILLISECONDS.toNanos(1);
        double maxMs = (double) scanNanosMax / TimeUnit.MILLISECONDS.toNanos(1);
        return String.format(Locale.ROOT,
                "villager reads %d (%d from cache), villages %d, residents %d, signals %d, "
                        + "project polls %d, capability misses %d, mutation failures %d, "
                        + "scan avg %.3f ms / max %.3f ms over %d scans",
                villagerReads, cacheHits, villagesScanned, residentsObserved, signalsFired,
                projectPolls, capabilityMisses, mutationFailures, averageMs, maxMs, scans);
    }

    /** Zeroes everything, so a measurement can be taken over a known window. */
    public static void reset() {
        villagerReads = 0;
        cacheHits = 0;
        villagesScanned = 0;
        residentsObserved = 0;
        signalsFired = 0;
        projectPolls = 0;
        capabilityMisses = 0;
        mutationFailures = 0;
        scans = 0;
        scanNanosTotal = 0;
        scanNanosMax = 0;
    }
}
