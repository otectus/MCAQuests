package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * How demanding a quest is, used to pick a reward band rather than hard-coding amounts in every quest
 * (spec 1.1.0). Purely optional metadata: a quest that omits {@code difficulty} keeps whatever explicit
 * rewards it declares, so existing third-party datapacks are unaffected.
 *
 * <p>The bands are deliberately coarse. {@code EASY} is a fetch-or-errand a player can finish without
 * leaving the village, {@code MEDIUM} needs a trip or a fight, and {@code HARD} means real risk or a long
 * expedition. Server owners map each band to a currency range in {@code mcaquests-common.toml}.
 */
public enum QuestDifficulty {
    EASY,
    MEDIUM,
    HARD;

    /** The band assumed when a reward needs one but neither it nor its quest declares a difficulty. */
    public static final QuestDifficulty DEFAULT = MEDIUM;

    public static final Codec<QuestDifficulty> CODEC = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown quest difficulty '" + s
                            + "' (expected one of easy, medium, hard)");
                }
            },
            d -> DataResult.success(d.lower()));

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
