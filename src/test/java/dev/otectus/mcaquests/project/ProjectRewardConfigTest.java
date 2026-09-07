package dev.otectus.mcaquests.project;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.reward.CommandReward;
import dev.otectus.mcaquests.quest.reward.XpReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectRewardConfigTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test void projectCommandsRequireTheirOwnOptInEvenWhenGlobalCommandsAreAllowed() {
        CommentedConfig config = CommentedConfig.inMemory();
        McaQuestsConfig.COMMON_SPEC.correct(config);
        McaQuestsConfig.COMMON_SPEC.acceptConfig(config);
        try {
            McaQuestsConfig.COMMON.allowCommandRewards.set(true);
            McaQuestsConfig.COMMON.allowProjectCommandRewards.set(false);
            assertFalse(ProjectRewardDistributor.rewardEnabled(new CommandReward("say test")));
            assertTrue(ProjectRewardDistributor.rewardEnabled(new XpReward(5)));
            McaQuestsConfig.COMMON.allowProjectCommandRewards.set(true);
            assertTrue(ProjectRewardDistributor.rewardEnabled(new CommandReward("say test")));
        } finally {
            McaQuestsConfig.COMMON.allowCommandRewards.set(false);
            McaQuestsConfig.COMMON.allowProjectCommandRewards.set(false);
        }
    }
}
