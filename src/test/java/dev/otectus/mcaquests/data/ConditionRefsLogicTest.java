package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ConditionRefsLogicTest {
    private final QuestCompletedCondition a = new QuestCompletedCondition(new ResourceLocation("test", "a"), HistoryScope.GLOBAL);
    private final QuestCompletedCondition b = new QuestCompletedCondition(new ResourceLocation("test", "b"), HistoryScope.GLOBAL);

    @Test
    void negatedConjunctionDoesNotRequireBothBranches() {
        assertTrue(ConditionRefs.required(Optional.of(new NotCondition(
                new AllOfCondition(List.of(a, new NotCondition(a)))))).isEmpty(),
                "NOT(A AND NOT A) is always true, not a contradictory quest gate");
    }

    @Test
    void negatedDisjunctionRequiresBothBranchesToBeFalse() {
        var refs = ConditionRefs.required(Optional.of(new NotCondition(new AnyOfCondition(List.of(a, b)))));
        assertEquals(List.of(a.quest(), b.quest()), refs.stream().map(ConditionRefs.Ref::quest).toList());
        assertTrue(refs.stream().noneMatch(ConditionRefs.Ref::polarity));
    }

    @Test
    void doubleNegationKeepsPositiveRequirements() {
        var refs = ConditionRefs.required(Optional.of(new NotCondition(new NotCondition(new AllOfCondition(List.of(a, b))))));
        assertEquals(2, refs.size());
        assertTrue(refs.stream().allMatch(ConditionRefs.Ref::polarity));
    }
}
