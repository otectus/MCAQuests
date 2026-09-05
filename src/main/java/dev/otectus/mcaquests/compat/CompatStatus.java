package dev.otectus.mcaquests.compat;

/**
 * How much of one optional mod a {@link CompatProvider} managed to reach.
 *
 * <p>The first four values mean exactly what {@link TownsteadStatus}' do, so the Townstead adapter is
 * a rename and nothing else. {@link #AMBIGUOUS} is the case that enum could not express: two
 * different mods claiming one mod id, where the honest answer is "something is installed and we
 * cannot say which".
 */
public enum CompatStatus {

    /** The mod is not installed. The normal, silent path — never a warning. */
    ABSENT,

    /** Installed, but the integration is switched off by config or nothing could be bound. */
    DISABLED,

    /** Some capabilities are available and some are not. */
    PARTIAL,

    /** Everything this provider declares is available. */
    FULL,

    /** Installed, but which build is installed could not be determined. Treated as degraded. */
    AMBIGUOUS
}
