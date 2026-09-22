package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failed situation is remembered, so a participant who was offline when it failed is reconciled at
 * login the way online participants were (1.7.0; F04).
 */
class SituationFailureRecordTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a failure survives a save and expires after the retention window")
    void failureIsRememberedThenForgotten() {
        UUID failed = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        SituationSavedData data = new SituationSavedData();
        data.recordFailure(failed, 1_000L);

        SituationSavedData reloaded = SituationSavedData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.failed(failed));
        assertFalse(reloaded.failed(later));

        reloaded.recordFailure(later, 1_000L + SituationSavedData.FAILED_RETENTION_TICKS + 1L);
        assertFalse(reloaded.failed(failed), "older than the retention window");
        assertTrue(reloaded.failed(later));
    }

    @Test
    @DisplayName("a save with no failures writes no failed key")
    void absentWhenEmpty() {
        assertFalse(new SituationSavedData().save(new CompoundTag()).contains("failed"));
    }
}
