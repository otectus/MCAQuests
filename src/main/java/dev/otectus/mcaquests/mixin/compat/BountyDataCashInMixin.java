package dev.otectus.mcaquests.mixin.compat;

import dev.otectus.mcaquests.compat.bountiful.BountifulHookEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes a Bountiful bounty being cashed in. The mod's first common mixin, and its only one that
 * targets another mod.
 *
 * <p><b>Why a mixin at all.</b> Bountiful publishes no completion callback — its
 * {@code BountifulSharedApi} is a loader-platform bridge, not an event API — so a quest that asks a
 * player to finish bounties has nothing to listen to. Everything else this integration does (pools,
 * decrees, the board, rarity) is ordinary data and reflection; this one fact is not readable any other
 * way.
 *
 * <p><b>It only ever watches.</b> Neither injection cancels, and neither touches the return value:
 * the callback is read, never written. Bountiful decides what happens to the bounty exactly as it
 * would with this mod absent, and if the whole hook were removed the only difference to a player would
 * be a quest that stops counting.
 *
 * <p><b>Why the work is elsewhere.</b> The two handlers are one line each, into
 * {@link BountifulHookEvents}. A mixin class is compiled into somebody else's class: nothing in it can
 * be unit-tested, it does not read as itself in a stack trace, and every type it names is loaded on
 * the transformer's terms rather than the game's. The head handler exists at all because a cash-in
 * consumes the bounty item, so the rarity has to be read before the method runs and the result is only
 * known after it.
 *
 * <p><b>Everything is unmapped.</b> {@code remap = false} throughout, because the target belongs to
 * another mod and its member names are not in Minecraft's mapping — while the descriptor's vanilla
 * types are safe in both dev and production, since Forge only renames members, never classes. Both
 * injections carry {@code require = 0}: this whole configuration is optional, and
 * {@code BountifulMixinPlugin} both decides whether it applies and verifies afterwards that it did.
 */
@Mixin(targets = "io.ejekta.bountiful.bounty.BountyData", remap = false)
public abstract class BountyDataCashInMixin {

    @Inject(method = "tryCashIn(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), remap = false, require = 0)
    private void mcaquests$beforeCashIn(Player player, ItemStack stack,
                                        CallbackInfoReturnable<Boolean> cir) {
        BountifulHookEvents.beforeCashIn((Object) this, player, stack);
    }

    @Inject(method = "tryCashIn(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"), remap = false, require = 0)
    private void mcaquests$afterCashIn(Player player, ItemStack stack,
                                       CallbackInfoReturnable<Boolean> cir) {
        BountifulHookEvents.afterCashIn((Object) this, player, stack, cir.getReturnValueZ());
    }
}
