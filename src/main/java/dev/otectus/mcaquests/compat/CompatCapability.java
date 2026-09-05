package dev.otectus.mcaquests.compat;

/**
 * One independently-answerable fact about an optional mod: a piece of content, a bound hook, or a
 * readable slice of state.
 *
 * <p>The unit datapacks gate on through {@code mcaquests:compat_capability}, so ids are part of the
 * pack contract and must not be renamed without a migration note. Ids are lowercase and dotted;
 * {@code detail} is free text for the status command only and is never parsed.
 *
 * @param id       the capability id, unique within its provider
 * @param present  whether it is available right now
 * @param evidence how that was decided — see {@link CapabilityEvidence}
 * @param detail   one short human-readable line for diagnostics, or the empty string
 */
public record CompatCapability(String id, boolean present, CapabilityEvidence evidence, String detail) {

    public CompatCapability(String id, boolean present, CapabilityEvidence evidence) {
        this(id, present, evidence, "");
    }
}
