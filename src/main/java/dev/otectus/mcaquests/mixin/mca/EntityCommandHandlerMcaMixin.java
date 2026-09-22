package dev.otectus.mcaquests.mixin.mca;

import dev.otectus.mcaquests.compat.mca.McaDialogueHookEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes MCA opening its dialogue, for the {@code net.mca} package root (1.6.6).
 *
 * <p>One of four variants, exactly like the Gift hook: {@code McaGiftMixinPlugin} applies the one whose
 * root this MCA ships and skips the rest. {@code EntityCommandHandler.interactAt} is where MCA sends a
 * player its dialogue screen, whichever of {@code interactAt} or {@code mobInteract} the MCA build calls
 * it from, and its descriptor names only vanilla types, so the hook links nothing of MCA's.
 *
 * <p><b>Observe-only.</b> The injection runs at {@code RETURN}, never cancels and never changes the
 * return value, so MCA and any other mod hooking the same method behave exactly as without this one.
 * {@code this} crosses as an {@code Object}; {@code McaHandles} reads the villager out of it by name.
 */
@Mixin(targets = "net.mca.entity.interaction.EntityCommandHandler", remap = false)
public abstract class EntityCommandHandlerMcaMixin {

    @Inject(method = "interactAt(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"), remap = false, require = 0)
    private void mcaquests$dialogueOpened(Player player, Vec3 position, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        McaDialogueHookEvents.onDialogueOpened((Object) this, player, hand, cir.getReturnValue());
    }
}
