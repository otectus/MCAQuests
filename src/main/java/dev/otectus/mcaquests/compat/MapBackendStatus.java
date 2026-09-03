package dev.otectus.mcaquests.compat;

import java.util.List;
import java.util.Optional;

/**
 * Everything a diagnostic can say about one backend without touching the map.
 *
 * <p>Read-only by contract, so that a status command is safe to run at any time — which the old probe
 * was not: it added a waypoint in order to find out whether it could.
 *
 * @param modVersion     the installed map mod's version, for a bug report; empty when it is absent
 * @param missingMembers the manifest entries that did not bind, which is the whole diagnosis when the
 *                       state is {@link BindingState#PARTIAL}
 * @param appliedCount   how many automatic waypoints the backend believes it has on the map
 */
public record MapBackendStatus(
        String id,
        BindingState binding,
        Optional<String> modVersion,
        MapBackendCapabilities capabilities,
        List<String> missingMembers,
        int appliedCount,
        Optional<MapBackendStatus.Failure> lastFailure) {

    public MapBackendStatus {
        missingMembers = List.copyOf(missingMembers);
    }

    /**
     * The last thing that went wrong, kept so it can be reported once instead of once per tick.
     *
     * @param fingerprint identifies the <em>cause</em> rather than the occurrence: the member that
     *                    failed and the exception it threw. One warning is logged per backend per
     *                    fingerprint, so a broken binding costs one line in the log instead of one
     *                    every frame
     * @param message     the exception's own message, or empty. Never a sentence this mod wrote — the
     *                    presentation layer supplies the words
     */
    public record Failure(String fingerprint, MapMutationResult result, Optional<String> message) {
    }
}
