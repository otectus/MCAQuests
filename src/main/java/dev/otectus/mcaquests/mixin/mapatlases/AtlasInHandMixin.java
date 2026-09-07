package dev.otectus.mcaquests.mixin.mapatlases;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.otectus.mcaquests.compat.mapatlases.AtlasHookManifest;
import dev.otectus.mcaquests.compat.mapatlases.client.AtlasHeldOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "pepjebs.mapatlases.client.AtlasInHandRenderer", remap = false)
public abstract class AtlasInHandMixin {
    @Inject(method = "render" + AtlasHookManifest.HAND, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/MapRenderer;render" + AtlasHookManifest.MAP_RENDER,
            shift = At.Shift.AFTER, remap = true), require = 0)
    private static void mcaquests$held(PoseStack pose, MultiBufferSource buffers, int light,
            ItemStack stack, Minecraft minecraft, CallbackInfo ci) {
        AtlasHeldOverlay.render(pose, buffers, light, stack);
    }
}
