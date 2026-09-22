package dev.otectus.mcaquests.compat.mapatlases;

import dev.otectus.mcaquests.compat.WaypointPresentation;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Points an integration publishes through {@code McaQuestsApi.publishExternalMapPoints} share the atlas
 * with quest destinations but are never owned by quest reconciliation.
 */
class ExternalAtlasPointsTest {

    static {
        // Level.OVERWORLD pulls in the registries; touching it before bootstrap fails the class for every
        // later test in the shared worker.
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("an external layer renders alongside quest points but quest reconciliation never removes it")
    void externalLayerRendersButIsNotOwnedByQuestReconciliation() {
        AtlasMarkerStore store = new AtlasMarkerStore();
        WaypointSpec quest = point("quest");
        WaypointSpec external = point("external/ultima/site");
        store.put(quest);
        store.replaceExternal("ultima_kingdoms", List.of(external));

        assertEquals(List.of("quest"), store.keys().stream().sorted().toList());
        assertEquals(2, store.snapshot().all().size());

        store.remove("quest");
        assertEquals(List.of(external), store.snapshot().all());

        store.replaceExternal("ultima_kingdoms", List.of());
        assertTrue(store.snapshot().all().isEmpty());
    }

    private static WaypointSpec point(String key) {
        return new WaypointSpec(key, new BlockPos(1, 64, 2), Level.OVERWORLD, key, GuidanceKind.STRUCTURE,
                WaypointSpec.Ownership.AUTOMATIC, WaypointPresentation.DEFAULT);
    }
}
