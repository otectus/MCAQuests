package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * Who an open situation is scoped to (0.8.0). Decides which givers may surface its offer and where the
 * reputation outcome is applied:
 *
 * <ul>
 *   <li>{@code VILLAGE} — the whole village reacts (the primary case).</li>
 *   <li>{@code VILLAGER} — a single villager's plight (the at-risk/infected/bereaved one).</li>
 *   <li>{@code FAMILY} — an MCA family lineage (e.g. missing kin).</li>
 * </ul>
 */
public enum SituationScope {
    VILLAGE,
    VILLAGER,
    FAMILY;

    public static final Codec<SituationScope> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(SituationScope.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown situation scope: " + s
                            + " (expected village/villager/family)");
                }
            },
            scope -> scope.name().toLowerCase(Locale.ROOT));

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
