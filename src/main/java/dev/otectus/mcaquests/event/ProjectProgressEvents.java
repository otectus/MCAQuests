package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side credit for event-driven <em>project</em> objectives — kills, block placement, and
 * profession talks happening inside an active project's village/scope (spec 0.4.0). Kept separate from
 * {@code QuestProgressEvents} so the per-player quest path is untouched; Forge dispatches to both.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ProjectProgressEvents {

    private ProjectProgressEvents() {
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            ProjectManager.onProjectKill(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProjectManager.onProjectPlace(player, event.getPlacedBlock(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onTalkToVillager(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && McaCompat.isMcaVillager(event.getTarget())) {
            ProjectManager.onProjectTalk(player, event.getTarget());
        }
    }
}
