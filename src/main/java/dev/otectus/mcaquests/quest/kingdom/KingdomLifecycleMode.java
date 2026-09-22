package dev.otectus.mcaquests.quest.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

public enum KingdomLifecycleMode {
    OFFER_ONLY,
    BOUND_AT_ACCEPT,
    LIVE,
    FAIL_ON_CHANGE;

    public static final Codec<KingdomLifecycleMode> CODEC = Codec.STRING.flatXmap(
            value -> {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown kingdom lifecycle mode: " + value);
                }
            }, value -> DataResult.success(value.serializedName()));

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
