package dev.otectus.mcaquests.client;

import java.util.ArrayList;
import java.util.List;

/** Which rows the HUD tracker shows. Pure, so it can be tested without a client. */
final class HudRows {

    private HudRows() {
    }

    /**
     * Which quests the tracker shows, by index into the log (1.7.0). The first {@code max} in the log's
     * order, except that the quest the player is following is always among them: it used to fall off the
     * tracker whenever it had been accepted after {@code max} others, while the marker still pointed at it.
     */
    static List<Integer> visible(List<Boolean> tracked, int max) {
        int limit = Math.max(0, Math.min(tracked.size(), max));
        List<Integer> rows = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            rows.add(i);
        }
        int followed = tracked.indexOf(Boolean.TRUE);
        if (limit > 0 && followed >= limit) {
            rows.set(limit - 1, followed);
        }
        return rows;
    }
}
