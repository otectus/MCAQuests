package dev.otectus.mcaquests.client.marker;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.ClientGuidanceData;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * Draws the one place the tracked quest is sending the player: a beam of light, an icon, and a label.
 *
 * <p>Before this the mod's only navigation aid was a line of text on the tracker, so "escort them
 * home" or "enter an ancient city" was an instruction the player had to translate into a direction
 * themselves. The beam is the translation.
 *
 * <p>Three deliberate constraints:
 *
 * <ul>
 *   <li><b>Exactly one.</b> The server sends one target; there is no list here to filter, and no way
 *       for the world to fill up with markers as the player takes on more quests.</li>
 *   <li><b>Through walls, but not <em>over</em> them.</b> Depth testing is off, so the beam is visible
 *       from inside a cave — the whole point is finding somewhere you cannot see. It is translucent
 *       and a quarter of a block wide so it never becomes a wall of its own.</li>
 *   <li><b>It gets out of the way.</b> {@link MarkerGeometry#alpha} fades it to nothing inside the
 *       objective's arrival radius, so walking up to the bed removes the marker rather than leaving
 *       it standing in the player's face while they try to see what they came for.</li>
 * </ul>
 *
 * <p>An entity-backed target follows the live entity by id, so an escortee's marker moves smoothly
 * between the server's once-a-second recomputes rather than teleporting each time.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestMarkerRenderer {

    /** How wide the floating icon is, in blocks. */
    private static final float ICON_SIZE = 0.9F;
    /** Text is authored at 1/40th of a block per pixel, matching vanilla's own name tags. */
    private static final float TEXT_SCALE = 0.025F;

    private QuestMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // After particles: past everything solid, so the beam blends over the world rather than being
        // blended into by it, and before the clouds and weather that should still draw in front.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!McaQuestsConfig.CLIENT.showQuestMarker.get()) {
            return;
        }
        // The primary entry, never the whole list: the snapshot names a destination per quest so the
        // tracker can print them all, and the server marks exactly one of them as the beam's. Drawing
        // the list would be five beacons for five quests, which is the mistake highlighting made.
        Optional<ActiveGuidance> guidance = ClientGuidanceData.primary();
        if (guidance.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        GuidanceTarget target = guidance.get().target();
        // A marker for another dimension would be drawn at a coordinate that means nothing here — the
        // Nether's are the overworld's divided by eight. Objectives answer with a route instead.
        if (!minecraft.level.dimension().equals(target.dimension())) {
            return;
        }
        Vec3 at = position(minecraft, target, event.getPartialTick());
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double distance = MarkerGeometry.horizontalDistance(at.x - eye.x, at.z - eye.z);
        float alpha = MarkerGeometry.alpha(distance, target.arriveRadius(),
                McaQuestsConfig.CLIENT.questMarkerMaxDistance.get());
        if (alpha <= 0.0F) {
            return;
        }
        draw(minecraft, event.getPoseStack(), camera, at.subtract(eye), target, alpha, distance);
    }

    /**
     * Where to draw: the live entity when the target is one and it is loaded, otherwise the position
     * the server sent.
     *
     * <p>Interpolated with the partial tick for an entity, so the marker rides on a walking villager
     * instead of stepping twenty times a second. The sent position is the fallback for a villager who
     * has since left render distance, which is exactly when the marker is most worth having.
     */
    private static Vec3 position(Minecraft minecraft, GuidanceTarget target, float partialTick) {
        if (target.entityId().isPresent() && minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(target.entityId().getAsInt());
            if (entity != null) {
                return new Vec3(entity.getX(),
                        entity.getY(partialTick) + entity.getBbHeight(),
                        entity.getZ());
            }
        }
        return Vec3.atBottomCenterOf(target.pos());
    }

    private static void draw(Minecraft minecraft, PoseStack pose, Camera camera, Vec3 relative,
                             GuidanceTarget target, float alpha, double distance) {
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int colour = MarkerColours.of(target.kind());
        pose.pushPose();
        pose.translate(relative.x, relative.y, relative.z);

        beam(pose, buffers, colour, alpha);

        // The icon and the label both face the camera, which is the same billboard vanilla builds for
        // a name tag: undo the camera's rotation, then draw in the XY plane.
        pose.pushPose();
        pose.translate(0.0D, MarkerGeometry.labelHeight(distance), 0.0D);
        pose.mulPose(camera.rotation());
        icon(pose, buffers, target, alpha);
        label(minecraft.font, pose, buffers, target, distance, alpha);
        pose.popPose();

        pose.popPose();
        buffers.endBatch();
    }

    /** A translucent column standing on the target, brightest at the base and fading out with height. */
    private static void beam(PoseStack pose, MultiBufferSource buffers, int colour, float alpha) {
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.BEAM);
        Matrix4f matrix = pose.last().pose();
        float half = (float) MarkerGeometry.BEAM_WIDTH / 2.0F;
        float top = (float) MarkerGeometry.BEAM_HEIGHT;
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int baseAlpha = (int) (alpha * 190.0F);
        // Two crossed quads rather than four sides: from any angle at least one is near-face-on, and a
        // box would read as a solid block of colour from a corner.
        quad(builder, matrix, -half, 0.0F, -half, half, top, half, r, g, b, baseAlpha);
        quad(builder, matrix, -half, 0.0F, half, half, top, -half, r, g, b, baseAlpha);
    }

    /** One vertical quad from ({@code x0},{@code z0}) to ({@code x1},{@code z1}), fading toward the top. */
    private static void quad(VertexConsumer builder, Matrix4f matrix, float x0, float y0, float z0,
                             float x1, float y1, float z1, int r, int g, int b, int alpha) {
        int faded = alpha / 6;
        builder.vertex(matrix, x0, y0, z0).color(r, g, b, alpha).endVertex();
        builder.vertex(matrix, x1, y0, z1).color(r, g, b, alpha).endVertex();
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, faded).endVertex();
        builder.vertex(matrix, x0, y1, z0).color(r, g, b, faded).endVertex();
    }

    /** The kind's glyph off {@code icons.png}, billboarded above the beam. */
    private static void icon(PoseStack pose, MultiBufferSource buffers, GuidanceTarget target, float alpha) {
        GuiTextures.Sprite sprite = MarkerIcons.of(target.kind());
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.ICON);
        Matrix4f matrix = pose.last().pose();
        float half = ICON_SIZE / 2.0F;
        float u0 = sprite.u() / (float) GuiTextures.SHEET;
        float u1 = (sprite.u() + sprite.width()) / (float) GuiTextures.SHEET;
        float v0 = sprite.v() / (float) GuiTextures.SHEET;
        float v1 = (sprite.v() + sprite.height()) / (float) GuiTextures.SHEET;
        int a = (int) (alpha * 255.0F);
        // The billboard's Y runs up the screen while the sheet's V runs down it, so the V pair is
        // swapped here rather than the quad being wound the other way round.
        builder.vertex(matrix, -half, -half, 0.0F).color(255, 255, 255, a).uv(u0, v1).endVertex();
        builder.vertex(matrix, half, -half, 0.0F).color(255, 255, 255, a).uv(u1, v1).endVertex();
        builder.vertex(matrix, half, half, 0.0F).color(255, 255, 255, a).uv(u1, v0).endVertex();
        builder.vertex(matrix, -half, half, 0.0F).color(255, 255, 255, a).uv(u0, v0).endVertex();
    }

    /** The target's name and how far it is, under the icon, in vanilla's see-through name-tag style. */
    private static void label(Font font, PoseStack pose, MultiBufferSource buffers, GuidanceTarget target,
                              double distance, float alpha) {
        Component text = dev.otectus.mcaquests.client.GuidanceText.markerLabel(target, distance);
        pose.pushPose();
        // Scale is negative on both axes because the billboard we are inside is the camera's frame,
        // in which +Y is down and text would otherwise render mirrored and upside down.
        pose.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        pose.translate(0.0D, -ICON_SIZE / TEXT_SCALE / 2.0D - font.lineHeight, 0.0D);
        int textAlpha = (int) (alpha * 255.0F) << 24;
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, 0xFFFFFF | textAlpha, false,
                pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * The two render types the marker needs, neither of which vanilla exposes.
     *
     * <p>{@code RenderType.create} and the state shards it takes are protected, so reaching them means
     * extending {@link RenderType} — the standard route, and the reason this class exists at all. Both
     * types disable depth testing, which is the whole feature: a marker you can only see when you can
     * already see the place is not a marker.
     */
    private static final class MarkerRenderTypes extends RenderType {

        /**
         * Never called. The class exists only to be a subclass, which is the access this needs; it is
         * declared so that no compiler-generated public constructor suggests otherwise.
         */
        private MarkerRenderTypes() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false,
                    () -> {
                    }, () -> {
                    });
        }

        private static final RenderType BEAM = RenderType.create(
                McaQuests.MOD_ID + ":marker_beam", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));

        private static final RenderType ICON = RenderType.create(
                McaQuests.MOD_ID + ":marker_icon", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                GuiTextures.ICON_SHEET, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }
}
