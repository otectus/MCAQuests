package dev.otectus.mcaquests.mixin.mapatlases;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import dev.otectus.mcaquests.compat.mapatlases.AtlasHookManifest;
import dev.otectus.mcaquests.compat.mapatlases.client.AtlasQuestOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "pepjebs.mapatlases.client.AbstractAtlasWidget", remap = false)
public abstract class AtlasDisplayMixin {
    @Unique private AtlasQuestOverlay.Frame mcaquests$frame;

    @Inject(method = "drawAtlas" + AtlasHookManifest.DRAW, at = @At("HEAD"), require = 0)
    private void mcaquests$begin(GuiGraphics graphics, int x, int y, int width, int height,
            Player player, float zoom, boolean borders, @Coerce Object type, int light,
            MapItemSavedData selected, CallbackInfo ci) {
        mcaquests$frame = AtlasQuestOverlay.begin(this, graphics, x, y, width, height);
    }

    // Upstream names stay literal. Minecraft invocation targets alone are refmap-remapped.
    @Inject(method = "drawMap" + AtlasHookManifest.TILE, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", remap = true), require = 0)
    private void mcaquests$capture(Player player, PoseStack pose, MultiBufferSource.BufferSource buffers,
            Pair<?, ?> outlines, int ix, int iy, @Coerce Object holder, boolean drawPlayers,
            int light, MapItemSavedData selected, CallbackInfo ci) {
        AtlasQuestOverlay.capture(mcaquests$frame, holder, pose);
    }

    @Inject(method = "drawAtlas" + AtlasHookManifest.DRAW, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
            ordinal = 0, shift = At.Shift.AFTER, remap = true), require = 0)
    private void mcaquests$overlay(GuiGraphics graphics, int x, int y, int width, int height,
            Player player, float zoom, boolean borders, @Coerce Object type, int light,
            MapItemSavedData selected, CallbackInfo ci) {
        AtlasQuestOverlay.render(mcaquests$frame, graphics);
    }

    @Inject(method = "drawAtlas" + AtlasHookManifest.DRAW, at = @At("RETURN"), require = 0)
    private void mcaquests$end(GuiGraphics graphics, int x, int y, int width, int height,
            Player player, float zoom, boolean borders, @Coerce Object type, int light,
            MapItemSavedData selected, CallbackInfo ci) {
        mcaquests$frame = null;
    }
}
