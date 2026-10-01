package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reward that threw is held on the player, survives a save, and is bounded (1.7.0). */
class HeldQuestRewardTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static HeldQuestReward held(int index) {
        return new HeldQuestReward(new ResourceLocation("test", "quest"), Optional.of(new UUID(1L, 2L)), index,
                "addon:broken", "0123456789abcdef", OptionalInt.of(7), 100L + index, "boom");
    }

    @Test
    @DisplayName("held rewards round-trip through the player's save")
    void roundTrip() {
        PlayerQuestData data = new PlayerQuestData();
        data.holdReward(held(2));
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(data.save());
        assertEquals(1, reloaded.heldRewards().size());
        assertEquals(held(2), reloaded.heldRewards().get(0));
    }

    @Test
    @DisplayName("the giver context a retry pays through survives the save (1.7.1)")
    void contextRoundTrip() {
        QuestReward.RewardContext context = new QuestReward.RewardContext(new UUID(3L, 4L),
                Component.literal("Anna"), new ResourceLocation("minecraft", "overworld"), OptionalInt.of(0),
                new ResourceLocation("test", "quest"), Optional.of(new UUID(1L, 2L)));
        HeldQuestReward withContext = new HeldQuestReward(new ResourceLocation("test", "quest"),
                Optional.of(new UUID(1L, 2L)), 1, "mcaquests:hearts", "0123456789abcdef", OptionalInt.empty(),
                100L, "boom", Optional.of(context));
        PlayerQuestData data = new PlayerQuestData();
        data.holdReward(withContext);
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(data.save());
        QuestReward.RewardContext loaded = reloaded.heldRewards().get(0).context().orElseThrow();
        assertEquals(context.giverUuid(), loaded.giverUuid());
        assertEquals("Anna", loaded.giverName().getString());
        assertEquals(context.dimension(), loaded.dimension());
        assertEquals(OptionalInt.of(0), loaded.villageId(), "village 0 is a real MCA village, not 'none'");
        assertEquals(context.questId(), loaded.questId());
        assertEquals(context.instance(), loaded.instance());
    }

    @Test
    @DisplayName("a reward held before 1.7.1 loads with no context and retries as it did")
    void legacyEntryHasNoContext() {
        PlayerQuestData data = new PlayerQuestData();
        data.holdReward(held(0));
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(data.save());
        assertTrue(reloaded.heldRewards().get(0).context().isEmpty());
    }

    @Test
    @DisplayName("a save with nothing held writes no held_rewards key")
    void absentWhenEmpty() {
        assertTrue(!new PlayerQuestData().save().contains("held_rewards"));
    }

    @Test
    @DisplayName("the list is bounded, oldest dropped first, and dismissal removes by position")
    void boundedAndDismissable() {
        PlayerQuestData data = new PlayerQuestData();
        for (int i = 0; i < HeldQuestReward.MAX_PER_PLAYER + 3; i++) {
            data.holdReward(held(i));
        }
        assertEquals(HeldQuestReward.MAX_PER_PLAYER, data.heldRewards().size());
        assertEquals(3, data.heldRewards().get(0).rewardIndex());
        assertEquals(3, data.removeHeldReward(0).orElseThrow().rewardIndex());
        assertTrue(data.removeHeldReward(500).isEmpty());
    }
}
