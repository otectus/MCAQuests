package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server-side set of currently-loaded quest definitions, swapped atomically by
 * {@link QuestDataLoader} on every datapack reload. Read-only to the rest of the mod.
 */
public final class QuestRegistry {

    private static volatile Map<ResourceLocation, QuestDefinition> quests = Map.of();
    private static volatile List<String> lastErrors = List.of();

    private QuestRegistry() {
    }

    static void replaceAll(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        quests = Map.copyOf(loaded);
        lastErrors = List.copyOf(errors);
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

    public static List<String> lastErrors() {
        return lastErrors;
    }
}
