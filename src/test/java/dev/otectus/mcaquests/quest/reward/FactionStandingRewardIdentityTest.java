package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.compat.KingdomIntegration;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import dev.otectus.mcaquests.state.KingdomBindingSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FactionStandingRewardIdentityTest {
    @Test
    void operationIsStablePerAcceptedCopyAndRewardSlot() {
        UUID instance = UUID.randomUUID();
        ResourceLocation quest = new ResourceLocation("pack", "diplomacy");
        ResourceLocation kingdom = new ResourceLocation("ultima_kingdoms", "lunari");
        UUID first = KingdomIntegration.rewardOperation(instance, quest, 0);
        assertEquals(first, KingdomIntegration.rewardOperation(instance, quest, 0));
        assertNotEquals(first, KingdomIntegration.rewardOperation(instance, quest, 1));
        assertNotEquals(first, KingdomIntegration.rewardOperation(UUID.randomUUID(), quest, 0));
    }

    @Test
    void independentRewardsAtSameSettlementRevisionUseReceiptsWithoutSourceCheckpoint() {
        ResourceLocation quest = new ResourceLocation("pack", "diplomacy");
        ResourceLocation kingdom = new ResourceLocation("ultima_kingdoms", "lunari");
        KingdomBindingSnapshot snapshot = new KingdomBindingSnapshot(UUID.randomUUID(), kingdom, 77L,
                new ResourceLocation("minecraft", "overworld"));
        QuestReward.RewardContext firstContext = new QuestReward.RewardContext(UUID.randomUUID(), Component.empty(),
                snapshot.dimension(), OptionalInt.of(3), quest, Optional.of(UUID.randomUUID()));
        QuestReward.RewardContext secondContext = new QuestReward.RewardContext(UUID.randomUUID(), Component.empty(),
                snapshot.dimension(), OptionalInt.of(3), quest, Optional.of(UUID.randomUUID()));
        FactionStandingReward.Prepared first = FactionStandingReward.prepareResolved(
                kingdom, firstContext, snapshot, 0).orElseThrow();
        FactionStandingReward.Prepared replay = FactionStandingReward.prepareResolved(
                kingdom, firstContext, snapshot, 0).orElseThrow();
        FactionStandingReward.Prepared second = FactionStandingReward.prepareResolved(
                kingdom, secondContext, snapshot, 0).orElseThrow();
        assertEquals(0L, first.sourceRevision());
        assertEquals(first.operationId(), replay.operationId(), "retry must replay the same durable receipt");
        assertNotEquals(first.operationId(), second.operationId(),
                "independent rewards must not collide at an unchanged settlement revision");
        FactionStandingReward.Prepared changedTargetRetry = FactionStandingReward.prepareResolved(
                new ResourceLocation("ultima_kingdoms", "madera"), firstContext, snapshot, 0).orElseThrow();
        assertEquals(first.operationId(), changedTargetRetry.operationId(),
                "a retry after reassignment must retain the acceptance+slot receipt identity");
    }
}
