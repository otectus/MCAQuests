package dev.otectus.mcaquests.quest.situation.state;

import java.util.Locale;

/** Lifecycle state of an open {@link SituationInstance} (0.8.0). */
public enum SituationStatus {
    /** Live and surfacing its offer. */
    OPEN,
    /** A player resolved it (the offer quest was completed). */
    RESOLVED_SUCCESS,
    /** The deadline expired or the condition resolved against the village. */
    RESOLVED_FAILURE,
    /** The condition lifted on its own before anyone acted (usually neutral). */
    EXPIRED;

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isOpen() {
        return this == OPEN;
    }

    public static SituationStatus fromString(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
