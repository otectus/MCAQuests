package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * Who a community project is shared among (spec 0.4.0). Determines how a project instance is keyed in
 * shared storage:
 *
 * <ul>
 *   <li>{@code PLAYER} — one instance per contributing player (a personal multi-stage project).</li>
 *   <li>{@code VILLAGER} — one instance per sponsoring villager.</li>
 *   <li>{@code FAMILY} — shared across an MCA family lineage.</li>
 *   <li>{@code PROFESSION} — shared by a profession group within a village.</li>
 *   <li>{@code VILLAGE} — shared by a whole MCA village (the primary case).</li>
 * </ul>
 */
public enum ProjectScope {
    PLAYER,
    VILLAGER,
    FAMILY,
    PROFESSION,
    VILLAGE;

    public static final Codec<ProjectScope> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(ProjectScope.valueOf(s.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown project scope: " + s
                            + " (expected player/villager/family/profession/village)");
                }
            },
            scope -> scope.name().toLowerCase(Locale.ROOT));

    /** True when resolving this scope's identity requires MCA village/family data. */
    public boolean requiresMcaData() {
        return this == FAMILY || this == PROFESSION || this == VILLAGE;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}
