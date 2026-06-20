package dev.otectus.mcaquests.project.state;

import java.util.Locale;

/** Lifecycle state of a project instance (spec 0.4.0). */
public enum ProjectStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED;

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public static ProjectStatus fromString(String s) {
        try {
            return ProjectStatus.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
