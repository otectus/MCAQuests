package dev.otectus.mcaquests.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registry-free tests for the quest-card viewport's scroll math. This is the half of the overflow
 * fix that can be checked without a client: that content can never be scrolled past its own end and
 * that a widget straddling a viewport edge is reported hidden (which is what keeps card buttons from
 * painting over — and stealing clicks from — the header and footer).
 *
 * <p>Lives in the {@code client} package because {@link ScrollView} is package-private; it pulls in
 * no Minecraft types, so it needs no Forge bootstrap.
 */
class ScrollViewTest {

    /** A 100px window (y 50..150) onto 300px of content. */
    private static ScrollView view() {
        ScrollView view = new ScrollView();
        view.setViewport(50, 150);
        view.setContentHeight(300);
        return view;
    }

    @Test
    void contentThatFitsNeverScrolls() {
        ScrollView view = new ScrollView();
        view.setViewport(50, 150);
        view.setContentHeight(80);

        assertEquals(0, view.maxScroll());
        assertFalse(view.overflows(), "no scrollbar when the content already fits");

        view.scrollBy(40);
        assertEquals(0, view.scroll(), "scrolling is a no-op when everything is visible");
    }

    @Test
    void scrollClampsToBothEnds() {
        ScrollView view = view();
        assertEquals(200, view.maxScroll()); // 300 content - 100 viewport
        assertTrue(view.overflows());

        view.scrollBy(9999);
        assertEquals(200, view.scroll(), "cannot scroll past the end of the content");

        view.scrollBy(-9999);
        assertEquals(0, view.scroll(), "cannot scroll above the start of the content");
    }

    @Test
    void screenYTracksScroll() {
        ScrollView view = view();
        assertEquals(50, view.screenY(0), "unscrolled, content 0 sits at the viewport top");

        view.scrollBy(30);
        assertEquals(20, view.screenY(0));
        assertEquals(70, view.screenY(50));
    }

    @Test
    void fullVisibilityRequiresBothEdgesInside() {
        ScrollView view = view();

        assertTrue(view.isFullyVisible(20, 20), "wholly inside");
        assertTrue(view.isFullyVisible(0, 100), "exactly filling the viewport still counts");
        assertFalse(view.isFullyVisible(90, 20), "crosses the bottom edge");

        view.scrollBy(30);
        assertFalse(view.isFullyVisible(20, 20), "scrolled up past the top edge");
    }

    @Test
    void shrinkingContentRewindsAScrolledView() {
        ScrollView view = view();
        view.scrollBy(200);
        assertEquals(200, view.scroll());

        // A shorter card list (e.g. a quest was accepted) must not leave the view stranded past it.
        view.setContentHeight(120);
        assertEquals(20, view.scroll());

        view.setContentHeight(50);
        assertEquals(0, view.scroll(), "content now fits, so the view rewinds to the top");
        assertFalse(view.overflows());
    }

    @Test
    void growingViewportRewindsAScrolledView() {
        ScrollView view = view();
        view.scrollBy(200);

        // A window resize / GUI-scale change that reveals more must not strand the view either.
        view.setViewport(50, 300);
        assertEquals(50, view.scroll()); // 300 content - 250 viewport
    }

    @Test
    void thumbStaysInsideTheTrack() {
        ScrollView view = view();
        int height = view.thumbHeight();
        assertTrue(height > 0 && height <= view.viewportHeight());

        assertEquals(50, view.thumbTop(), "unscrolled thumb sits at the track top");

        view.scrollBy(200);
        assertEquals(150, view.thumbTop() + height, "fully scrolled thumb ends at the track bottom");
    }
}
