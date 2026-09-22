package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.quest.escort.EscortHoldRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Project lifecycle hooks (spec 0.4.0): sponsor death handling, offline reward delivery on login, and
 * teardown of per-session project state when the server stops.
 * Separate from {@code QuestProgressEvents.onGiverDeath} so quest behavior is unchanged.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ProjectLifecycleEvents {

    private ProjectLifecycleEvents() {
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
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

    /**
     * Clears state that only makes sense for one running server. On a single-player client the JVM
     * survives leaving a world, so anything keyed on game time has to be dropped here or the next
     * world inherits it.
     */
    /**
     * Opens this world's outage ledger and records which definitions loaded (1.7.0). After the initial
     * datapack load and before anyone can join, so the first poll already has the answer.
     */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        dev.otectus.mcaquests.state.ContentOutageData.attach(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        dev.otectus.mcaquests.state.ContentOutageData.detach();
        ProjectManager.clearSessionState();
        // Escort leases are saved with the world (1.7.0); stop tracking this one without forgetting them.
        EscortHoldRegistry.detach();
    }
}
