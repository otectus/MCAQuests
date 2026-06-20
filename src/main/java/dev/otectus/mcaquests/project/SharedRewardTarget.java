package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * Who receives a phase reward when a project phase completes (spec 0.4.0).
 *
 * <ul>
 *   <li>{@code CONTRIBUTORS} — every player who contributed to <em>this phase</em>.</li>
 *   <li>{@code ALL_PARTICIPANTS} — every player who contributed to the project at any phase.</li>
 *   <li>{@code SPONSOR_VILLAGE} — the village itself (reputation / village hearts), granted once.</li>
 *   <li>{@code TOP_CONTRIBUTOR} — only the single largest contributor to this phase.</li>
 * </ul>
 */
public enum SharedRewardTarget {
    CONTRIBUTORS,
    ALL_PARTICIPANTS,
    SPONSOR_VILLAGE,
    TOP_CONTRIBUTOR;

    public static final Codec<SharedRewardTarget> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(SharedRewardTarget.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown reward target: " + s
                            + " (expected contributors/all_participants/sponsor_village/top_contributor)");
                }
            },
            target -> target.name().toLowerCase(Locale.ROOT));

    /** True when this target rewards individual players (not the village pool). */
    public boolean isPlayerTarget() {
        return this != SPONSOR_VILLAGE;
    }

    public String translationKey() {
        return "mcaquests.project.target." + name().toLowerCase(Locale.ROOT);
    }
}
