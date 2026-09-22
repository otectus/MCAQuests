package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The namespaces of the optional mods MCA: Quests integrates with.
 *
 * <p>A definition can name another mod's content in two ways: through one of this mod's own types, which
 * {@code IntegrationRequirements} judges after parsing, or directly — a condition or objective type that
 * the other mod registers itself ({@code ultima_kingdoms:kingdom}, {@code mcaconversations:talk_about}),
 * or an item or entity only that mod adds. Without the mod the second kind cannot even be parsed. It is
 * still content for a mod this installation does not have, not a malformed file, so under the 1.7.0
 * optional-dependency contract it is excluded rather than reported as an error — strict mode included.
 *
 * <p>Only the namespaces listed here get that treatment. Any other unknown namespace keeps failing
 * loudly, because on a server without the mod a misspelt namespace and an absent one look the same, and
 * a typo must stay an error.
 */
public final class OptionalModNamespaces {

    private static final Set<String> KNOWN = Set.of(
            "townstead", "mcacapitals", "ultima_kingdoms", "mcareputation", "mcaconversations",
            "ftbquests", "iceandfire", "bountiful");

    /** Exactly the characters a {@code ResourceLocation} allows, so it cannot run away over a long message. */
    private static final Pattern RESOURCE_ID = Pattern.compile("([a-z0-9_.-]+):[a-z0-9_./-]+");

    private OptionalModNamespaces() {
    }

    /**
     * The namespace of the first {@code namespace:path} in a codec error message, which for a definition
     * that named content from an uninstalled mod is that mod's. Empty when there is none. A heuristic,
     * used only to decide between "excluded" and "error" for the namespaces listed above.
     */
    public static String namespaceIn(String message) {
        if (message == null) {
            return "";
        }
        Matcher matcher = RESOURCE_ID.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * The first namespace in a codec error message that belongs to an optional mod that is not installed,
     * or the empty string. Every id in the message is considered, not only the first: vanilla's own
     * registry errors name the registry ({@code minecraft:item}) before the missing entry.
     */
    public static String absentOptionalNamespace(String message) {
        if (message == null) {
            return "";
        }
        Matcher matcher = RESOURCE_ID.matcher(message);
        while (matcher.find()) {
            if (isAbsentOptionalMod(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        return "";
    }

    /**
     * Whether a parse failure was an uninstalled optional mod's content. Records it in {@code absent}
     * when it was, so the loader can report one summary line instead of an error per file.
     */
    public static boolean excludedForAbsentMod(String failure, Map<String, Integer> absent) {
        String namespace = absentOptionalNamespace(failure);
        if (namespace.isEmpty()) {
            return false;
        }
        absent.merge(namespace, 1, Integer::sum);
        return true;
    }

    /** True when {@code namespace} belongs to an optional integration that is not installed here. */
    public static boolean isAbsentOptionalMod(String namespace) {
        return namespace != null && KNOWN.contains(namespace) && !loaded(namespace);
    }

    private static boolean loaded(String namespace) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(namespace);
    }

    /** One INFO line per loader and kind, naming each absent mod and how many files needed it. */
    public static void report(String kind, Map<String, Integer> byNamespace) {
        if (byNamespace.isEmpty()) {
            return;
        }
        int total = byNamespace.values().stream().mapToInt(Integer::intValue).sum();
        McaQuests.LOGGER.info("[MCA: Quests] Not loading {} {} file(s) that name content from optional mods "
                + "this installation does not have: {}. Records already accepted stay paused, not lost.",
                total, kind, byNamespace);
    }
}
