package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.WaypointSpec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Makes every installed map show the desired set of waypoints, and says what happened.
 *
 * <p>Desired-state reconciliation rather than the old publish-and-forget diff. The caller says what
 * <em>should</em> be on the map; each backend says what it has; the difference is applied and the
 * outcome is returned. Nothing here decides what a destination is, keeps a clock, or reads the game —
 * time and the current dimension arrive as parameters, which is what makes the whole thing testable
 * without a client.
 *
 * <h2>What it fixes</h2>
 *
 * <ul>
 *   <li><b>Failures are outcomes.</b> A backend that could not apply a spec leaves its own applied
 *       state unchanged, so the next pass tries again — under {@link RetryBackoff}, so a permanently
 *       broken binding costs one attempt a minute rather than twenty a second.</li>
 *   <li><b>Backends are isolated.</b> One failing backend neither suppresses nor delays the other; each
 *       has its own backoff and each is asked separately.</li>
 *   <li><b>One warning per cause.</b> A fingerprinted log-once, because the alternative — the honest
 *       one, a line per failure — is a log file per session.</li>
 *   <li><b>Other dimensions are the reconciler's problem.</b> A backend whose waypoints have no
 *       dimension of their own never sees a spec for anywhere else, and so never needs to ask the
 *       client where the player is.</li>
 * </ul>
 */
public final class WaypointReconciler {

    private final Map<String, RetryBackoff> backoffs = new HashMap<>();
    /** Keys a backend failed to remove. Retained so a failed cleanup is retried, not forgotten. */
    private final Map<String, Set<String>> pendingRemovals = new HashMap<>();
    /** Backend id plus failure fingerprint, so each distinct cause is warned about exactly once. */
    private final Set<String> warned = new HashSet<>();

    /**
     * Brings {@code backends} in line with {@code desired}.
     *
     * @param enabled          per-backend config gate. A backend that has just been turned off is
     *                         cleared rather than merely skipped, so its waypoints go away at once
     * @param currentDimension where the player is, for backends that can only draw one dimension
     * @param nowMillis        wall clock, for backoff only
     */
    public SyncReport reconcile(Collection<MapWaypointBackend> backends, List<WaypointSpec> desired,
                                Predicate<MapWaypointBackend> enabled,
                                @Nullable ResourceKey<Level> currentDimension,
                                long worldEpoch, long guidanceRevision, long nowMillis) {
        List<SyncReport.BackendReport> reports = new ArrayList<>(backends.size());
        for (MapWaypointBackend backend : backends) {
            reports.add(reconcile(backend, desired, enabled.test(backend), currentDimension, nowMillis));
        }
        return new SyncReport(worldEpoch, guidanceRevision, reports);
    }

    private SyncReport.BackendReport reconcile(MapWaypointBackend backend, List<WaypointSpec> desired,
                                               boolean enabled,
                                               @Nullable ResourceKey<Level> currentDimension,
                                               long nowMillis) {
        RetryBackoff backoff = backoffs.computeIfAbsent(backend.id(), id -> new RetryBackoff());
        if (!enabled || !backend.capabilities().automaticWaypoints()) {
            if (!backend.appliedKeys().isEmpty()) {
                backend.clearAutomatic(ClearCause.DISABLED);
            }
            pendingRemovals.remove(backend.id());
            backoff.reset();
            return report(backend, enabled, Map.of(), backoff);
        }
        if (!backend.isUsable() || !backoff.isDue(nowMillis)) {
            return report(backend, true, Map.of(), backoff);
        }

        Map<String, MapMutationResult> results = new LinkedHashMap<>();
        Map<String, MapMutationResult> skipped = new LinkedHashMap<>();
        boolean failed = false;

        // Withdrawals first: a destination that changed dimension is a removal and a create, and this
        // order means the map never briefly holds two waypoints for one quest.
        Map<String, WaypointSpec> applicable = applicable(backend, desired, currentDimension, skipped);
        Set<String> stale = new HashSet<>(backend.appliedKeys());
        stale.addAll(pendingRemovals.getOrDefault(backend.id(), Set.of()));
        stale.removeAll(applicable.keySet());
        for (String key : stale) {
            MapMutationResult result = backend.withdraw(key);
            results.put(key, result);
            if (result == MapMutationResult.FAILED || result == MapMutationResult.RETRY_LATER) {
                pendingRemovals.computeIfAbsent(backend.id(), id -> new HashSet<>()).add(key);
                failed |= result == MapMutationResult.FAILED;
            } else {
                forgetRemoval(backend.id(), key);
            }
        }

        for (WaypointSpec spec : applicable.values()) {
            MapMutationResult result = backend.apply(spec);
            results.put(spec.key(), result);
            failed |= result == MapMutationResult.FAILED;
        }

        if (failed) {
            backoff.recordFailure(nowMillis);
            warnOnce(backend);
        } else if (!results.containsValue(MapMutationResult.RETRY_LATER)) {
            backoff.recordSuccess();
        }
        // Last, so that the pass which also withdraws a destination that has just changed dimension
        // reports why it went rather than only that it did. The skip is the standing state; the
        // withdrawal happens once.
        results.putAll(skipped);
        return report(backend, true, results, backoff);
    }

    /**
     * The desired specs this backend can actually take, recording a result for the ones it cannot.
     *
     * <p>A spec dropped here must not survive in the backend's applied state either, or a waypoint
     * published in the overworld would still be on the map after the player walked into the Nether.
     * Leaving it out of the applicable set is what makes the stale pass withdraw it.
     */
    private static Map<String, WaypointSpec> applicable(MapWaypointBackend backend,
                                                        List<WaypointSpec> desired,
                                                        @Nullable ResourceKey<Level> currentDimension,
                                                        Map<String, MapMutationResult> skipped) {
        Map<String, WaypointSpec> applicable = new LinkedHashMap<>();
        boolean hereOnly = backend.capabilities().currentDimensionOnly();
        for (WaypointSpec spec : desired) {
            if (spec.ownership() != WaypointSpec.Ownership.AUTOMATIC) {
                continue;
            }
            if (hereOnly && !spec.dimension().equals(currentDimension)) {
                skipped.put(spec.key(), MapMutationResult.SKIPPED_OTHER_DIMENSION);
                continue;
            }
            applicable.put(spec.key(), spec);
        }
        return applicable;
    }

    /**
     * Takes every automatic waypoint off every map, and forgets the backoff with it.
     *
     * <p>Called on the lifecycle events that end a world: what failed to publish into a world that no
     * longer exists is not a failure worth waiting out before touching the next one.
     */
    public void clearAutomatic(Collection<MapWaypointBackend> backends, ClearCause cause) {
        for (MapWaypointBackend backend : backends) {
            backend.clearAutomatic(cause);
            pendingRemovals.remove(backend.id());
            backoffs.computeIfAbsent(backend.id(), id -> new RetryBackoff()).reset();
        }
        warned.clear();
    }

    private void forgetRemoval(String backendId, String key) {
        Set<String> keys = pendingRemovals.get(backendId);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            pendingRemovals.remove(backendId);
        }
    }

    /**
     * One WARN per backend per root cause; everything else is DEBUG.
     *
     * <p>The fingerprint comes from the backend and names the member and the exception rather than the
     * waypoint, so a binding that is broken for every waypoint says so once.
     */
    private void warnOnce(MapWaypointBackend backend) {
        Optional<MapBackendStatus.Failure> failure = backend.status().lastFailure();
        String fingerprint = backend.id() + "/"
                + failure.map(MapBackendStatus.Failure::fingerprint).orElse("unknown");
        if (warned.add(fingerprint)) {
            McaQuests.LOGGER.warn("[MCA: Quests] Map backend {} could not apply a quest waypoint ({}); "
                            + "retrying with backoff. Quest destinations still show on the tracker and "
                            + "on the world marker.", backend.id(),
                    failure.map(f -> f.message().orElse(f.fingerprint())).orElse("no detail"));
        } else {
            McaQuests.LOGGER.debug("[MCA: Quests] Map backend {} failed again ({})", backend.id(),
                    fingerprint);
        }
    }

    private static SyncReport.BackendReport report(MapWaypointBackend backend, boolean enabled,
                                                   Map<String, MapMutationResult> results,
                                                   RetryBackoff backoff) {
        long next = backoff.nextAttemptAtMillis();
        return new SyncReport.BackendReport(backend.id(), enabled, backend.isUsable(),
                backend.appliedKeys().size(), results,
                next == 0L ? Optional.empty() : Optional.of(next),
                backend.status().lastFailure());
    }
}
