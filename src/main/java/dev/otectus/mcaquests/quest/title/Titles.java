package dev.otectus.mcaquests.quest.title;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** Every defined title id (task M5.1: FTB editor known-ids sync, spec §20). */
    public static Set<ResourceLocation> ids() {
        return definitions.keySet();
    }

    /**
     * Display name for a title id: the translation {@code mcaquests.title.<path>} when the client has
     * one, falling back to the datapack's own {@code name}, and to the key itself when there is neither.
     *
     * <p>The fallback order used to be reversed -- a defined title returned its {@code name} as a
     * literal, which meant every bundled title was pinned to English and the Portuguese entries for
     * them were dead weight nobody could ever see. Going through {@code translatableWithFallback} keeps
     * a third-party pack that ships a name and no lang file working exactly as before, while letting a
     * translated title actually be translated.
     */
    public static Component displayName(ResourceLocation id) {
        String key = "mcaquests.title." + id.getPath();
        TitleDefinition def = definitions.get(id);
        return def != null
                ? Component.translatableWithFallback(key, def.name())
                : Component.translatable(key);
    }
}
