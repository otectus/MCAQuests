package dev.otectus.mcaquests.project.state;

import dev.otectus.mcaquests.project.ProjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical key for one project instance: {@code projectId|scope|identity}. The {@code identity} is the
 * scope identity string from {@code ScopeResolver} (e.g. {@code v:12} for village 12). Neither the
 * project id nor the scope/identity ever contain a {@code '|'}, so the three parts split cleanly.
 */
public record ProjectInstanceKey(ResourceLocation projectId, ProjectScope scope, String identity) {

    public String asString() {
        return projectId + "|" + scope.lower() + "|" + identity;
    }

    public static Optional<ProjectInstanceKey> parse(String s) {
        String[] parts = s.split("\\|", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(parts[0]);
        if (id == null) {
            return Optional.empty();
        }
        try {
            ProjectScope scope = ProjectScope.valueOf(parts[1].toUpperCase(Locale.ROOT));
            return Optional.of(new ProjectInstanceKey(id, scope, parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
