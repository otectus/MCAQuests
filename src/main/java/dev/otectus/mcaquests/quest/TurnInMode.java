package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/** Where a completed quest may be turned in (spec section 17). Phase 1 implements ORIGINAL_GIVER. */
public enum TurnInMode {
    ORIGINAL_GIVER,
    SAME_PROFESSION,
    ANY_VILLAGER,
    SELF_COMPLETE,
    SPECIFIED_PROFESSION;

    public static final Codec<TurnInMode> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown turn_in mode: " + name);
                }
            },
            mode -> DataResult.success(mode.name().toLowerCase(Locale.ROOT)));
}
