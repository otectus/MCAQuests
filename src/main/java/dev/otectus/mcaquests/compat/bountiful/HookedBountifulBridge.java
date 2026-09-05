package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;

import java.util.List;

/**
 * The bridge for an installation where a bounty cash-in can be observed.
 *
 * <p>Everything {@code DataOnlyBountifulBridge} does, plus {@link Capability#CASH_IN_HOOK}. The
 * difference between the two is one fact — whether Bountiful's
 * {@code BountyStack.tryCashIn} is present with the exact shape our hook needs — read out of the mod
 * file's bytes, never by loading the class.
 *
 * <p><b>Byte-verified is not the same as applied.</b> The bytes say the hook <em>can</em> go in;
 * only the hook itself can say it <em>did</em>, and that is a later step. So the capability's
 * evidence starts at {@link CapabilityEvidence#FLAVOR_DECLARED} — believed, not verified — and
 * becomes {@link CapabilityEvidence#ADAPTER_CONFIRMED} when {@link #markHookApplied()} is called.
 * Reporting the weaker evidence in the meantime is the honest answer, and it is what lets
 * {@code compat bountiful status} distinguish "should work" from "is working" in a bug report.
 */
public final class HookedBountifulBridge extends DataOnlyBountifulBridge {

    private volatile boolean hookApplied;

    private HookedBountifulBridge(BountifulBinding.Resolution resolution) {
        super(resolution);
    }

    public static HookedBountifulBridge of(BountifulBinding.Resolution resolution) {
        return new HookedBountifulBridge(resolution);
    }

    @Override
    public boolean has(Capability capability) {
        return capability == Capability.CASH_IN_HOOK || super.has(capability);
    }

    @Override
    public List<CompatCapability> capabilities() {
        List<CompatCapability> base = super.capabilities();
        List<CompatCapability> out = new java.util.ArrayList<>(base.size());
        for (CompatCapability capability : base) {
            out.add(capability.id().equals(Capability.CASH_IN_HOOK.id())
                    ? new CompatCapability(capability.id(), true, hookEvidence())
                    : capability);
        }
        return List.copyOf(out);
    }

    /**
     * Called once the cash-in hook has actually been installed, which upgrades the capability's
     * evidence from "the bytes allow it" to "the adapter is in place".
     *
     * <p>Idempotent and safe from any thread: the hook reports itself exactly once, but on a
     * dedicated server that report and a status command can race, and a wrong answer here is a
     * diagnostic that lies.
     */
    public void markHookApplied() {
        if (!hookApplied) {
            hookApplied = true;
            McaQuests.LOGGER.debug("[MCA: Quests] Bountiful cash-in hook reported applied.");
        }
    }

    /** Whether the hook has reported itself installed, rather than merely being possible. */
    public boolean hookApplied() {
        return hookApplied;
    }

    /**
     * Handed to {@link BountifulCompat}, which owns the list.
     *
     * <p>It used to live here, which was wrong for a reason that only shows up on the second
     * {@code /reload}: a bridge is rebuilt on every re-probe, so a list held by one is discarded with
     * it, and a listener registered once at start-up would quietly stop being called. The listeners
     * outlive the bridges because they belong to the mod, not to the installation.
     */
    @Override
    public void addCompletionListener(BountifulCompletionListener listener) {
        BountifulCompat.addCompletionListener(listener);
    }

    /** Weaker than a bound handle until the hook says otherwise; see the class javadoc. */
    private CapabilityEvidence hookEvidence() {
        return hookApplied ? CapabilityEvidence.ADAPTER_CONFIRMED : CapabilityEvidence.FLAVOR_DECLARED;
    }
}
