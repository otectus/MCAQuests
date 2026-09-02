package dev.otectus.mcaquests.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import java.util.OptionalInt;

/**
 * The scrollbar: textured, and draggable.
 *
 * <p>Three screens each carried a byte-identical private {@code renderScrollbar} — two
 * {@code fill()} rectangles, three pixels wide, translucent white on translucent white. None of them
 * responded to the mouse at all: the only way to move a long quest log was the wheel, and the bar was
 * decoration that happened to indicate position.
 *
 * <p>The geometry still comes from {@code ScrollView}, which stays free of Minecraft types so its
 * clamping rules remain unit-testable. This class takes that geometry as a {@link Geometry} snapshot
 * and hands back a scroll offset; it never holds a reference to the view. {@link #scrollForThumbTop}
 * is pure for the same reason.
 *
 * <p>Clicking the track jumps rather than paging, and does so by the same code path as a drag — the
 * thumb simply centres under the cursor and the drag continues from there, so press-and-sweep works
 * without a separate branch.
 */
public final class Scrollbar {

    /** Track width. Wide enough to grab, narrow enough not to eat content. */
    public static final int WIDTH = 6;

    /**
     * A snapshot of where the bar is and how far its content can travel.
     *
     * @param maxScroll how far the content can scroll; zero when it already fits
     */
    public record Geometry(int x, int top, int bottom, int thumbTop, int thumbHeight, int maxScroll) {

        public boolean overflows() {
            return maxScroll > 0;
        }

        /** Pixels the thumb itself can move between the ends of the track. */
        public int travel() {
            return Math.max(0, (bottom - top) - thumbHeight);
        }

        public boolean withinTrack(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < bottom;
        }

        public boolean overThumb(double mouseX, double mouseY) {
            return withinTrack(mouseX, mouseY) && mouseY >= thumbTop && mouseY < thumbTop + thumbHeight;
        }
    }

    private boolean dragging;
    private int grabOffset;

    /**
     * Begins a drag, or a jump-then-drag when the press lands on the track rather than the thumb.
     *
     * @return whether the press was consumed; the caller should not also treat it as a click on the
     *         content underneath
     */
    public boolean mouseClicked(double mouseX, double mouseY, Geometry geometry) {
        if (!geometry.overflows() || !geometry.withinTrack(mouseX, mouseY)) {
            return false;
        }
        grabOffset = geometry.overThumb(mouseX, mouseY)
                ? (int) Math.round(mouseY - geometry.thumbTop())
                : geometry.thumbHeight() / 2;
        dragging = true;
        return true;
    }

    /**
     * The scroll offset the cursor is asking for, while a drag is in progress.
     *
     * @return empty when no drag is under way, or when the content no longer overflows — a datapack
     *         reload can shorten the list mid-drag, and continuing to scroll a list that now fits
     *         would strand the view past its end
     */
    public OptionalInt scrollFor(double mouseY, Geometry geometry) {
        if (!dragging || !geometry.overflows()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(scrollForThumbTop((int) Math.round(mouseY - grabOffset), geometry));
    }

    public void release() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * The scroll offset that would put the thumb's top edge at {@code thumbTop}, clamped to the
     * content. Pure, so the ends of the travel are testable without a render context.
     */
    public static int scrollForThumbTop(int thumbTop, Geometry geometry) {
        int travel = geometry.travel();
        if (travel <= 0) {
            return 0;
        }
        long offset = (long) (thumbTop - geometry.top()) * geometry.maxScroll() / travel;
        return (int) Math.max(0, Math.min(geometry.maxScroll(), offset));
    }

    /** Draws nothing when the content fits, exactly as the three hand-rolled copies did. */
    public void render(GuiGraphics graphics, Geometry geometry, int mouseX, int mouseY) {
        if (!geometry.overflows()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiTextures.SCROLL_TRACK.nineSlice(graphics, geometry.x(), geometry.top(), WIDTH,
                geometry.bottom() - geometry.top());

        GuiTextures.Sprite thumb = GuiTextures.SCROLL_THUMB;
        if (dragging) {
            thumb = GuiTextures.SCROLL_THUMB_DRAG;
        } else if (geometry.overThumb(mouseX, mouseY)) {
            thumb = GuiTextures.SCROLL_THUMB_HOVER;
        }
        thumb.nineSlice(graphics, geometry.x(), geometry.thumbTop(), WIDTH, geometry.thumbHeight());
        RenderSystem.disableBlend();
    }
}
