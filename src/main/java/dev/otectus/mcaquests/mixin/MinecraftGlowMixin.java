package dev.otectus.mcaquests.mixin;

import dev.otectus.mcaquests.client.ClientHighlightData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the outline on villagers that are targets of the local player's active quests.
 *
 * <p>{@code LevelRenderer} decides whether to render an entity's outline by asking
 * {@code Minecraft#shouldEntityAppearGlowing}, so forcing that true is the whole feature — and it is
 * inherently per-player, because it never touches world state. That is the point: the previous
 * implementation applied a real {@code MobEffects.GLOWING} to the villager, which every player on the
 * server then saw.
 *
 * <p>A mixin is required rather than a client-side {@code Entity#setGlowingTag(true)}: in 1.20.1 that
 * method ends with {@code setSharedFlag(6, isCurrentlyGlowing())}, and {@code isCurrentlyGlowing()} on the
 * client reads shared flag 6 — so from the client it writes the flag back to its own value and does
 * nothing at all.
 *
 * <p>HEAD-injected and only ever forces {@code true}, so vanilla's own glow (spectator creeper outlines,
 * the Glowing effect) is never suppressed. The outline colour comes from {@code Entity#getTeamColor()},
 * which is white with no scoreboard team — deliberately left alone, since teams are global state shared
 * with every other mod.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void mcaquests$questTargetsGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClientHighlightData.isHighlighted(entity.getId())) {
            cir.setReturnValue(true);
        }
    }
}
