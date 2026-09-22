package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * When the compat registry re-asks its questions.
 *
 * <p>Two hooks, both on the FORGE bus. {@link AddReloadListenerEvent} fires on world load and on
 * {@code /reload}, <b>before</b> quest JSON is parsed and after registries have frozen, which is the
 * only moment at which "does this entity exist?" is both answerable and still useful — the quarantine
 * and every tolerant target are decided during that parse. It also carries the reload's
 * {@code RegistryAccess}, which is the only way to reach dynamic registries such as structures.
 * {@link ServerAboutToStartEvent} covers a dedicated server, where the first world load is the only
 * load and a provider that bound during mod construction may want a second look.
 *
 * <p>Highest priority on the reload hook so the re-probe finishes before {@code QuestDataLoader} adds
 * its listener and starts asking.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class CompatLifecycleEvents {

    private CompatLifecycleEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        CompatRegistry.get().reprobeAll("reload", event.getRegistryAccess());
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        CompatRegistry.get().reprobeAll("server_start", null);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Reload listeners can consult an old definition during preparation. Clear once more after
        // every loader has applied; a login's sync is harmless and does not rebind third-party classes.
        TownsteadBridge.Holder.get().invalidateDataCaches();
        if (event.getPlayer() == null) {
            // A /reload can load or drop definitions; the outage ledger records which (1.7.0).
            net.minecraft.server.MinecraftServer server = event.getPlayerList().getServer();
            dev.otectus.mcaquests.state.ContentOutageData.sampleNow(server);
            // And every open view built from the old catalogue is refreshed, not left to go stale until
            // it is closed: project menus, the log and tracker, and any open Quests menu (1.7.0).
            dev.otectus.mcaquests.project.ProjectMenuSessions.refreshAll(server);
            for (net.minecraft.server.level.ServerPlayer player : event.getPlayerList().getPlayers()) {
                dev.otectus.mcaquests.quest.QuestManager.syncLog(player);
                dev.otectus.mcaquests.project.ProjectManager.syncProjects(player);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        dev.otectus.mcaquests.network.QuestMenusStaleS2CPacket.INSTANCE);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TownsteadBridge.Holder.get().invalidateDataCaches();
    }
}
