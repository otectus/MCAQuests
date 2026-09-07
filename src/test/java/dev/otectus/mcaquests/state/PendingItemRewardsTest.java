package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingItemRewardsTest {
    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("missing_mod", "currency");

    @Test void independentAwardsShareOneTickBudget() {
        PendingItemRewards pending = new PendingItemRewards();
        assertEquals(10, pending.takeStackBudget(100, 10, 16));
        assertEquals(6, pending.takeStackBudget(100, 16, 16));
        assertEquals(0, pending.takeStackBudget(100, 16, 16));
        assertEquals(16, pending.takeStackBudget(101, 16, 16));
    }

    @Test void repeatedHugeAwardsKeepExactOwedCountThroughReload() {
        PendingItemRewards pending = new PendingItemRewards();
        pending.add(ITEM, Integer.MAX_VALUE);
        pending.add(ITEM, Integer.MAX_VALUE);
        pending.delivered(ITEM, 64);
        PendingItemRewards loaded = new PendingItemRewards();
        loaded.load(pending.save());
        assertEquals(2L * Integer.MAX_VALUE - 64, loaded.snapshot().get(ITEM));
        loaded.delivered(ITEM, 2L * Integer.MAX_VALUE - 64);
        assertTrue(loaded.isEmpty());
    }

    @Test void unknownIdsSurvivePlayerCloneAndCloneIsIndependent() {
        PlayerQuestData original = new PlayerQuestData();
        original.pendingItems().add(ITEM, 3000);
        PlayerQuestData clone = new PlayerQuestData();
        clone.copyFrom(original);
        clone.pendingItems().delivered(ITEM, 1000);
        assertEquals(3000L, original.pendingItems().snapshot().get(ITEM));
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(clone.save());
        assertEquals(2000L, reloaded.pendingItems().snapshot().get(ITEM));
    }

    @Test void oldSavesAndMalformedAmountsDoNotCreatePayouts() {
        PlayerQuestData old = new PlayerQuestData();
        old.load(new CompoundTag());
        assertTrue(old.pendingItems().isEmpty());
        CompoundTag malformed = new CompoundTag();
        malformed.putLong(ITEM.toString(), -1);
        malformed.putLong("INVALID ID", 10);
        old.pendingItems().load(malformed);
        assertTrue(old.pendingItems().isEmpty());
    }
}
