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

    /**
     * The original overflow report: with a full villager menu, later cards could not be reached at all.
     * Ten 60px cards in a 100px window is well past the point where the old fixed layout ran off-screen.
     */
    @Test
    void everyCardOfATenCardMenuCanBeScrolledIntoView() {
        int cards = 10;
        int cardHeight = 60;
        ScrollView view = new ScrollView();
        view.setViewport(50, 150);
        view.setContentHeight(cards * cardHeight);

        assertTrue(view.overflows(), "ten cards must not silently fit");
        for (int i = 0; i < cards; i++) {
            int cardTop = i * cardHeight;
            // Scroll from the top each time, the way a player wheels down to a specific card.
            view.scrollBy(-9999);
            view.scrollBy(cardTop);
            assertTrue(view.isFullyVisible(cardTop, cardHeight),
                    "card " + i + " of " + cards + " should be fully reachable by scrolling");
        }
    }

    /** A card scrolled out of the viewport must be reported hidden, which is what suppresses its buttons. */
    @Test
    void scrolledAwayCardsAreReportedHidden() {
        ScrollView view = new ScrollView();
        view.setViewport(50, 150);
        view.setContentHeight(600);

        // Scrolled to the very bottom: the first card is far above the viewport.
        view.scrollBy(9999);
        assertFalse(view.isFullyVisible(0, 60), "the first card must be hidden once scrolled past");
        assertTrue(view.screenY(0) < 50, "and must render above the viewport's top edge");

        // ...and the last card is now the visible one.
        assertTrue(view.isFullyVisible(540, 60), "the last card should be fully visible at the bottom");
    }

    /**
     * Tab moves focus to a widget that may be anywhere in the content, so the view has to follow it —
     * downward and upward — and must not move at all for one that is already visible.
     */
    @Test
    void scrollIntoViewMovesTheLeastItCan() {
        ScrollView view = view();

        view.scrollIntoView(20, 20);
        assertEquals(0, view.scroll(), "a band already inside the window does not move the view");

        // Below the fold: its bottom edge settles on the window's bottom edge.
        view.scrollIntoView(180, 20);
        assertEquals(100, view.scroll());
        assertTrue(view.isFullyVisible(180, 20));

        // Above it: its top edge settles on the window's top edge.
        view.scrollIntoView(40, 20);
        assertEquals(40, view.scroll());
        assertTrue(view.isFullyVisible(40, 20));
    }

    /** A band taller than the window cannot fit; it settles at its top, which is the half to read first. */
    @Test
    void scrollIntoViewPrefersTheTopOfAnOversizedBand() {
        ScrollView view = view();

        view.scrollIntoView(60, 150);
        assertEquals(60, view.scroll());
        assertEquals(50, view.screenY(60), "the band's top sits at the window's top");
    }

    /** Whatever it is asked for, it can never strand the view past the end of the content. */
    @Test
    void scrollIntoViewStaysInsideTheContent() {
        ScrollView view = view();

        view.scrollIntoView(280, 20);
        assertEquals(200, view.scroll(), "the very last band still leaves the view at maxScroll");

        view.scrollIntoView(-40, 20);
        assertEquals(0, view.scroll());
    }

    /**
     * A tiny window (as at GUI scale 4 on a small screen) leaves a very short viewport. Scrolling must
     * still be able to bring any card fully into view rather than leaving some permanently clipped.
     */
    @Test
    void aVeryShortViewportStillReachesEveryCard() {
        int cardHeight = 60;
        ScrollView view = new ScrollView();
        view.setViewport(40, 100); // 60px tall — exactly one card
        view.setContentHeight(10 * cardHeight);

        for (int i = 0; i < 10; i++) {
            view.scrollBy(-9999);
            view.scrollBy(i * cardHeight);
            assertTrue(view.isFullyVisible(i * cardHeight, cardHeight),
                    "card " + i + " should still be reachable in a one-card-tall viewport");
        }
    }
}
