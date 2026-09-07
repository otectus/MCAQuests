package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressBoundsTest {
    @Test void objectiveProgressSaturatesInsteadOfWrapping() {
        ObjectiveProgress progress = new ObjectiveProgress(Integer.MAX_VALUE - 1);
        progress.add(100);
        assertEquals(Integer.MAX_VALUE, progress.count());
        progress.add(Integer.MIN_VALUE);
        assertEquals(0, progress.count());
        progress.addElapsed(Long.MAX_VALUE);
        progress.addElapsed(1);
        assertEquals(Long.MAX_VALUE, progress.elapsedTicks());
    }

    @Test void malformedNegativeProgressCannotSurviveReload() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", -1);
        tag.putLong("elapsed", -1);
        assertEquals(0, ObjectiveProgress.load(tag).count());
        assertEquals(0, ObjectiveProgress.load(tag).elapsedTicks());
        assertEquals(0, SharedObjectiveProgress.load(tag).count());
    }

    @Test void sharedContributionCannotWrapOrBecomeNegative() {
        UUID player = UUID.randomUUID();
        SharedObjectiveProgress progress = new SharedObjectiveProgress();
        progress.add(Integer.MAX_VALUE);
        progress.add(1);
        progress.addContribution(player, Integer.MAX_VALUE);
        progress.addContribution(player, 1);
        progress.addContribution(player, -1);
        assertEquals(Integer.MAX_VALUE, progress.count());
        assertEquals(Integer.MAX_VALUE, progress.contributionOf(player));
        assertEquals(Integer.MAX_VALUE, SharedObjectiveProgress.load(progress.save()).contributionOf(player));
    }

    @Test void bankedHeartsSaturateWithoutChangingSignAndStillCancel() {
        UUID player = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        PendingHeartsData data = new PendingHeartsData();
        data.queue(villager, player, Integer.MAX_VALUE);
        data.queue(villager, player, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, data.owedTo(villager).get(player));
        data.queue(villager, player, -Integer.MAX_VALUE);
        assertTrue(data.isEmpty());
        data.queue(villager, player, Integer.MIN_VALUE);
        data.queue(villager, player, -1);
        assertEquals(Integer.MIN_VALUE, data.owedTo(villager).get(player));
    }
}
