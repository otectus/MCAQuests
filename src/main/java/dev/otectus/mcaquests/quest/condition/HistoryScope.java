package dev.otectus.mcaquests.quest.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * Whether a quest-state condition ({@code quest_completed} / {@code quest_not_completed} /
 * {@code quest_failed} / {@code quest_abandoned}) reads the player's <b>global</b> history (the
 * historical default) or only the history recorded against the <b>giver</b> villager currently being
 * talked to.
 *
 * <p>Chain {@code prerequisites} desugar to {@link #GIVER} so relationship arcs are per-villager (an arc
 * advanced with one villager does not advance with another). Hand-authored conditions default to
 * {@link #GLOBAL} for backward compatibility; a branch within a per-villager arc opts in with
 * {@code "scope": "giver"}.
 */
public enum HistoryScope {
    GLOBAL, GIVER;

    public static final Codec<HistoryScope> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown history scope: " + name);
                }
            },
            scope -> DataResult.success(scope.name().toLowerCase(Locale.ROOT)));
}
