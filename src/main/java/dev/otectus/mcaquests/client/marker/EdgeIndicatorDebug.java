package dev.otectus.mcaquests.client.marker;

/**
 * The switch behind {@code /mcaquestsclient debug marker}, and the last frame's working.
 *
 * <p>Everything the edge indicator decides is invisible by design: the player sees an arrow, not the
 * direction it was filtered from or the mode it is in. When the arrow is in the wrong place there is
 * nothing to look at, which is how the 1.5.3 flicker survived a release. Turning this on draws the raw
 * bearing and the filtered one as two lines from the centre of the screen, so a disagreement between
 * them is visible rather than inferred.
 *
 * <p>Off by default and read on the render thread every frame, so the flag is volatile and the numbers
 * are only written when it is on.
 */
public final class EdgeIndicatorDebug {

    /** Whether the overlay draws. Toggled by the client command; never persisted. */
    public static volatile boolean ENABLED;

    private static volatile double rawX;
    private static volatile double rawY;
    private static volatile double filteredX;
    private static volatile double filteredY;
    private static volatile EdgeIndicatorState.EdgeMode mode = EdgeIndicatorState.EdgeMode.HIDDEN;
    private static volatile EdgeIndicatorState.EdgeSide side = EdgeIndicatorState.EdgeSide.NONE;

    private EdgeIndicatorDebug() {
    }

    /** Toggle the overlay, and say what it is now. */
    public static boolean toggle() {
        ENABLED = !ENABLED;
        return ENABLED;
    }

    /** Keep this frame's working, for the HUD to draw a moment later. */
    public static void record(EdgeIndicatorState state) {
        rawX = state.rawDirectionX();
        rawY = state.rawDirectionY();
        filteredX = state.filteredDirectionX();
        filteredY = state.filteredDirectionY();
        mode = state.mode();
        side = state.edgeSide();
    }

    public static double rawX() {
        return rawX;
    }

    public static double rawY() {
        return rawY;
    }

    public static double filteredX() {
        return filteredX;
    }

    public static double filteredY() {
        return filteredY;
    }

    public static EdgeIndicatorState.EdgeMode mode() {
        return mode;
    }

    public static EdgeIndicatorState.EdgeSide side() {
        return side;
    }
}
