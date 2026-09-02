package dev.otectus.mcaquests.client;

/**
 * Scroll geometry for a clipped viewport: a window of {@code [top, bottom)} onto taller content
 * laid out in its own coordinate space starting at 0.
 *
 * <p>Deliberately pure integer math with no Minecraft types, so the clamping rules that keep cards
 * off the header and footer are unit-testable without a Forge bootstrap. Callers own all drawing —
 * they scissor to {@link #top()}/{@link #bottom()}, place content at {@link #screenY(int)}, and
 * draw a scrollbar from {@link #thumbTop()}/{@link #thumbHeight()} when {@link #overflows()}.
 *
 * <p>Every mutator re-clamps the scroll offset, so a resize or a now-shorter content list can never
 * strand the view past the end of the content.
 */
final class ScrollView {

    /** Minimum scrollbar thumb size, so it stays grabbable when the content is very long. */
    private static final int MIN_THUMB_HEIGHT = 8;

    private int top;
    private int bottom;
    private int contentHeight;
    private int scroll;

    /** Sets the visible window in screen coordinates. */
    void setViewport(int top, int bottom) {
        this.top = top;
        this.bottom = Math.max(top, bottom);
        clampScroll();
    }

    /** Sets the full height of the laid-out content, measured up front by the caller. */
    void setContentHeight(int contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        clampScroll();
    }

    /** Scrolls by {@code dy} content pixels (positive scrolls down), clamped to the content. */
    void scrollBy(int dy) {
        scroll += dy;
        clampScroll();
    }

    int top() {
        return top;
    }

    int bottom() {
        return bottom;
    }

    /** Height of the visible window. */
    int viewportHeight() {
        return bottom - top;
    }

    /**
     * Scrolls the least amount that brings a content-space band wholly inside the window.
     *
     * <p>What the keyboard needs: Tab moves focus to a widget that may be anywhere in the content,
     * and a focused button the player cannot see is worse than no focus at all. A band taller than
     * the window settles with its top edge at the top, which is the half a reader wants first.
     */
    void scrollIntoView(int contentY, int height) {
        if (contentY + height > scroll + viewportHeight()) {
            scroll = contentY + height - viewportHeight();
        }
        if (contentY < scroll) {
            scroll = contentY;
        }
        clampScroll();
    }

    int scroll() {
        return scroll;
    }

    /** How far the content can scroll; 0 when it already fits. */
    int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    /** Whether the content is taller than the window (i.e. a scrollbar is warranted). */
    boolean overflows() {
        return maxScroll() > 0;
    }

    /** Screen y for a content-space y. */
    int screenY(int contentY) {
        return top + contentY - scroll;
    }

    /**
     * Whether a content-space band is entirely inside the window. Interactive widgets are placed by
     * this: {@code super.render()} draws them outside our scissor and so cannot clip them, and a
     * widget straddling an edge would both paint over the header/footer and stay clickable there.
     */
    boolean isFullyVisible(int contentY, int height) {
        int y = screenY(contentY);
        return y >= top && y + height <= bottom;
    }

    /** Top of the scrollbar thumb, in screen coordinates. Meaningless unless {@link #overflows()}. */
    int thumbTop() {
        if (!overflows()) {
            return top;
        }
        int travel = viewportHeight() - thumbHeight();
        return top + (int) ((long) travel * scroll / maxScroll());
    }

    int thumbHeight() {
        if (contentHeight <= 0) {
            return viewportHeight();
        }
        int proportional = (int) ((long) viewportHeight() * viewportHeight() / contentHeight);
        return Math.max(MIN_THUMB_HEIGHT, Math.min(viewportHeight(), proportional));
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }
}
