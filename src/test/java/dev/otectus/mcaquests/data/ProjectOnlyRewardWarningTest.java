package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.quest.reward.HeartsWithParticipantsReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.UnlockReward;
import dev.otectus.mcaquests.quest.reward.XpReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A project-only reward in a quest pays nothing, so the loader says so (1.7.1). */
class ProjectOnlyRewardWarningTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestDefinition quest(String path, List<QuestReward> rewards) {
        ResourceLocation id = new ResourceLocation("testpack", path);
        return new QuestDefinition(id, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE), Map.of(),
                List.of(new ItemDeliveryObjective(Items.BREAD, 1, true)), rewards, TurnInSpec.DEFAULT,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE,
                QuestReputationBlock.NONE);
    }

    @Test
    @DisplayName("unlock and hearts_with_participants are named; ordinary rewards are not")
    void projectOnlyRewardsWarn() {
        QuestDefinition misplaced = quest("misplaced", List.of(new XpReward(5),
                new UnlockReward(new ResourceLocation("testpack", "next_project")),
                new HeartsWithParticipantsReward(10, false)));
        QuestDefinition ordinary = quest("ordinary", List.of(new XpReward(5)));
        List<String> warnings = new ArrayList<>();
        QuestDataLoader.warnOnProjectOnlyRewards(Map.of(misplaced.id(), misplaced, ordinary.id(), ordinary),
                warnings);

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("reward 1") && warnings.get(0).contains("mcaquests:unlock"));
        assertTrue(warnings.get(1).contains("reward 2") && warnings.get(1).contains("mcaquests:hearts_with_participants"));
    }
}
