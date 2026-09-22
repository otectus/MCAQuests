package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A project's build area, shown to the player who asked for it (1.6.6).
 *
 * <p>The geometry is the server's own: the same box or radius it tests every placement and kill
 * against, so what is drawn is what counts. It is outlined in the world for
 * {@code client.showBuildAreaSeconds}, and while it is shown a HUD line says whether the block the
 * player is looking at is inside — in words and a glyph, never colour alone. No map mod is needed.
 * An outline MCA could not supply exactly is labelled approximate rather than drawn with confidence.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class BuildAreaClient {

    private record Shown(Component title, ScopeGeometry geometry, long until) {
    }

    @Nullable
    private static Shown shown;

    private BuildAreaClient() {
    }

    /** Called with the server's answer to Show build area. */
    static void show(Component title, Component summary, ScopeGeometry geometry, List<Component> materials) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.player.displayClientMessage(Component.empty().append(title).append(Component.literal(": "))
                .append(summary), false);
        if (!materials.isEmpty()) {
            MutableComponent counts = Component.empty();
            for (int i = 0; i < materials.size(); i++) {
                if (i > 0) {
                    counts.append(Component.literal(", "));
                }
                counts.append(materials.get(i));
            }
            minecraft.player.displayClientMessage(Component.translatable("mcaquests.project.buildarea.counts", counts), false);
        }
        int seconds = McaQuestsConfig.CLIENT.showBuildAreaSeconds.get();
        shown = seconds <= 0 ? null : new Shown(title, geometry, minecraft.level.getGameTime() + seconds * 20L);
    }

    /** The area currently shown, if it has not expired and the player is still in its dimension. */
    @Nullable
    private static Shown active() {
        Minecraft minecraft = Minecraft.getInstance();
        if (shown == null || minecraft.level == null) {
            return null;
        }
        if (minecraft.level.getGameTime() > shown.until()) {
            shown = null;
            return null;
        }
        return minecraft.level.dimension().location().equals(shown.geometry().dimension()) ? shown : null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Shown current = active();
        if (current == null) {
            return;
        }
        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        ScopeGeometry geometry = current.geometry();
        if (geometry.effectiveBox().isPresent()) {
            BoundingBox box = geometry.effectiveBox().get();
            LevelRenderer.renderLineBox(pose, lines, new AABB(box.minX(), box.minY(), box.minZ(),
                    box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1), 0.25F, 0.85F, 0.35F, 1.0F);
        } else {
            int radius = geometry.radius() + (geometry.shape() == ScopeGeometry.Shape.VILLAGE_APPROXIMATE
                    ? geometry.margin() : 0);
            float y = (float) camera.getPosition().y - 1.5F;
            ring(pose, lines, geometry.anchor(), radius, y, geometry.exact());
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** A horizontal circle at the camera's feet; dashed when it is only an approximation. */
    private static void ring(PoseStack pose, VertexConsumer lines, BlockPos centre, int radius, float y, boolean exact) {
        int segments = Math.max(32, Math.min(256, radius));
        double cx = centre.getX() + 0.5D;
        double cz = centre.getZ() + 0.5D;
        for (int i = 0; i < segments; i++) {
            if (!exact && (i & 1) == 1) {
                continue;
            }
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = (float) (cx + Math.cos(a0) * radius);
            float z0 = (float) (cz + Math.sin(a0) * radius);
            float x1 = (float) (cx + Math.cos(a1) * radius);
            float z1 = (float) (cz + Math.sin(a1) * radius);
            float nx = x1 - x0;
            float nz = z1 - z0;
            float length = (float) Math.sqrt(nx * nx + nz * nz);
            if (length > 0) {
                nx /= length;
                nz /= length;
            }
            lines.addVertex(pose.last(), x0, y, z0).setColor(0.25F, 0.85F, 0.35F, 1.0F).setNormal(pose.last(), nx, 0, nz);
            lines.addVertex(pose.last(), x1, y, z1).setColor(0.25F, 0.85F, 0.35F, 1.0F).setNormal(pose.last(), nx, 0, nz);
        }
    }

    /** One HUD line while an area is shown: which project, and whether the looked-at block is inside. */
    public static final class Overlay implements LayeredDraw.Layer {
        @Override
        public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            Shown current = active();
            Minecraft minecraft = Minecraft.getInstance();
            if (current == null || minecraft.options.hideGui || minecraft.level == null) {
                return;
            }
            Component line;
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = block.getBlockPos().relative(block.getDirection());
                ScopeGeometry geometry = current.geometry();
                boolean inside = geometry.contains(minecraft.level.dimension().location(), pos);
                // A glyph as well as the words, so inside and outside never differ by colour alone.
                line = inside
                        ? Component.literal("\u2714 ").append(Component.translatable(geometry.exact()
                                ? "mcaquests.project.buildarea.inside"
                                : "mcaquests.project.buildarea.inside_approximate", current.title()))
                        : Component.literal("\u2716 ").append(Component.translatable(
                                "mcaquests.project.buildarea.outside", current.title(),
                                Math.max(1, geometry.blocksOutside(pos))));
            } else {
                line = Component.translatable("mcaquests.project.buildarea.look", current.title());
            }
            int x = (width - minecraft.font.width(line)) / 2;
            int y = height - 72;
            graphics.fill(x - 3, y - 2, x + minecraft.font.width(line) + 3, y + 10, 0x90000000);
            graphics.drawString(minecraft.font, line, x, y, 0xFFFFFF, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        shown = null;
    }

    static void clear() {
        shown = null;
    }
}
