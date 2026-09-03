package dev.otectus.mcaquests.compat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One mapping mod, as the rest of this mod sees it.
 *
 * <p>Replaces the old single {@code MapWaypointBridge} composite. The composite fanned every call out
 * to both mods and collapsed the answers into a {@code void}: JourneyMap succeeding hid Xaero failing,
 * and neither could be retried because nothing recorded that anything had gone wrong. Here each mod is
 * its own backend, owns its own applied state, and answers for itself; {@code client/map} holds the
 * registry and the one reconciler that drives them.
 *
 * <h2>What may appear in this interface</h2>
 *
 * <p>Vanilla and first-party types only. No {@code net.minecraft.client}, because two of the three
 * implementations live outside {@code client/} and must stay loadable on a dedicated server long
 * enough to be absent, and no third-party type, because naming one is the linkage the whole
 * arrangement exists to avoid. Anything a backend would need the client for — chiefly "which dimension
 * is the player in" — is expressed as a capability instead and answered by the reconciler.
 *
 * <h2>Applied state belongs to the backend</h2>
 *
 * <p>{@link #appliedKeys()} is the truth the reconciler diffs against, and a backend may only add to it
 * once a call has actually returned. That is the fix for the defect where the sync recorded what it
 * intended before the map had agreed to it, and then never tried again because its own record already
 * said the waypoint was there.
 */
public interface MapWaypointBackend {

    /** Stable id: {@code journeymap}, {@code xaero}. The registry key, and the config key. */
    String id();

    /** The installed mod's version, for diagnostics. Empty when it cannot be determined. */
    Optional<String> modVersion();

    MapBackendCapabilities capabilities();

    /**
     * Whether calls are worth making at all — the mod is installed and everything bound.
     *
     * <p>Distinct from readiness: a usable backend may still answer {@link MapMutationResult#RETRY_LATER}
     * for as long as the map has not built its session.
     */
    boolean isUsable();

    /** The automatic waypoints this backend believes are on the map. Never includes pins. */
    Set<String> appliedKeys();

    /** Creates, moves or re-themes one automatic waypoint. {@link MapMutationResult#UNCHANGED} costs nothing. */
    MapMutationResult apply(WaypointSpec spec);

    /** Removes one automatic waypoint. Unknown keys are {@link MapMutationResult#UNCHANGED}. */
    MapMutationResult withdraw(String key);

    /**
     * Removes every automatic waypoint, and only those.
     *
     * <p>Never a "remove everything this mod id owns": that is what deleted players' pins.
     */
    void clearAutomatic(ClearCause cause);

    /**
     * Drops a waypoint the player owns and this mod never removes.
     *
     * <p>{@link MapBackendCapabilities#pins()} says how long it will last. A backend that cannot do
     * this at all answers {@link MapMutationResult#UNSUPPORTED}.
     */
    MapMutationResult pin(WaypointSpec spec);

    /** A snapshot for diagnostics. Reads only; safe to call at any time. */
    MapBackendStatus status();

    /**
     * A self-test that <b>writes</b>: it proves the whole chain by using it.
     *
     * <p>Separate from {@link #status()} for that reason, and run only when an operator asks. Both
     * mods can decline a waypoint without throwing, and from inside the game that silence is
     * indistinguishable from having no minimap installed at all.
     */
    List<ProbeStep> probe();

    /**
     * Forgets applied state without touching the map, because the map that held it is gone.
     *
     * <p>The counterpart of {@link #clearAutomatic}: that one removes waypoints, this one accepts that
     * they have already been removed by somebody else — a world change, a logout — and stops the
     * backend from believing in waypoints that no longer exist.
     */
    void resetEpoch();
}
