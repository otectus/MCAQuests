package dev.otectus.mcaquests.quest.reward;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RewardBoundsTest {
    static { TestBootstrap.ensureBootstrapped(); }
    @BeforeAll static void config() {
        CommentedConfig config = CommentedConfig.inMemory();
        McaQuestsConfig.COMMON_SPEC.correct(config);
        McaQuestsConfig.COMMON_SPEC.acceptConfig(config);
    }

    @Test void multipliersCannotWrapPositiveRewardsNegative() {
        assertEquals(Integer.MAX_VALUE, RewardAmounts.positiveScaled(Integer.MAX_VALUE, 2));
        assertEquals(0, RewardAmounts.positiveScaled(Integer.MAX_VALUE, 0));
        assertEquals(8, RewardAmounts.positiveScaled(5, 1.5));
    }

    @Test void addingToExistingExperienceCannotEraseThePlayersTotal() {
        assertEquals(7, RewardAmounts.remainingCapacity(Integer.MAX_VALUE - 7, 100));
        assertEquals(0, RewardAmounts.remainingCapacity(Integer.MAX_VALUE, 100));
        assertEquals(100, RewardAmounts.remainingCapacity(500, 100));
    }

    @Test void invertedHeartsLimitsStillRespectMaximum() {
        assertEquals(0, RewardAmounts.hearts(20, 1, 1000, 0));
        assertEquals(-10, RewardAmounts.hearts(Integer.MIN_VALUE, 2, -10, 20));
        assertEquals(20, RewardAmounts.hearts(Integer.MAX_VALUE, 2, -10, 20));
    }

    @Test void currencyCanRollTheEntireNonnegativeIntegerRange() {
        CurrencyReward reward = new CurrencyReward(Optional.of(0), Optional.of(Integer.MAX_VALUE), Optional.empty());
        RandomSource random = RandomSource.create(1234L);
        boolean nonzero = false;
        for (int i = 0; i < 100; i++) {
            int rolled = assertDoesNotThrow(() -> reward.roll(random));
            assertTrue(rolled >= 0);
            nonzero |= rolled > 0;
        }
        assertTrue(nonzero);
    }

}
