package dev.otectus.mcaquests;

import dev.otectus.mcaquests.quest.ChainSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link ChainSpec}'s final-stage detection (spec §29.1 #3 / §15.2): a quest is
 * a final stage of its chain when {@code stage == stageTotal} if {@code stageTotal} is present,
 * otherwise when {@code unlocks} is empty. Branching arcs may have multiple final stages.
 */
class ChainFinalStageTest {

    private static ChainSpec chain(int stage, Optional<Integer> stageTotal, List<ResourceLocation> unlocks) {
        return new ChainSpec("family_arc", stage, stageTotal, Optional.empty(), Optional.empty(), List.of(), unlocks);
    }

    @Test
    void stageTotalPresentAndMatchingIsFinal() {
        assertTrue(chain(3, Optional.of(3), List.of()).isFinalStage());
    }

    @Test
    void stageTotalPresentAndNotMatchingIsNotFinal() {
        assertFalse(chain(2, Optional.of(3), List.of()).isFinalStage());
    }

    @Test
    void stageTotalPresentIgnoresUnlocksWhenDecidingFinality() {
        // stage_total is authoritative when present, even if unlocks happens to be non-empty (e.g. a
        // sequel arc hook) or empty (e.g. a genuinely last stage) — stage == stageTotal decides it.
        ResourceLocation sequelHook = new ResourceLocation("mcaquests", "sequel_arc_intro");
        assertTrue(chain(3, Optional.of(3), List.of(sequelHook)).isFinalStage());
        assertFalse(chain(1, Optional.of(3), List.of()).isFinalStage());
    }

    @Test
    void stageTotalAbsentAndUnlocksEmptyIsFinal() {
        assertTrue(chain(1, Optional.empty(), List.of()).isFinalStage());
    }

    @Test
    void stageTotalAbsentAndUnlocksNonEmptyIsNotFinal() {
        ResourceLocation next = new ResourceLocation("mcaquests", "family_arc_stage_2");
        assertFalse(chain(1, Optional.empty(), List.of(next)).isFinalStage());
    }

    @Test
    void branchingArcCanHaveMultipleFinalStages() {
        // A branching arc: two sibling stages both unlocked from a common stage 1, each with empty
        // `unlocks` of its own (both are dead ends -> both are finals). "Any counts" per §15.2.
        ChainSpec goodEnding = chain(2, Optional.empty(), List.of());
        ChainSpec badEnding = chain(2, Optional.empty(), List.of());
        assertTrue(goodEnding.isFinalStage());
        assertTrue(badEnding.isFinalStage());

        // A third sibling that instead continues the arc is correctly excluded.
        ResourceLocation continuation = new ResourceLocation("mcaquests", "family_arc_stage_3");
        ChainSpec middleBranch = chain(2, Optional.empty(), List.of(continuation));
        assertFalse(middleBranch.isFinalStage());
    }
}
