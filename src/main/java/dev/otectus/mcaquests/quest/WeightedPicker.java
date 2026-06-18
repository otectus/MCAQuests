package dev.otectus.mcaquests.quest;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * Deterministic weighted selection of distinct items (spec section 18). For a given item order +
 * seed the result is stable, so re-opening the menu the same day does not reroll the offers. Pure
 * (no Minecraft types) so it is unit-testable.
 */
public final class WeightedPicker {

    private WeightedPicker() {
    }

    @Nullable
    public static <T> T pick(List<T> items, ToIntFunction<T> weight, long seed) {
        List<T> chosen = pickMany(items, weight, seed, 1);
        return chosen.isEmpty() ? null : chosen.get(0);
    }

    /** Up to {@code count} <b>distinct</b> weighted picks; weights below 1 are treated as 1. */
    public static <T> List<T> pickMany(List<T> items, ToIntFunction<T> weight, long seed, int count) {
        List<T> pool = new ArrayList<>(items);
        List<T> chosen = new ArrayList<>();
        Random random = new Random(seed);
        int picks = Math.min(count, pool.size());
        for (int i = 0; i < picks; i++) {
            int total = 0;
            for (T item : pool) {
                total += Math.max(1, weight.applyAsInt(item));
            }
            int roll = random.nextInt(total);
            int accumulated = 0;
            int index = pool.size() - 1;
            for (int j = 0; j < pool.size(); j++) {
                accumulated += Math.max(1, weight.applyAsInt(pool.get(j)));
                if (roll < accumulated) {
                    index = j;
                    break;
                }
            }
            chosen.add(pool.remove(index));
        }
        return chosen;
    }
}
