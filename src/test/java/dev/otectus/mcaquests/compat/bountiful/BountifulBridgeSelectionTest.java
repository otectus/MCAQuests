package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which bridge MCA: Quests runs against Bountiful, for every combination of the four facts that
 * decide it.
 *
 * <p>This is the part of the integration that is hardest to observe from inside a game and easiest to
 * get subtly wrong. All three bridges behave plausibly — quests are offered, or they are not — so the
 * wrong one does not crash, it just quietly stops a player's bounty quests from ever advancing, and
 * the report that comes back is "the quest is broken" rather than "the mode is wrong".
 *
 * <p>{@code select} is pure precisely so this can be asserted without Forge, a mod file or a world.
 */
class BountifulBridgeSelectionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** Stands in for a fully resolved manifest; selection never looks inside it. */
    private static BountifulBinding.Resolution nothingBound() {
        return BountifulBinding.absent();
    }

    @Test
    @DisplayName("Bountiful absent — the Noop bridge, and ABSENT rather than DISABLED")
    void absentModIsNoop() {
        BountifulBridge bridge = BountifulCompat.select(false, McaQuestsConfig.BountifulMode.AUTO,
                true, BountifulHookProbe.State.UNKNOWN, nothingBound());

        assertInstanceOf(NoopBountifulBridge.class, bridge);
        assertEquals(CompatStatus.ABSENT, bridge.status(),
                "\"not installed\" and \"switched off\" are different answers to the owner asking why "
                        + "their bounty quests never appear");
        for (BountifulBridge.Capability capability : BountifulBridge.Capability.values()) {
            assertFalse(bridge.has(capability), capability + " must be absent with no mod installed");
        }
    }

    @Test
    @DisplayName("installed, hook unavailable — the data-only bridge")
    void hookUnavailableIsDataOnly() {
        BountifulBridge bridge = BountifulCompat.select(true, McaQuestsConfig.BountifulMode.AUTO,
                false, BountifulHookProbe.State.UNKNOWN, nothingBound());

        assertInstanceOf(DataOnlyBountifulBridge.class, bridge);
        assertFalse(bridge instanceof HookedBountifulBridge);
        assertTrue(bridge.has(BountifulBridge.Capability.DATA_PACK),
                "our pools still load; it is only completion that cannot be observed");
        assertFalse(bridge.has(BountifulBridge.Capability.CASH_IN_HOOK));
    }

    @Test
    @DisplayName("installed, hook available — the hooked bridge")
    void hookAvailableIsHooked() {
        BountifulBridge bridge = BountifulCompat.select(true, McaQuestsConfig.BountifulMode.AUTO,
                true, BountifulHookProbe.State.UNKNOWN, nothingBound());

        assertInstanceOf(HookedBountifulBridge.class, bridge);
        assertTrue(bridge.has(BountifulBridge.Capability.CASH_IN_HOOK));
        assertTrue(bridge.has(BountifulBridge.Capability.DATA_PACK));
    }

    @Test
    @DisplayName("mode=OFF beats everything, and reports DISABLED")
    void offIsNoopEvenWithTheHookAvailable() {
        BountifulBridge bridge = BountifulCompat.select(true, McaQuestsConfig.BountifulMode.OFF,
                true, BountifulHookProbe.State.UNKNOWN, nothingBound());

        assertInstanceOf(NoopBountifulBridge.class, bridge);
        assertEquals(CompatStatus.DISABLED, bridge.status(),
                "an owner who switched it off should see that, not \"Bountiful is not installed\"");
        assertFalse(bridge.has(BountifulBridge.Capability.DATA_PACK),
                "off must be genuinely off: our pools must not reach Bountiful's loader either");
    }

    @Test
    @DisplayName("mode=DATA_ONLY beats an available hook")
    void dataOnlyIgnoresAWorkingHook() {
        BountifulBridge bridge = BountifulCompat.select(true, McaQuestsConfig.BountifulMode.DATA_ONLY,
                true, BountifulHookProbe.State.UNKNOWN, nothingBound());

        assertInstanceOf(DataOnlyBountifulBridge.class, bridge);
        assertFalse(bridge instanceof HookedBountifulBridge,
                "an owner who asked for no hook has asked for no hook, however well it would work");
        assertFalse(bridge.has(BountifulBridge.Capability.CASH_IN_HOOK));
    }

    @Test
    @DisplayName("a hook the plugin says failed falls back to data-only")
    void aFailedHookProbeFallsBackToDataOnly() {
        BountifulBridge bridge = BountifulCompat.select(true, McaQuestsConfig.BountifulMode.AUTO,
                true, BountifulHookProbe.State.FAILED, nothingBound());

        assertInstanceOf(DataOnlyBountifulBridge.class, bridge);
        assertFalse(bridge instanceof HookedBountifulBridge,
                "the bytes said the method was hookable and the hook still did not go in; offering "
                        + "bounty-completion quests now would offer quests nothing could advance");
        assertFalse(bridge.has(BountifulBridge.Capability.CASH_IN_HOOK));
        assertTrue(bridge.has(BountifulBridge.Capability.DATA_PACK),
                "the rest of the integration is unaffected by a hook that did not apply");
    }

    @Test
    @DisplayName("the hooked bridge's evidence starts believed and becomes confirmed")
    void hookEvidenceUpgradesOnlyWhenApplied() {
        HookedBountifulBridge bridge = (HookedBountifulBridge) BountifulCompat.select(
                true, McaQuestsConfig.BountifulMode.AUTO, true, BountifulHookProbe.State.UNKNOWN,
                nothingBound());

        assertFalse(bridge.hookApplied(), "byte-verified is not the same as applied");
        bridge.markHookApplied();
        assertTrue(bridge.hookApplied());
        bridge.markHookApplied();
        assertTrue(bridge.hookApplied(), "reporting twice must not undo it");
    }
}
