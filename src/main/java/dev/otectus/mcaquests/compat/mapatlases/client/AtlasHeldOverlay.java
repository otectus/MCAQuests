package dev.otectus.mcaquests.compat.mapatlases.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.marker.MarkerColours;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.compat.mapatlases.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import java.util.*;

/** First-person rendering uses the caller's map-space pose and buffers, never GUI coordinates. */
public final class AtlasHeldOverlay {
    private static final RenderType INK = RenderType.text(ResourceLocation.fromNamespaceAndPath("mcaquests", "textures/gui/atlas_ink.png"));
    private static long observedAt;
    private static int visible;
    private AtlasHeldOverlay() { }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack) {
        var runtime = AtlasRuntime.get();
        var config = McaQuestsConfig.CLIENT;
        if (runtime == null || !AtlasHookState.handApplied() || !config.mapWaypoints.get()
                || !config.mapAtlasesWaypoints.get() || !config.mapAtlasesInHand.get()) return;
        try {
            var selected = runtime.binding().heldTile(stack);
            if (selected.isEmpty()) return;
            var tile = selected.get();
            var data = tile.data();
            if (data == null || data.scale < 0 || data.scale > 4) return;
            var snapshot = runtime.store().snapshot();
            Set<Long> occupied = new HashSet<>();
            int drawn = 0;
            for (WaypointSpec spec : snapshot.near(data.dimension, data.centerX, data.centerZ, data.scale)) {
                var position = runtime.position(spec);
                var point = AtlasProjection.project(position.x, position.z, data.centerX, data.centerZ, data.scale);
                if (!AtlasProjection.owns(point) || !AtlasQuestOverlay.policy(spec, tile, position.x, position.z).equals("ready")) continue;
                long cell = ((long)(point.x() / 7) << 32) | (long)(point.y() / 7);
                if (!occupied.add(cell)) continue;
                if (drawn >= config.mapAtlasesMinimapBudget.get()) break;
                pose.pushPose();
                try {
                    pose.translate(point.x(), point.y(), -0.1);
                    float scale = config.mapAtlasesMarkerScale.get().floatValue();
                    pose.scale(scale, scale, 1);
                    // The held map has no HUD rotation/zoom. Applying its stale thread-local scale here
                    // would make the pin depend on which other atlas surface rendered most recently.
                    float radius = spec.presentation().primary() ? 3.5f : 2.75f;
                    float left = Math.max(-radius-1, (float)-point.x()/scale);
                    float top = Math.max(-radius-1, (float)-point.y()/scale);
                    float right = Math.min(radius+1, (float)(128-point.x())/scale);
                    float bottom = Math.min(radius+1, (float)(128-point.y())/scale);
                    quad(pose, buffers, light, left, top, right, bottom, 0xFF111118, 0);
                    quad(pose, buffers, light, Math.max(left,-radius), Math.max(top,-radius),
                            Math.min(right,radius), Math.min(bottom,radius), 0xFF000000 | MarkerColours.of(spec.kind()), -0.01f);
                    if (spec.presentation().approximate() || spec.presentation().lastKnown())
                        quad(pose, buffers, light, Math.max(left,-radius+1), Math.max(top,-radius+1),
                                Math.min(right,radius-1), Math.min(bottom,radius-1), 0xFF111118, -0.02f);
                    if (point.x() > 5*scale && point.x() < 128-5*scale && point.y() > 5*scale && point.y() < 128-5*scale) {
                        pose.translate(0,0,-0.03);
                        pose.scale(.5f,.5f,1);
                        var font = Minecraft.getInstance().font;
                        String mark = spec.presentation().lastKnown() ? "?" : spec.presentation().approximate() ? "~"
                                : MarkerColours.initials(spec.kind());
                        font.drawInBatch(mark, -font.width(mark)/2f, -4, -1, false, pose.last().pose(),
                                buffers, Font.DisplayMode.NORMAL, 0, light);
                    }
                } finally { pose.popPose(); }
                drawn++;
            }
            visible = drawn; observedAt = System.currentTimeMillis();
            AtlasHookState.observedHand(drawn > 0);
        } catch (RuntimeException | LinkageError error) { runtime.failed("held render", error); }
    }
    private static void quad(PoseStack pose, MultiBufferSource buffers, int light,
            float left, float top, float right, float bottom, int color, float z) {
        if (left >= right || top >= bottom) return;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer ink = buffers.getBuffer(INK);
        ink.addVertex(matrix,left,bottom,z).setColor(color).setUv(0,1).setLight(light);
        ink.addVertex(matrix,right,bottom,z).setColor(color).setUv(1,1).setLight(light);
        ink.addVertex(matrix,right,top,z).setColor(color).setUv(1,0).setLight(light);
        ink.addVertex(matrix,left,top,z).setColor(color).setUv(0,0).setLight(light);
    }
    public static int visible() { return System.currentTimeMillis()-observedAt <= 250 ? visible : 0; }
    public static void reset() { visible = 0; observedAt = 0; }
}
