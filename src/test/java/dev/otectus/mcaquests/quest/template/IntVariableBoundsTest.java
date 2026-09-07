package dev.otectus.mcaquests.quest.template;

import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class IntVariableBoundsTest {
    @Test
    void fullIntegerRangeDoesNotOverflowTheRandomBound() {
        var variable = new IntVariable(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, OptionalInt.empty());
        var random = new Random(2);
        for (int i = 0; i < 100; i++) assertDoesNotThrow(() -> variable.resolveAmount(random, 0, 0));
    }

    @Test
    void largeScalingSaturatesAndHonorsTheAuthoredLimit() {
        var unlimited = new IntVariable(10, 10, 100, 100, OptionalInt.empty());
        assertEquals(Integer.MAX_VALUE, unlimited.resolveAmount(new Random(0), Integer.MAX_VALUE, Integer.MAX_VALUE));
        var limited = new IntVariable(10, 10, 100, 100, OptionalInt.of(64));
        assertEquals(64, limited.resolveAmount(new Random(0), Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void ordinaryRollsKeepTheirExistingSeededResult() {
        var variable = new IntVariable(2, 8, 0.5, 0.25, OptionalInt.empty());
        assertEquals(2 + new Random(17).nextInt(7) + 5 + 2,
                variable.resolveAmount(new Random(17), 10, 8));
    }

    @Test
    void oppositeHugeCoefficientsCancelWithoutLosingTheRolledValue() {
        var variable = new IntVariable(5, 10, Double.MAX_VALUE, -Double.MAX_VALUE, OptionalInt.empty());
        assertEquals(5 + new Random(17).nextInt(6), variable.resolveAmount(new Random(17), 2, 2));
    }
}
