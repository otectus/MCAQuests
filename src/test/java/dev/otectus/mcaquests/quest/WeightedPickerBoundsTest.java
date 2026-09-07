package dev.otectus.mcaquests.quest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WeightedPickerBoundsTest {
    @Test void legalLargeWeightsCannotOverflowTheRandomBound() {
        List<String> pool = List.of("one", "two", "three");
        List<String> picked = WeightedPicker.pickMany(pool, ignored -> Integer.MAX_VALUE, 12, 3);
        assertEquals(3, picked.stream().distinct().count());
        assertEquals(picked, WeightedPicker.pickMany(pool, ignored -> Integer.MAX_VALUE, 12, 3));
    }
}
