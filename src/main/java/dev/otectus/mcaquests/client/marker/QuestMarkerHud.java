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

    /** Half the indicator diamond, in GUI-scaled pixels. The diamond is 18 across. */
    private static final int DIAMOND_HALF = 9;
    /** How thick its dark outline is. */
    private static final int OUTLINE_WIDTH = 2;
    /** How long the chevron inside it is. */
    private static final int CHEVRON = 8;
    /** How far in from the indicator the distance is written, in GUI-scaled pixels. */
    private static final int DISTANCE_GAP = 16;
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
        int x = (int) Math.round(state.screenX());
        int y = (int) Math.round(state.screenY());
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

        // Inward from the edge, so the number is never the thing that falls off the screen.
        Component distance = Component.translatable("mcaquests.marker.edge_distance", state.roundedDistance());
        int width = minecraft.font.width(distance);
        double toCentreX = minecraft.getWindow().getGuiScaledWidth() / 2.0D - x;
        double toCentreY = minecraft.getWindow().getGuiScaledHeight() / 2.0D - y;
        double length = Math.max(Math.sqrt(toCentreX * toCentreX + toCentreY * toCentreY), 1.0E-4D);
        int textX = (int) Math.round(x + toCentreX / length * DISTANCE_GAP) - width / 2;
        int textY = (int) Math.round(y + toCentreY / length * DISTANCE_GAP)
                - minecraft.font.lineHeight / 2;
        graphics.drawString(minecraft.font, distance, textX, textY, 0xFFFFFFFF, true);
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
