package dev.otectus.mcaquests.project.objective;

import net.minecraft.network.chat.Component;

/**
 * Where one project objective stands, in the four states the recovery brief asked to keep apart plus
 * done (1.6.6). Every state has a glyph and a word, so it never depends on colour alone.
 *
 * <ul>
 *   <li>{@link #IN_PROGRESS}: legitimately not yet satisfied; the help says what to do next.</li>
 *   <li>{@link #BLOCKED}: cannot advance until something else changes — a phase gate, a pending
 *       baseline, an unknown or unreadable building.</li>
 *   <li>{@link #UNAVAILABLE}: the optional mod or capability it reads is missing; progress and clocks
 *       are paused, nothing is lost.</li>
 *   <li>{@link #UNOBSERVED}: it depends on residents or places that are not loaded right now, so the
 *       count shown is the last one seen, not a failure.</li>
 * </ul>
 */
public enum ProjectObjectiveStatus {
    SATISFIED("✔", "mcaquests.project.status.satisfied"),
    IN_PROGRESS("•", "mcaquests.project.status.in_progress"),
    BLOCKED("⚠", "mcaquests.project.status.blocked"),
    UNAVAILABLE("⏸", "mcaquests.project.status.unavailable"),
    UNOBSERVED("?", "mcaquests.project.status.unobserved");

    private final String glyph;
    private final String key;

    ProjectObjectiveStatus(String glyph, String key) {
        this.glyph = glyph;
        this.key = key;
    }

    public String glyph() {
        return glyph;
    }

    public Component label() {
        return Component.translatable(key);
    }

    public static ProjectObjectiveStatus byOrdinal(int ordinal) {
        ProjectObjectiveStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IN_PROGRESS;
    }
}
