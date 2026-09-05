package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.McaQuests;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
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
}
