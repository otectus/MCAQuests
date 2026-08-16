package dev.otectus.mcaquests.quest.situation;

/**
 * What gameplay condition opens a {@link SituationDefinition} (the "Living Village" phase, 0.8.0).
 * Registry-driven via {@link SituationTriggerTypes}; the {@code "type"} field of a trigger object
 * dispatches to a leaf type's codec.
 *
 * <p>Detectors (0.8.0) translate world events into {@link TriggerSignal}s; {@link #signalType()} lets
 * {@link SituationManager} pre-filter definitions to the signal at hand, and {@link #matches} applies the
 * trigger's own parameters (thresholds, full-moon, ...). Player-relative {@code relation} filters are
 * applied later, at offer eligibility, not here.
 */
public interface SituationTrigger {

    /** The registry type for this trigger (drives codec dispatch). */
    SituationTriggerType<?> type();

    /** The single signal kind this trigger consumes. */
    SituationSignalType signalType();

    /**
     * Whether this trigger fires for {@code signal} (already known to be of {@link #signalType()}).
     * Pure and side-effect-free; must not dereference {@link TriggerSignal#level()} (it may be null).
     */
    boolean matches(TriggerSignal signal);
}
