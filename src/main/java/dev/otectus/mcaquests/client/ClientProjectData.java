package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.project.ProjectLogEntry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of community-project state (spec 0.4.0): the participating-project log (for the
 * quest log + HUD) and the most recent per-villager project menu (so the villager screen can offer a
 * "View Project" button without an extra round-trip). Mirrors {@code ClientQuestData}.
 */
public final class ClientProjectData {

    private static volatile List<ProjectLogEntry> projects = List.of();
    private static final Map<UUID, List<ProjectCard>> menus = new ConcurrentHashMap<>();

    private ClientProjectData() {
    }

    public static void updateProjects(List<ProjectLogEntry> entries) {
        projects = List.copyOf(entries);
    }

    public static List<ProjectLogEntry> projects() {
        return projects;
    }

    public static void cacheMenu(UUID villager, List<ProjectCard> cards) {
        if (cards.isEmpty()) {
            menus.remove(villager);
        } else {
            menus.put(villager, List.copyOf(cards));
        }
    }

    public static List<ProjectCard> menuFor(UUID villager) {
        return menus.getOrDefault(villager, List.of());
    }

    public static boolean hasMenuFor(UUID villager) {
        return !menuFor(villager).isEmpty();
    }
}
