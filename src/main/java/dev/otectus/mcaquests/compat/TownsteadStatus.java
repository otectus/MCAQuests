package dev.otectus.mcaquests.compat;

/**
 * How much of Townstead this build managed to bind (Townstead spec §3.2).
 *
 * <p>Reported by {@link TownsteadBridge#status()} and by
 * {@code /mcaquests compat townstead status}. Only {@link #FULL} is a supported configuration;
 * {@link #PARTIAL} is graceful degradation so a point release that moved one internal method
 * disables one feature rather than the mod.
 */
public enum TownsteadStatus {

    /** Townstead is not installed. The normal, silent path — never a warning. */
    ABSENT,

    /** Every capability in {@link TownsteadCapability} bound. */
    FULL,

    /** The public read facade bound, but at least one capability did not. */
    PARTIAL,

    /** Townstead is installed but even its baseline public facade could not be bound. */
    DISABLED
}
