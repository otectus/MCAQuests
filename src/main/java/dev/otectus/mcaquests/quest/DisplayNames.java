package dev.otectus.mcaquests.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Turns raw ids and tags into player-readable names for objective text. Tags and ids that have no
 * curated lang entry fall back to a humanized form of their path (e.g. {@code small_flowers} →
 * "Small Flowers"), so the HUD never shows raw strings like {@code #minecraft:small_flowers}.
 *
 * <p>These run server-side while building objective Components; {@link Component#translatableWithFallback}
 * defers the actual lookup to the client, so curated lang entries and resource packs still win.
 */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** Readable name for a tag id. Lang key: {@code mcaquests.tag.<namespace>.<path>}. */
    public static Component tagName(ResourceLocation tagId) {
        return Component.translatableWithFallback(
                "mcaquests.tag." + tagId.getNamespace() + "." + tagId.getPath(),
                humanize(tagId.getPath()));
    }

    /** Readable name for a plain id (dimension, profession, biome, ...). Lang key: {@code mcaquests.name.<namespace>.<path>}. */
    public static Component name(ResourceLocation id) {
        return Component.translatableWithFallback(
                "mcaquests.name." + id.getNamespace() + "." + id.getPath(),
                humanize(id.getPath()));
    }

    /** Title-cases a resource path: splits on {@code _} and {@code /}, capitalizes each word. */
    public static String humanize(String path) {
        String[] words = path.split("[_/]");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
