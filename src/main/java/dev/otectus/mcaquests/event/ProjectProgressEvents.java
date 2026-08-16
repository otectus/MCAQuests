package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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

    // Project talk credit is dispatched by QuestEventHandlers, which owns the single empty-hand /
    // non-canceled "this counts as a conversation" gate for both quest and project objectives. Having a
    // second EntityInteract listener here would have applied a different (looser) rule to projects.
}
