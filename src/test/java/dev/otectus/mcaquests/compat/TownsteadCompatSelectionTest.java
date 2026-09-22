package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which bridge binds which Townstead. The one rule that matters: a Townstead that publishes its API
 * is never reached by reflection, because the reflective binding was written against 0.7.x
 * internals and would bypass the write policy the API enforces.
 */
class TownsteadCompatSelectionTest {

    @Test
    @DisplayName("Townstead 0.7.x, which has no api.v1, keeps the reflective bridge")
    void legacyIsReflective() {
        assertEquals(TownsteadCompat.Binding.REFLECTIVE, TownsteadCompat.chooseBinding(false, true));
        assertEquals(TownsteadCompat.Binding.REFLECTIVE, TownsteadCompat.chooseBinding(false, false));
    }

    @Test
    @DisplayName("Townstead 0.8 with the typed adapter present binds through the API")
    void apiIsTyped() {
        assertEquals(TownsteadCompat.Binding.TYPED, TownsteadCompat.chooseBinding(true, true));
    }

    @Test
    @DisplayName("Townstead 0.8 in a reflective-only build is disabled, never bound by reflection")
    void apiWithoutAdapterIsDisabled() {
        assertEquals(TownsteadCompat.Binding.DISABLED_NO_ADAPTER, TownsteadCompat.chooseBinding(true, false));
    }

    @Test
    @DisplayName("a disabled bridge refuses every write and reports why")
    void disabledBridgeRefuses() {
        DisabledTownsteadBridge bridge = new DisabledTownsteadBridge("0.8.0", "no adapter");
        assertEquals(TownsteadStatus.DISABLED, bridge.status());
        assertEquals(0, bridge.capabilities().size());
        assertEquals(TownsteadMutationResult.Reason.CAPABILITY_MISSING,
                bridge.awardProfessionXp(null, "minecraft:farmer", 10, true).reason());
        assertEquals("disabled: no adapter", bridge.bindingPath());
        assertEquals("0.8.0", bridge.detectedVersion());
    }
}
