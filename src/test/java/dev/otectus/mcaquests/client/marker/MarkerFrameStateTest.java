package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hand-off from the world renderer to the HUD, and the stamp that stops it going stale.
 *
 * <p>Untested until 1.5.4, which is the wrong way round: the frame stamp is the only thing standing
 * between a quest ending and its arrow being painted on the screen forever. A frame the world
 * renderer never ran in — no quest, GUI hidden, the player in another dimension, the level-render
 * event skipped — must read as nothing to draw, and each answer must be drawn at most once.
 */
class MarkerFrameStateTest {

    private static MarkerFrameState published(MarkerFrameState state, int renderTick) {
        state.publish(state.nextFrameId(renderTick), true, false, 100.0D, 200.0D, 1.5D,
                0x00FF00, 42L, GuidanceKind.LOCATION, 1.0F, EdgeIndicatorState.EdgeSide.LEFT);
        return state;
    }

    @Test
    @DisplayName("hands over a published frame exactly once")
    void publishThenConsumeOnce() {
        MarkerFrameState state = published(new MarkerFrameState(), 7);
        assertTrue(state.consume());
        assertEquals(100.0D, state.screenX());
        assertEquals(200.0D, state.screenY());
        assertEquals(42L, state.roundedDistance());
        assertSame(EdgeIndicatorState.EdgeSide.LEFT, state.edgeSide());

        // The HUD event can run twice for one world frame; the second one draws nothing.
        assertFalse(state.consume());
    }

    @Test
    @DisplayName("reads as already consumed when the renderer never published at all")
    void unpublishedReadsAsConsumed() {
        assertFalse(new MarkerFrameState().consume());
    }

    @Test
    @DisplayName("gives a fresh frame after each publish, so the next frame is drawn")
    void eachPublishIsConsumableAgain() {
        MarkerFrameState state = new MarkerFrameState();
        published(state, 7);
        assertTrue(state.consume());
        published(state, 7);
        assertTrue(state.consume());
    }

    @Test
    @DisplayName("never repeats a frame id, even within one render tick")
    void frameIdsAreUnique() {
        MarkerFrameState state = new MarkerFrameState();
        // The render tick alone is not enough: it changes twenty times a second while frames go past
        // at a hundred and forty, so a counter goes with it.
        long first = state.nextFrameId(7);
        long second = state.nextFrameId(7);
        long later = state.nextFrameId(8);
        assertNotEquals(first, second);
        assertNotEquals(second, later);
        assertNotEquals(0L, first, "zero is the unpublished state and must not be handed out");
    }
}
