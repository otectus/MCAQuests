package dev.otectus.mcaquests.mixin.mca;

import dev.otectus.mcaquests.compat.mca.McaGiftHookEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes MCA's Gift gesture into a quest delivery, for the {@code net.conczin.mca} package root
 * (un-merged layout, renamed base package).
 *
 * <p><b>One class per root, and only one of them can ever apply.</b> MCA has repackaged twice and the
 * two axes -- Forgix merge and base package name -- vary independently, so all four combinations are
 * shipped and {@code McaGiftMixinPlugin} decides which has a class to attach to. The other three are
 * skipped with a reason; that is the healthy state, not a failure.
 *
 * <p><b>Why {@code handle} and not {@code giveGift}.</b> {@code giveGift} takes MCA's own
 * {@code Memories} type, which would put an MCA type in this class's constant pool and trip the
 * standing static-link tripwire. {@code handle(ServerPlayer, String)Z} is declared by
 * {@code VillagerCommandHandler} in every probed build and its descriptor carries nothing but vanilla
 * types, so the hook can exist at all without linking MCA. It is also the earlier boundary: the
 * command is routed before MCA classifies the gift, which is what lets a requested cake pay a quest
 * instead of triggering pregnancy.
 *
 * <p><b>What cancelling means.</b> On any answer other than {@code PASS_THROUGH} the return value is
 * set to {@code true} -- exactly what MCA's own gift branch returns -- and the call is cancelled, so
 * MCA awards no hearts, no mood and no saturation for an item the quest has taken as payment. Every
 * other command returns {@code PASS_THROUGH} before anything is resolved, so nothing else MCA does is
 * touched. Another mod injecting into the same method still sees the call; a HEAD injector of theirs
 * runs, and their later injectors do not when this one cancels.
 *
 * <p><b>Nothing here is typed.</b> {@code this} is handed over as {@code Object}, the target is a
 * dotted string, and {@code remap = false} throughout -- the member names belong to another mod and
 * are not in Minecraft's mapping, while the descriptor's vanilla types are safe in dev and production
 * alike because Forge renames members, never classes. {@code require = 0} because three of the four
 * variants legitimately have no target.
 */
@Mixin(targets = "net.conczin.mca.entity.interaction.VillagerCommandHandler", remap = false)
public abstract class VillagerCommandHandlerConczinMixin {

    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void mcaquests$routeGift(ServerPlayer player, String command,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (McaGiftHookEvents.handle((Object) this, player, command).handled()) {
            cir.setReturnValue(true);
        }
    }
}
