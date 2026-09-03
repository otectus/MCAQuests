package dev.otectus.mcaquests.client.map;

import dev.otectus.mcaquests.compat.BindingState;
import dev.otectus.mcaquests.compat.ClearCause;
import dev.otectus.mcaquests.compat.MapBackendCapabilities;
import dev.otectus.mcaquests.compat.MapBackendStatus;
import dev.otectus.mcaquests.compat.MapMutationResult;
import dev.otectus.mcaquests.compat.MapWaypointBackend;
import dev.otectus.mcaquests.compat.PinSupport;
import dev.otectus.mcaquests.compat.ProbeStep;
import dev.otectus.mcaquests.compat.WaypointSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A map mod that does exactly what the test tells it to.
 *
 * <p>The reconciler's whole job is deciding what to call and how to react to the answer, and neither
 * real backend can be asked those questions without a mod jar nobody can put on a Maven. So the
 * outcomes are scripted: {@link #scriptApply}/{@link #scriptWithdraw} queue results for the next
 * calls, and everything else behaves like a correct backend — applied state changes only when a call
 * succeeds, which is the contract the real ones are held to.
 */
final class FakeMapWaypointBackend implements MapWaypointBackend {

    private final String id;
    private final MapBackendCapabilities capabilities;

    private final Map<String, WaypointSpec> applied = new LinkedHashMap<>();
    private final Deque<MapMutationResult> scriptedApplies = new ArrayDeque<>();
    private final Deque<MapMutationResult> scriptedWithdrawals = new ArrayDeque<>();

    /** Every call made, in order, as {@code verb:key}. The assertion surface for "did nothing". */
    private final List<String> calls = new ArrayList<>();
    private final List<ClearCause> clears = new ArrayList<>();
    private final List<WaypointSpec> pins = new ArrayList<>();

    private boolean usable = true;
    private MapBackendStatus.Failure lastFailure;

    FakeMapWaypointBackend(String id) {
        this(id, new MapBackendCapabilities(true, PinSupport.PERSISTENT, false));
    }

    FakeMapWaypointBackend(String id, MapBackendCapabilities capabilities) {
        this.id = id;
        this.capabilities = capabilities;
    }

    /** Queues the result of the next {@link #apply}; unscripted calls succeed. */
    FakeMapWaypointBackend scriptApply(MapMutationResult... results) {
        for (MapMutationResult result : results) {
            scriptedApplies.add(result);
        }
        return this;
    }

    /** Queues the result of the next {@link #withdraw}; unscripted calls succeed. */
    FakeMapWaypointBackend scriptWithdraw(MapMutationResult... results) {
        for (MapMutationResult result : results) {
            scriptedWithdrawals.add(result);
        }
        return this;
    }

    FakeMapWaypointBackend usable(boolean value) {
        this.usable = value;
        return this;
    }

    List<String> calls() {
        return calls;
    }

    List<ClearCause> clears() {
        return clears;
    }

    List<WaypointSpec> pins() {
        return pins;
    }

    Map<String, WaypointSpec> appliedSpecs() {
        return applied;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<String> modVersion() {
        return Optional.of("test");
    }

    @Override
    public MapBackendCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public boolean isUsable() {
        return usable;
    }

    @Override
    public Set<String> appliedKeys() {
        return Set.copyOf(applied.keySet());
    }

    @Override
    public MapMutationResult apply(WaypointSpec spec) {
        calls.add("apply:" + spec.key());
        if (spec.equals(applied.get(spec.key()))) {
            return MapMutationResult.UNCHANGED;
        }
        MapMutationResult scripted = scriptedApplies.poll();
        if (scripted != null && scripted != MapMutationResult.APPLIED) {
            fail(scripted);
            return scripted;
        }
        applied.put(spec.key(), spec);
        return MapMutationResult.APPLIED;
    }

    @Override
    public MapMutationResult withdraw(String key) {
        calls.add("withdraw:" + key);
        if (!applied.containsKey(key)) {
            return MapMutationResult.UNCHANGED;
        }
        MapMutationResult scripted = scriptedWithdrawals.poll();
        if (scripted != null && scripted != MapMutationResult.APPLIED) {
            fail(scripted);
            return scripted;
        }
        applied.remove(key);
        return MapMutationResult.APPLIED;
    }

    @Override
    public void clearAutomatic(ClearCause cause) {
        calls.add("clear:" + cause);
        clears.add(cause);
        applied.clear();
    }

    @Override
    public MapMutationResult pin(WaypointSpec spec) {
        calls.add("pin:" + spec.key());
        if (capabilities.pins() == PinSupport.NONE) {
            return MapMutationResult.UNSUPPORTED;
        }
        pins.add(spec);
        return MapMutationResult.APPLIED;
    }

    @Override
    public MapBackendStatus status() {
        return new MapBackendStatus(id, usable ? BindingState.BOUND : BindingState.PARTIAL,
                modVersion(), capabilities, List.of(), applied.size(),
                Optional.ofNullable(lastFailure));
    }

    @Override
    public List<ProbeStep> probe() {
        return List.of(ProbeStep.passed("fake"));
    }

    @Override
    public void resetEpoch() {
        calls.add("resetEpoch");
        applied.clear();
    }

    private void fail(MapMutationResult result) {
        lastFailure = new MapBackendStatus.Failure(id + "/scripted", result, Optional.of("scripted"));
    }
}
