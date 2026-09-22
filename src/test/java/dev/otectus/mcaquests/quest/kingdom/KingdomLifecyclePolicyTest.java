package dev.otectus.mcaquests.quest.kingdom;

import dev.otectus.mcaquests.state.KingdomBindingSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KingdomLifecyclePolicyTest {
    @Test
    void reassignmentComparisonIgnoresRevisionButNotPoliticalIdentity() {
        UUID settlement = UUID.randomUUID();
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");
        KingdomBindingSnapshot accepted = new KingdomBindingSnapshot(settlement,
                new ResourceLocation("ultima_kingdoms", "lunari"), 1L, dimension);
        assertTrue(KingdomQuestLifecycle.samePoliticalContext(accepted,
                new KingdomBindingSnapshot(settlement, accepted.kingdomId(), 99L, dimension)),
                "unrelated settlement edits must not fail an accepted quest");
        assertFalse(KingdomQuestLifecycle.samePoliticalContext(accepted,
                new KingdomBindingSnapshot(settlement, new ResourceLocation("ultima_kingdoms", "madera"),
                        100L, dimension)));
        assertTrue(KingdomQuestLifecycle.compareChange(Optional.of(accepted), Optional.empty())
                        == KingdomQuestLifecycle.ActiveStatus.WAIT,
                "an unloaded giver or temporarily absent optional API must suspend, never cancel");
        assertTrue(KingdomQuestLifecycle.compareChange(Optional.of(accepted), Optional.of(
                        new KingdomBindingSnapshot(settlement,
                                new ResourceLocation("ultima_kingdoms", "madera"), 100L, dimension)))
                        == KingdomQuestLifecycle.ActiveStatus.FAIL_KINGDOM,
                "a positively resolved reassignment must fail an opted-in quest");
    }

    @Test
    void boundStandingPersistsTheCapturedSettlementCommunity() {
        KingdomBindingSnapshot captured = new KingdomBindingSnapshot(UUID.randomUUID(),
                new ResourceLocation("ultima_kingdoms", "lunari"), 3L,
                new ResourceLocation("minecraft", "overworld"),
                Optional.of(new ResourceLocation("minecraft", "the_nether")),
                java.util.OptionalInt.of(7));
        KingdomBindingSnapshot restored = KingdomBindingSnapshot.load(captured.save()).orElseThrow();
        assertEquals(new ResourceLocation("minecraft", "the_nether"), restored.localDimension().orElseThrow());
        assertEquals(7, restored.localVillageId().orElseThrow());
    }
}
