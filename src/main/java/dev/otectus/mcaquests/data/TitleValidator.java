package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.title.TitleDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Validation for title definitions (spec 0.7.0). Scope is constrained by the codec, so this currently
 * only guards against empty display names; kept as a seam for future title-level checks.
 */
public final class TitleValidator {

    private TitleValidator() {
    }

    public static boolean validate(ResourceLocation id, TitleDefinition def, List<String> errors) {
        if (def.name().isBlank()) {
            errors.add("Title '" + id + "' has a blank display name.");
            return false;
        }
        return true;
    }
}
