package dev.otectus.mcaquests.quest.turnin;

import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same-profession hand-in fallback is about a giver who is <em>gone</em>.
 *
 * <p>{@code CONFIG.md} has always described {@code allowTurnInToSameProfessionIfOriginalMissing} as
 * "if the original giver is gone, allow any same-profession villager", and the code asked only
 * whether the flag was set: a player could walk past their giver and hand the quest to the next
 * farmer along. The presence check is the missing half of that sentence, and the decision itself is
 * a pure function of presence and flag so it can be pinned down without a running server.
 */
class TurnInEligibilityTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a giver standing right there always takes their own quest back")
    void presentGiverBlocksTheFallback() {
        assertFalse(GiverPresence.PRESENT.permitsSameProfessionFallback(true),
                "the flag must not override a giver who is present");
        assertFalse(GiverPresence.PRESENT.permitsSameProfessionFallback(false));
    }

    @Test
    @DisplayName("a dead giver permits the fallback, but only when the pack asked for it")
    void deadGiverFollowsTheFlag() {
        assertTrue(GiverPresence.KNOWN_DEAD.permitsSameProfessionFallback(true));
        assertFalse(GiverPresence.KNOWN_DEAD.permitsSameProfessionFallback(false),
                "the default stays off: the quest waits rather than reassigning itself");
    }

    @Test
    @DisplayName("an unloaded giver is treated as absent, because nothing can tell it from a dead one")
    void unloadedGiverFollowsTheFlag() {
        assertTrue(GiverPresence.UNLOADED_OR_UNKNOWN.permitsSameProfessionFallback(true));
        assertFalse(GiverPresence.UNLOADED_OR_UNKNOWN.permitsSameProfessionFallback(false));
    }

    @Test
    @DisplayName("a null server or a quest with no giver resolves to unknown rather than throwing")
    void missingInputsResolveToUnknown() {
        assertTrue(GiverPresence.of((net.minecraft.server.MinecraftServer) null, null)
                == GiverPresence.UNLOADED_OR_UNKNOWN);
        assertTrue(GiverPresence.of((net.minecraft.server.level.ServerLevel) null, null)
                == GiverPresence.UNLOADED_OR_UNKNOWN);
    }
}
