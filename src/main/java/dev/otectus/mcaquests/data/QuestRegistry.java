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

    private QuestRegistry() {
    }

    static void replaceAll(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors, List<String> warnings) {
        quests = Map.copyOf(loaded);
        lastErrors = List.copyOf(errors);
        lastWarnings = List.copyOf(warnings);
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
