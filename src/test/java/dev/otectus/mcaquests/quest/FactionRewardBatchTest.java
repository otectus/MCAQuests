package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.quest.reward.FactionStandingReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionRewardBatchTest {
    @Test
    void rejectedBatchStopsCompletionAndRetriesTheSameRewardSlots() {
        List<QuestReward> rewards = List.of(
                new FactionStandingReward(2, Optional.empty(), "giver_residence", Optional.empty(), true),
                new FactionStandingReward(3, Optional.empty(), "giver_residence", Optional.empty(), true));
        List<Integer> firstAttempt = new ArrayList<>();
        assertFalse(QuestManager.applyFactionRewardBatch(rewards, index -> {
            firstAttempt.add(index);
            return index == 0;
        }));
        assertEquals(List.of(0, 1), firstAttempt);

        List<Integer> retry = new ArrayList<>();
        assertTrue(QuestManager.applyFactionRewardBatch(rewards, index -> {
            retry.add(index);
            return true;
        }));
        assertEquals(List.of(0, 1), retry);
    }
}
