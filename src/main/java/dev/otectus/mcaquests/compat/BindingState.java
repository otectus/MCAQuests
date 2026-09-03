package dev.otectus.mcaquests.compat;

/**
 * How well a backend is attached to its map mod right now.
 *
 * <p>Four states because the three non-working ones are not the same failure and the operator's next
 * step differs for each: an absent mod is not a problem at all, a partial binding is a mod version
 * this build does not know, and a not-ready backend is only the clock.
 */
public enum BindingState {
    /** The mod is not installed. The ordinary case, and silent. */
    ABSENT,
    /** Installed, but some member the integration needs did not resolve. */
    PARTIAL,
    /** Everything resolved, but the map has not built the object we write into yet. */
    NOT_READY,
    /** Resolved, and ready to be written to. */
    BOUND
}
