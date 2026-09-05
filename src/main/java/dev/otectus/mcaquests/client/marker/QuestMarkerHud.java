package dev.otectus.mcaquests.client.marker;

import com.mojang.math.Axis;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.gui.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The part of the marker that is drawn flat on the screen: an arrow at the edge for a target that is
 * off it, and the glyph itself in the rare case the world one would be too small to read.
 *
 * <p>Without this, a target the player is not facing is simply not drawn, and the marker answers
 * "where" only once the player has already guessed which way to look. An arrow on the edge answers it
 * from anywhere.
 *
 * <p>Everything it draws comes out of {@link MarkerFrameState}, which the world renderer filled in
 * earlier the same frame — this event has no projection matrix of its own and must not invent one.
 * A frame the renderer did not publish draws nothing at all, so a marker cannot linger for a frame
 * after the quest that owned it ended.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestMarkerHud {

    /**
     * Half the indicator diamond, in GUI-scaled pixels. The diamond is 18 across.
     *
     * <p>Public because the safe rectangle is inset by the configured amount plus this: an inset that
     * places the centre of the diamond leaves half of it outside the inset the player asked for.
     */
    public static final int DIAMOND_HALF = 9;
    /** How thick its dark outline is. */
    private static final int OUTLINE_WIDTH = 2;
    /** How long the chevron inside it is. */
    private static final int CHEVRON = 8;
    /** How far in from the indicator the distance is written, in GUI-scaled pixels. */
    private static final int DISTANCE_GAP = 16;
    /** The gap between the diamond and the distance beside it, in GUI-scaled pixels. */
    private static final int LABEL_GAP = 3;
    /** How close the label may come to the window's own edge, in GUI-scaled pixels. */
    private static final int SCREEN_MARGIN = 2;
    /** How long the debug direction lines are, in GUI-scaled pixels. */
    private static final int DEBUG_LINE = 60;
    /** The raw bearing, and the filtered one drawn over it. */
    private static final int DEBUG_RAW = 0xFFFF5555;
    private static final int DEBUG_FILTERED = 0xFF55FF55;
    /** The glyph the HUD fallback draws, in GUI-scaled pixels; the sheet's own art is 16. */
    private static final float HUD_GLYPH_SCALE = 18.0F / 16.0F;

    /** The dark the indicator is outlined in. Not black: black on a night sky is a hole. */
    private static final int OUTLINE = 0xFF101820;

    private QuestMarkerHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        MarkerFrameState state = MarkerFrameState.CURRENT;
        if (!state.consume()) {
            return;
        }
        if (state.alpha() <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        if (!Double.isFinite(state.screenX()) || !Double.isFinite(state.screenY())) {
            return; // never turn a NaN into a cast integer coordinate
        }
        int x = (int) Math.round(state.screenX());
        int y = (int) Math.round(state.screenY());
        if (EdgeIndicatorDebug.ENABLED) {
            debugOverlay(graphics, minecraft, state);
        }
        if (state.edgeActive()) {
            indicator(graphics, minecraft, state, x, y);
        } else if (state.hudGlyph()) {
            // The six-block cap has made the world glyph smaller than it is legible at, which happens
            // at a very wide field of view or a very small window. Drawn flat at its true projected
            // position instead, so it is still where the target is.
            Panel.iconScaled(graphics, MarkerIcons.of(state.kind()),
                    x - DIAMOND_HALF, y - DIAMOND_HALF, HUD_GLYPH_SCALE);
        }
    }

    /** The diamond, the chevron pointing at the target, and how far away it is. */
    private static void indicator(GuiGraphics graphics, Minecraft minecraft, MarkerFrameState state,
                                  int x, int y) {
        int alpha = (int) (Math.min(state.alpha(), 1.0F) * 0.90F * 255.0F) << 24;
        int fill = (state.rgb() & 0xFFFFFF) | alpha;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0F);
            diamond(graphics, DIAMOND_HALF, OUTLINE);
            diamond(graphics, DIAMOND_HALF - OUTLINE_WIDTH, fill);
            graphics.pose().pushPose();
            try {
                graphics.pose().mulPose(Axis.ZP.rotation((float) state.angleRadians()));
                chevron(graphics, OUTLINE);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            graphics.pose().popPose();
        }

        if (!MarkerSettings.current().edgeShowDistance()) {
            return;
        }
        distance(graphics, minecraft, state, x, y);
    }

    /**
     * How far away the target is, written inward from whichever edge the arrow is on.
     *
     * <p>Placed by the edge rather than by the angle back to the centre. The two agree in the middle
     * of an edge and disagree in a corner, which is exactly where the old placement pushed half the
     * number off the screen: an arrow in the top-right corner wants its label below it or to its left,
     * not diagonally inward through the icon. The whole text box is then clamped inside the window, so
     * a long number on a small GUI scale still reads.
     */
    private static void distance(GuiGraphics graphics, Minecraft minecraft, MarkerFrameState state,
                                 int x, int y) {
        Component distance = Component.translatable("mcaquests.marker.edge_distance", state.roundedDistance());
        int width = minecraft.font.width(distance);
        int height = minecraft.font.lineHeight;
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();

        int textX;
        int textY;
        switch (state.edgeSide()) {
            case LEFT -> {
                textX = x + DIAMOND_HALF + LABEL_GAP;
                textY = y - height / 2;
            }
            case RIGHT -> {
                textX = x - DIAMOND_HALF - LABEL_GAP - width;
                textY = y - height / 2;
            }
            case TOP -> {
                textX = x - width / 2;
                textY = y + DIAMOND_HALF + LABEL_GAP;
            }
            case BOTTOM -> {
                textX = x - width / 2;
                textY = y - DIAMOND_HALF - LABEL_GAP - height;
            }
            default -> {
                // No edge was named: the pre-1.5.4 placement, straight back toward the centre.
                double toCentreX = guiWidth / 2.0D - x;
                double toCentreY = guiHeight / 2.0D - y;
                double length = Math.max(Math.sqrt(toCentreX * toCentreX + toCentreY * toCentreY), 1.0E-4D);
                textX = (int) Math.round(x + toCentreX / length * DISTANCE_GAP) - width / 2;
                textY = (int) Math.round(y + toCentreY / length * DISTANCE_GAP) - height / 2;
            }
        }
        textX = Math.max(SCREEN_MARGIN, Math.min(textX, guiWidth - width - SCREEN_MARGIN));
        textY = Math.max(SCREEN_MARGIN, Math.min(textY, guiHeight - height - SCREEN_MARGIN));
        graphics.drawString(minecraft.font, distance, textX, textY, 0xFFFFFFFF, true);
    }

    /**
     * What the indicator was thinking, for {@code /mcaquestsclient debug marker}.
     *
     * <p>Two lines from the centre of the screen -- the raw bearing and the filtered one -- and the
     * mode and edge in words. When they disagree the arrow is mid-swing; when the raw one jitters and
     * the filtered one does not, the filter is doing its job.
     */
    private static void debugOverlay(GuiGraphics graphics, Minecraft minecraft,
                                     MarkerFrameState state) {
        int centreX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int centreY = minecraft.getWindow().getGuiScaledHeight() / 2;
        ray(graphics, centreX, centreY, EdgeIndicatorDebug.rawX(), EdgeIndicatorDebug.rawY(), DEBUG_RAW);
        ray(graphics, centreX, centreY, EdgeIndicatorDebug.filteredX(), EdgeIndicatorDebug.filteredY(),
                DEBUG_FILTERED);
        Component line = Component.translatable("mcaquests.marker.debug.state",
                EdgeIndicatorDebug.mode().name(), EdgeIndicatorDebug.side().name(),
                String.format("%.0f, %.0f", state.screenX(), state.screenY()));
        graphics.drawString(minecraft.font, line, centreX + 8, centreY + 8, 0xFFFFFFFF, true);
    }

    /** A one-pixel line from the centre along a unit direction, drawn as its own points. */
    private static void ray(GuiGraphics graphics, int centreX, int centreY,
                            double dirX, double dirY, int colour) {
        if (!Double.isFinite(dirX) || !Double.isFinite(dirY)) {
            return;
        }
        for (int i = 0; i < DEBUG_LINE; i++) {
            int px = centreX + (int) Math.round(dirX * i);
            int py = centreY + (int) Math.round(dirY * i);
            graphics.fill(px, py, px + 1, py + 1, colour);
        }
    }

    /** A filled diamond centred on the current pose origin, drawn as its rows. */
    private static void diamond(GuiGraphics graphics, int half, int colour) {
        for (int dy = -half; dy <= half; dy++) {
            int width = half - Math.abs(dy);
            if (width <= 0) {
                continue;
            }
            graphics.fill(-width, dy, width, dy + 1, colour);
        }
    }

    /** An arrowhead pointing along +X, which the pose rotation turns toward the target. */
    private static void chevron(GuiGraphics graphics, int colour) {
        for (int i = 0; i < CHEVRON; i++) {
            int half = (CHEVRON - i) / 2;
            if (half <= 0) {
                break;
            }
            graphics.fill(i - CHEVRON / 2, -half, i - CHEVRON / 2 + 1, half, colour);
        }
    }
}
