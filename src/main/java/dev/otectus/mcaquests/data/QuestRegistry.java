package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The server-side set of currently-loaded quest definitions, swapped atomically by
 * {@link QuestDataLoader} on every datapack reload. Read-only to the rest of the mod.
 */
public final class QuestRegistry {

    private static volatile Map<ResourceLocation, QuestDefinition> quests = Map.of();
    private static volatile List<String> lastErrors = List.of();
    private static volatile List<String> lastWarnings = List.of();
    /**
     * Bumped on every reload, so anything holding a decision derived from the catalogue can tell whether
     * that catalogue has changed underneath it. A remembered offer set is the first such thing: a quest it
     * names may not exist any more, and re-checking every slot against the registry on every menu open
     * would be the very per-open recomputation the offer session exists to avoid.
     */
    private static volatile int generation;
    /**
     * Quest ids that failed to parse at the last load, mapped to the resource namespace their error
     * blamed (empty string when none could be identified).
     *
     * <p>Kept because "this quest could not be loaded" and "this quest never existed" are different
     * facts and the quest log used to conflate them: a player holding a quest from a datapack that
     * names an uninstalled mod's content saw "Unknown quest" and lost the ability to tell whether
     * removing that mod had done it. Recorded per reload, cleared by the next one, never persisted —
     * a reload that fixes the pack empties this by itself.
     */
    private static volatile Map<ResourceLocation, String> quarantine = Map.of();

    private QuestRegistry() {
    }

    static void replaceAll(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors, List<String> warnings) {
        replaceAll(loaded, errors, warnings, Map.of());
    }

    static void replaceAll(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors,
                           List<String> warnings, Map<ResourceLocation, String> quarantined) {
        quests = Map.copyOf(loaded);
        generation++;
        lastErrors = List.copyOf(errors);
        lastWarnings = List.copyOf(warnings);
        quarantine = Map.copyOf(quarantined);
    }

    /** True when this id was seen at the last load but could not be parsed. */
    public static boolean isQuarantined(ResourceLocation id) {
        return quarantine.containsKey(id);
    }

    /**
     * The namespace blamed for a quarantined quest's parse failure, if one could be identified —
     * normally the mod whose content the quest names. Empty for an id that loaded fine, and for one
     * whose error message named nothing useful.
     */
    public static Optional<String> quarantinedNamespace(ResourceLocation id) {
        String namespace = quarantine.get(id);
        return namespace == null || namespace.isEmpty() ? Optional.empty() : Optional.of(namespace);
    }

    /** Every quarantined quest from the last load, for diagnostics. */
    public static Map<ResourceLocation, String> quarantined() {
        return quarantine;
    }

    /** How many times the catalogue has been replaced. Never reset; wraps harmlessly. */
    public static int generation() {
        return generation;
    }

    public static Collection<QuestDefinition> all() {
        return quests.values();
    }

    public static Optional<QuestDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(quests.get(id));
    }

    public static boolean contains(ResourceLocation id) {
        return quests.containsKey(id);
    }

    public static int size() {
        return quests.size();
    }

    /**
     * Every distinct {@code chain} id referenced by a loaded quest's {@link dev.otectus.mcaquests.quest.ChainSpec}
     * (spec §21 {@code ftbq validate} — "is this chain id known" without re-scanning every quest at
     * each call site; {@code FtbqEditorIdsSync} additionally wants display names per id, so it keeps
     * its own parallel scan rather than sharing this one).
     */
    public static Set<String> chainIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (QuestDefinition def : quests.values()) {
            def.chain().ifPresent(chain -> ids.add(chain.chain()));
        }
        return ids;
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    /** Non-fatal chain/style problems from the last load (heuristics that never block load). */
    public static List<String> lastWarnings() {
        return lastWarnings;
    }
}
