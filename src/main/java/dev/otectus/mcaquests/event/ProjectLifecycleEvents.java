package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Project lifecycle hooks (spec 0.4.0): sponsor death handling and offline reward delivery on login.
 * Separate from {@code QuestProgressEvents.onGiverDeath} so quest behavior is unchanged.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ProjectLifecycleEvents {

    private ProjectLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onSponsorDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !McaCompat.isMcaVillager(event.getEntity())) {
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) {
            ProjectManager.onSponsorDeath(server, event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProjectManager.deliverPending(player);
        }
    }
}
