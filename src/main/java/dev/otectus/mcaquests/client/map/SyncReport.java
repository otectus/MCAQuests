package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapMutationResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one reconciliation pass did, per backend, as data.
 *
 * <p>Held by the sync so {@code /mcaquestsclient waypoints status} can answer "why is there no
 * waypoint" without touching the map to find out. Every field is a value or an enum: the command
 * turns them into translated lines, and this record stays testable and side-effect free.
 *
 * @param worldEpoch       counts world and dimension changes, so a stale report is recognisable
 * @param guidanceRevision the {@code ClientGuidanceData} revision this pass reconciled
 */
public record SyncReport(long worldEpoch, long guidanceRevision, List<SyncReport.BackendReport> backends) {

    public SyncReport {
        backends = List.copyOf(backends);
    }

    /** An empty pass — no backends installed, or nothing to do yet. */
    public static SyncReport empty() {
        return new SyncReport(0L, 0L, List.of());
    }

    /**
     * One backend's share of the pass.
     *
     * @param enabled            whether config let it be reconciled at all
     * @param results            per waypoint key, what happened this pass. Keys the backend already
     *                           held and did not need touching appear as
     *                           {@link MapMutationResult#UNCHANGED}
     * @param nextRetryAtMillis  empty unless the backend is in backoff
     */
    public record BackendReport(
            String id,
            boolean enabled,
            boolean usable,
            int appliedCount,
            Map<String, MapMutationResult> results,
            Optional<Long> nextRetryAtMillis,
            Optional<MapBackendStatus.Failure> lastFailure) {

        public BackendReport {
            results = Map.copyOf(results);
        }
    }
}
