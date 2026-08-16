package dev.otectus.mcaquests.project.data;

import dev.otectus.mcaquests.project.ProjectDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The server-side set of currently-loaded project definitions, swapped atomically by
 * {@code ProjectDataLoader} on every datapack reload. Read-only to the rest of the mod (mirrors
 * {@code QuestRegistry}).
 */
public final class ProjectRegistry {

    private static volatile Map<ResourceLocation, ProjectDefinition> projects = Map.of();
    private static volatile List<String> lastErrors = List.of();

    private ProjectRegistry() {
    }

    public static void replaceAll(Map<ResourceLocation, ProjectDefinition> loaded, List<String> errors) {
        projects = Map.copyOf(loaded);
        lastErrors = List.copyOf(errors);
    }

    public static Collection<ProjectDefinition> all() {
        return projects.values();
    }

    public static Optional<ProjectDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(projects.get(id));
    }

    public static boolean contains(ResourceLocation id) {
        return projects.containsKey(id);
    }

    public static int size() {
        return projects.size();
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }
}
