package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * The result an {@code mcaquests:ftbq_*} condition (spec §17) falls back to whenever FTB Quests'
 * real state cannot be consulted: the integration is absent/disabled, the bridge throws, or the id
 * doesn't resolve to an object of the right kind in the loaded book. Shared by
 * {@link FtbqQuestCompletedCondition}, {@link FtbqChapterCompletedCondition}, and
 * {@link FtbqTaskCompletedCondition} so the enum and its codec exist exactly once.
 */
public enum FtbqWhenMissing {
    NOT_MET,
    MET;

    /** {@code MET} reads as "condition passes"; {@code NOT_MET} as "condition fails". */
    public boolean asBoolean() {
        return this == MET;
    }

    public static final Codec<FtbqWhenMissing> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown when_missing: '" + name + "' (expected not_met/met)");
                }
            },
            value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
}
