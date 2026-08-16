package dev.otectus.mcaquests.quest.title;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * Where an earned title is stored and shown (spec 0.7.0):
 *
 * <ul>
 *   <li>{@code VILLAGE} — earned and displayed per MCA village (e.g. "Honored of this village").</li>
 *   <li>{@code GLOBAL} — earned once and displayed everywhere.</li>
 * </ul>
 */
public enum TitleScope {
    VILLAGE,
    GLOBAL;

    public static final Codec<TitleScope> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(TitleScope.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown title scope: " + s + " (expected village/global)");
                }
            },
            scope -> scope.name().toLowerCase(Locale.ROOT));

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
