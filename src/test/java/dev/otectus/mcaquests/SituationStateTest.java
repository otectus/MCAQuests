package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.quest.situation.state.SituationStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry-free tests for situation persistence and the synthetic-id backbone (0.8.0). */
class SituationStateTest {

    private static SituationInstance sampleInstance(UUID id, int villageId) {
        return new SituationInstance(id, ResourceLocation.parse("mcaquests:after_raid"), villageId,
                null, null, 1000L, 5000L, 42L, SituationStatus.OPEN);
    }

    @Test
    void instanceRoundTripsWithOptionalFields() {
        UUID id = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        UUID participant = UUID.randomUUID();
        SituationInstance instance = new SituationInstance(id, ResourceLocation.parse("mcaquests:cure_kin"),
                7, villager, family, 100L, 700L, 99L, SituationStatus.OPEN);
        instance.addParticipant(participant);
        instance.setStatus(SituationStatus.RESOLVED_SUCCESS);

        SituationInstance loaded = SituationInstance.load(instance.save());
        assertEquals(id, loaded.instanceId());
        assertEquals(ResourceLocation.parse("mcaquests:cure_kin"), loaded.defId());
        assertEquals(7, loaded.villageId());
        assertEquals(Optional.of(villager), loaded.villagerUuid());
        assertEquals(Optional.of(family), loaded.familyRootUuid());
        assertEquals(100L, loaded.openGameTime());
        assertEquals(700L, loaded.deadlineGameTime());
        assertEquals(99L, loaded.seed());
        assertEquals(SituationStatus.RESOLVED_SUCCESS, loaded.status());
        assertTrue(loaded.participants().contains(participant));
    }

    @Test
    void instanceRoundTripsWithoutOptionalFields() {
        SituationInstance instance = sampleInstance(UUID.randomUUID(), 3);
        SituationInstance loaded = SituationInstance.load(instance.save());
        assertEquals(Optional.empty(), loaded.villagerUuid());
        assertEquals(Optional.empty(), loaded.familyRootUuid());
        assertTrue(loaded.participants().isEmpty());
        assertTrue(loaded.isOpen());
    }

    @Test
    void deadlineHelpers() {
        SituationInstance instance = sampleInstance(UUID.randomUUID(), 1); // deadline 5000
        assertFalse(instance.isExpiredAt(4999L));
        assertTrue(instance.isExpiredAt(5000L));
        assertEquals(1L, instance.remainingTicks(4999L));
        assertEquals(0L, instance.remainingTicks(6000L));
    }

    @Test
    void savedDataRoundTripsInstancesAndCooldowns() {
        SituationSavedData data = new SituationSavedData();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        data.putInstance(sampleInstance(a, 12));
        data.putInstance(sampleInstance(b, 12));
        SituationInstance closed = sampleInstance(UUID.randomUUID(), 99);
        closed.setStatus(SituationStatus.EXPIRED);
        data.putInstance(closed);
        ResourceLocation defId = ResourceLocation.parse("mcaquests:after_raid");
        data.setCooldownUntil(12, defId, 8000L);

        assertEquals(2, data.openCountInVillage(12));
        assertEquals(0, data.openCountInVillage(99)); // EXPIRED is not open

        SituationSavedData loaded = SituationSavedData.load(data.save(new CompoundTag()));
        assertEquals(3, loaded.allInstances().size());
        assertEquals(2, loaded.openInstancesInVillage(12).size());
        assertEquals(8000L, loaded.cooldownUntil(12, defId));
        assertEquals(Long.MIN_VALUE, loaded.cooldownUntil(34, defId)); // unset -> sentinel
        assertTrue(loaded.getInstance(a).isPresent());
    }

    @Test
    void syntheticIdRoundTripsAndIsRecognisable() {
        ResourceLocation defId = ResourceLocation.parse("mymod:raid_help");
        ResourceLocation synthetic = SituationIds.syntheticId(defId);

        assertEquals(McaQuests.MOD_ID, synthetic.getNamespace());
        assertTrue(SituationIds.isSyntheticId(synthetic));
        assertFalse(SituationIds.isSyntheticId(ResourceLocation.parse("mcaquests:some_quest")));
        assertEquals(Optional.of(defId), SituationIds.sourceIdOf(synthetic));
    }

    @Test
    void sourceIdOfRejectsNonSyntheticIds() {
        assertEquals(Optional.empty(),
                SituationIds.sourceIdOf(ResourceLocation.parse("mcaquests:plain_quest")));
        assertEquals(Optional.empty(),
                SituationIds.sourceIdOf(ResourceLocation.parse("othermod:situation/x/y")));
    }
}
