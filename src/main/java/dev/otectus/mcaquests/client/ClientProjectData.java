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

    /**
     * {@code incoming}, except that a card older than one already cached for the same live instance is
     * replaced by the cached one (1.7.0): a menu computed before a change must not undo the change on
     * screen. Cards for offers carry no instance and always pass.
     */
    public static List<ProjectCard> newestOf(UUID villager, List<ProjectCard> incoming) {
        List<ProjectCard> cached = menuFor(villager);
        if (cached.isEmpty()) {
            return incoming;
        }
        java.util.Map<String, ProjectCard> byKey = new java.util.HashMap<>();
        for (ProjectCard card : cached) {
            if (!card.instanceKey().isEmpty()) {
                byKey.put(card.instanceKey(), card);
            }
        }
        List<ProjectCard> out = new java.util.ArrayList<>(incoming.size());
        for (ProjectCard card : incoming) {
            ProjectCard previous = card.instanceKey().isEmpty() ? null : byKey.get(card.instanceKey());
            out.add(previous != null && previous.revision() > card.revision() ? previous : card);
        }
        return out;
    }

    public static List<ProjectCard> menuFor(UUID villager) {
        return menus.getOrDefault(villager, List.of());
    }

    public static boolean hasMenuFor(UUID villager) {
        return !menuFor(villager).isEmpty();
    }

    /**
     * Drops both caches on logout. The per-villager menus matter most: they are keyed by UUID, and a
     * villager on another server sharing one would have been offered a "View Project" button for a
     * project nobody here has ever heard of.
     */
    public static void clear() {
        projects = List.of();
        menus.clear();
    }
}
