package dev.otectus.mcaquests.compat;

/**
 * How a {@link CompatCapability} was decided, so a diagnostic can say more than yes/no.
 *
 * <p>The distinction matters when two builds of one mod share a mod id. "The registry contains this
 * entity" is proof; "the mod's own class layout says it is that flavour" is a declaration, and a
 * declaration can be wrong. Reporting which one answered is what lets a bug report be about the
 * right thing.
 */
public enum CapabilityEvidence {

    /** A vanilla registry was asked and answered. The strongest evidence available. */
    REGISTRY_CONFIRMED,

    /** The mod declared it — a flavour or version probe, believed but not verified against content. */
    FLAVOR_DECLARED,

    /** An MCA: Quests adapter bound the thing it needs (a handle resolved, a hook applied). */
    ADAPTER_CONFIRMED
}
