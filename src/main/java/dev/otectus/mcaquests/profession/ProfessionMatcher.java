package dev.otectus.mcaquests.profession;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Profession matching per the configured mode (spec section 12):
 * <ul>
 *   <li>{@code STRICT} — only exact id matches.</li>
 *   <li>{@code NORMALIZED} — equivalent vanilla/MCA professions match (same path, any namespace),
 *       so {@code minecraft:farmer} matches {@code mca:farmer}.</li>
 *   <li>{@code LOOSE} — normalized plus a built-in alias table.</li>
 * </ul>
 * The mode-taking overloads are pure (config-free) so they are unit-testable.
 */
public final class ProfessionMatcher {

    /** Small built-in alias table for LOOSE mode (datapack-driven aliases are a later addition). */
    private static final Map<ResourceLocation, Set<ResourceLocation>> ALIASES = Map.of(
            new ResourceLocation("mca", "guard"),
            Set.of(new ResourceLocation("minecraft", "none")),
            new ResourceLocation("mca", "outlaw"),
            Set.of(new ResourceLocation("minecraft", "none")));

    private ProfessionMatcher() {
    }

    public static boolean matchesAny(List<ResourceLocation> required, @Nullable ResourceLocation actual) {
        return matchesAny(required, actual, McaQuestsConfig.COMMON.professionMatchingMode.get());
    }

    public static boolean matchesAny(List<ResourceLocation> required, @Nullable ResourceLocation actual,
                                     ProfessionMatchingMode mode) {
        if (actual == null) {
            return false;
        }
        for (ResourceLocation candidate : required) {
            if (matches(candidate, actual, mode)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(ResourceLocation required, ResourceLocation actual, ProfessionMatchingMode mode) {
        if (required.equals(actual)) {
            return true;
        }
        return switch (mode) {
            case STRICT -> false;
            case NORMALIZED -> required.getPath().equals(actual.getPath());
            case LOOSE -> required.getPath().equals(actual.getPath()) || aliasMatch(required, actual);
        };
    }

    private static boolean aliasMatch(ResourceLocation a, ResourceLocation b) {
        Set<ResourceLocation> forward = ALIASES.get(a);
        if (forward != null && forward.contains(b)) {
            return true;
        }
        Set<ResourceLocation> backward = ALIASES.get(b);
        return backward != null && backward.contains(a);
    }
}
