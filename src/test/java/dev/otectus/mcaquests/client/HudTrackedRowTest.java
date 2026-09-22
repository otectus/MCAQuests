package dev.otectus.mcaquests.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The followed quest always has a row on the tracker (1.7.0; F11). */
class HudTrackedRowTest {

    @Test
    @DisplayName("a followed quest past the limit takes the last row instead of vanishing")
    void followedQuestPastTheLimitIsShown() {
        assertEquals(List.of(0, 1, 4), HudRows.visible(List.of(false, false, false, false, true), 3));
    }

    @Test
    @DisplayName("within the limit, or with nothing followed, the log order is kept")
    void orderIsKeptOtherwise() {
        assertEquals(List.of(0, 1, 2), HudRows.visible(List.of(false, true, false, false), 3));
        assertEquals(List.of(0, 1), HudRows.visible(List.of(false, false, false), 2));
        assertEquals(List.of(0), HudRows.visible(List.of(false), 5));
        assertEquals(List.of(), HudRows.visible(List.of(true, false), 0));
    }
}
