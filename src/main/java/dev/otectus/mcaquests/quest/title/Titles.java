package dev.otectus.mcaquests.quest.title;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Read-only registry of title definitions, swapped atomically by {@code TitleLoader} on datapack reload
 * (mirrors {@code ProjectRegistry}). Titles work even when undefined; this registry only supplies display
 * names and scopes for validation/UI (spec 0.7.0).
 */
public final class Titles {

    private static volatile Map<ResourceLocation, TitleDefinition> definitions = Map.of();

    private Titles() {
    }

    public static void replaceAll(Map<ResourceLocation, TitleDefinition> loaded) {
        definitions = Map.copyOf(loaded);
    }

    public static Optional<TitleDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static boolean isDefined(ResourceLocation id) {
        return definitions.containsKey(id);
    }

    /**
     * Display name for a title id: the datapack name when defined, else the lang key
     * {@code mcaquests.title.<path>} (which itself falls back to a humanised id at the client).
     */
    public static Component displayName(ResourceLocation id) {
        TitleDefinition def = definitions.get(id);
        if (def != null) {
            return Component.literal(def.name());
        }
        return Component.translatable("mcaquests.title." + id.getPath());
    }
}
