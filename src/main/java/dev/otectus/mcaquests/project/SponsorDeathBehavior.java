package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * What happens to an active project when its last living sponsor dies (spec 0.4.0).
 *
 * <ul>
 *   <li>{@code FAIL} — the project fails immediately.</li>
 *   <li>{@code PAUSE} — the project pauses until another eligible villager interacts with a player.</li>
 *   <li>{@code TRANSFER} — reassign sponsorship to another eligible living villager, else pause.</li>
 *   <li>{@code TURN_IN_TO_VILLAGE} — grant the current village-targeted rewards and complete gracefully.</li>
 * </ul>
 */
public enum SponsorDeathBehavior {
    FAIL,
    PAUSE,
    TRANSFER,
    TURN_IN_TO_VILLAGE;

    public static final Codec<SponsorDeathBehavior> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(SponsorDeathBehavior.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown sponsor death behavior: " + s
                            + " (expected fail/pause/transfer/turn_in_to_village)");
                }
            },
            behavior -> behavior.name().toLowerCase(Locale.ROOT));
}
