package dev.otectus.mcaquests.compat.townstead;

import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TownsteadMutationValidationTest {
    @Test
    @SuppressWarnings("unchecked")
    void reloadDiscardsCachedProfessionDefinitions() throws Exception {
        java.lang.reflect.Field field = TownsteadHandles.class.getDeclaredField("TRACKS");
        field.setAccessible(true);
        java.util.Map<String, TownsteadProfessionTrackView> tracks =
                (java.util.Map<String, TownsteadProfessionTrackView>) field.get(null);
        tracks.put("example:farmer", TownsteadProfessionTrackView.none("example:farmer"));
        TownsteadHandles.invalidateDataCaches();
        assertTrue(tracks.isEmpty(), "the next world/reload must read the new definition");
    }

    @Test
    void professionAliasesMatchWholePaths() {
        assertTrue(TownsteadHandles.sameProfessionPath("minecraft:farmer", "farmer"));
        assertTrue(TownsteadHandles.sameProfessionPath("mca:FARMER", "farmer"));
        assertFalse(TownsteadHandles.sameProfessionPath("example:beet_farmer", "farmer"));
        assertFalse(TownsteadHandles.sameProfessionPath("example:outlaw", "law"));
        assertFalse(TownsteadHandles.sameProfessionPath(null, "farmer"));
    }

    @Test
    void malformedNeedMutationsFailBeforeAnyThirdPartySetterRuns() {
        for (NeedMutation mutation : new NeedMutation[] {null,
                NeedMutation.delta(NeedMutation.Need.SATURATION, Double.NaN),
                NeedMutation.target(NeedMutation.Need.HUNGER, Double.POSITIVE_INFINITY),
                NeedMutation.delta(null, 1), new NeedMutation(NeedMutation.Need.ENERGY, null, 1)}) {
            assertEquals(TownsteadMutationResult.Reason.INVALID_VALUE,
                    TownsteadHandles.changeNeeds(null, mutation).reason());
        }
    }
}
