package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingdomBindingPersistenceTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void politicalAndCivicBindingsSurviveQuestRestart() {
        ActiveQuest quest = new ActiveQuest(new ResourceLocation("pack", "bound"), UUID.randomUUID(),
                Component.literal("Giver"), null, new ResourceLocation("minecraft", "overworld"), 42L,
                OptionalLong.of(84L), OptionalInt.of(7), List.of(new ObjectiveProgress()), null, null);
        KingdomBindingSnapshot kingdom = new KingdomBindingSnapshot(UUID.randomUUID(),
                new ResourceLocation("ultima_kingdoms", "lunari"), 19L,
                new ResourceLocation("minecraft", "overworld"));
        CivicBuildingBinding building = new CivicBuildingBinding(UUID.randomUUID(), kingdom.settlementId(),
                kingdom.dimension(), 7, 12, "library", "library_l2");
        quest.bindKingdom(kingdom);
        quest.bindCivicBuilding(building);

        ActiveQuest restored = ActiveQuest.load(quest.save());
        assertEquals(kingdom, restored.kingdomBinding().orElseThrow());
        assertEquals(building, restored.civicBuildingBinding().orElseThrow());
    }

    @Test
    void sameFamilyRebindReplacesOnlyThePersistedIdentifierSnapshot() {
        ActiveQuest quest = ActiveQuest.create(new ResourceLocation("pack", "rebind"), UUID.randomUUID(),
                Component.empty(), null, new ResourceLocation("minecraft", "overworld"), 0L, 0, null);
        UUID settlement = UUID.randomUUID();
        quest.bindCivicBuilding(new CivicBuildingBinding(UUID.randomUUID(), settlement, quest.dimension(),
                2, 10, "smithy", "smithy_l1"));
        CivicBuildingBinding rebound = new CivicBuildingBinding(UUID.randomUUID(), settlement, quest.dimension(),
                2, 14, "smithy", "smithy_l2");
        quest.bindCivicBuilding(rebound);
        assertEquals(rebound, ActiveQuest.load(quest.save()).civicBuildingBinding().orElseThrow());
    }
}
