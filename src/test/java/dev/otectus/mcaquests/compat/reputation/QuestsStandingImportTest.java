package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.state.VillageStanding;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestsStandingImportTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void currentPlayerSnapshotWinsOverRetainedSharedTagsAndPreservesDimensions() {
        VillageStanding standing = new VillageStanding();
        standing.setScore(PLAYER, OVERWORLD, 3, 47);
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("test", "world/nested");
        standing.setScore(PLAYER, custom, 3, -5);
        var selected = QuestsStandingImport.select(standing, PLAYER, Map.of("v:3", 900),
                Map.of(), true, Set.of());
        assertEquals(2, selected.size());
        assertEquals(47, selected.get(new QuestsStandingImport.Community(OVERWORLD, 3)).score());
        assertEquals(-5, selected.get(new QuestsStandingImport.Community(custom, 3)).score());
    }

    @Test
    void canonicalMirrorCannotBeReimportedAsAnAdditionalBaseline() {
        VillageStanding standing = new VillageStanding();
        standing.setScore(PLAYER, OVERWORLD, 3, 80);
        assertTrue(QuestsStandingImport.select(standing, PLAYER, Map.of("v:3", 40), Map.of(),
                true, Set.of("minecraft:overworld/3")).isEmpty());
    }

    @Test
    void sharedHistoryRequiresEligibilityAndSkipsInvalidScopes() {
        Map<String, Integer> old = Map.of("v:3", 20, "v:-1", 30, "capital:1", 90, "v:broken", 80);
        assertTrue(QuestsStandingImport.select(new VillageStanding(), PLAYER, old, Map.of(),
                false, Set.of()).isEmpty());
        var selected = QuestsStandingImport.select(new VillageStanding(), PLAYER, old, Map.of(),
                true, Set.of());
        assertEquals(1, selected.size());
        assertEquals(20, selected.values().iterator().next().score());
    }

    @Test
    void zeroScoreDoesNotDiscardEarnedTitlesAndTierHighWater() {
        VillageStanding standing = new VillageStanding();
        ResourceLocation title = ResourceLocation.fromNamespaceAndPath("mcaquests", "trusted");
        standing.grantVillageTitle(PLAYER, OVERWORLD, 3, title);
        standing.setTierHighWater(PLAYER, ReputationTiers.DEFAULT_ID, OVERWORLD, 3, "trusted");
        var selected = QuestsStandingImport.select(standing, PLAYER, Map.of(), Map.of(), false, Set.of());
        var snapshot = selected.get(new QuestsStandingImport.Community(OVERWORLD, 3));
        assertNotNull(snapshot);
        assertEquals(0, snapshot.score());
        assertEquals("trusted", snapshot.highWater().orElseThrow());
        assertEquals(Set.of(title), snapshot.titles());
    }
}
