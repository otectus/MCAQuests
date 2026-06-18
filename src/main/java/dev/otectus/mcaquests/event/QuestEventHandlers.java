package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.QuestMenuService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus gameplay listeners. Phase 0 ships only a debug fallback (spec section 6): sneak-right-
 * click an MCA villager to open the quest menu, so the networking + screen pipeline can be exercised
 * before the MCA-menu button mixin exists. The mixin button — not this — is the real user-facing path.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestEventHandlers {

    private QuestEventHandlers() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return; // fire once, not per-hand
        }
        if (!event.getEntity().isShiftKeyDown()) {
            return; // debug trigger is sneak-right-click only
        }
        if (!McaCompat.isMcaVillager(event.getTarget())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestMenuService.open(player, event.getTarget());
            event.setCanceled(true); // swallow this debug interaction so MCA does not also react
        }
    }
}
