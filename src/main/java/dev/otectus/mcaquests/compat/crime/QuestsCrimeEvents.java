package dev.otectus.mcaquests.compat.crime;

import dev.otectus.mcacrime.api.event.PlayerJailedEvent;
import dev.otectus.mcacrime.api.event.PlayerReleasedFromJailEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the set of players MCA: Crime has jailed this session, so the once-a-second progress pass
 * can ask cheaply. Registered on {@code NeoForge.EVENT_BUS} <b>only</b> from
 * {@link QuestsCrimeCompat#register()}, never as an {@code @Mod.EventBusSubscriber}, which would put
 * {@code dev.otectus.mcacrime.*} on the classpath of an install that has no Crime.
 */
public final class QuestsCrimeEvents {

    private static final Set<UUID> JAILED = ConcurrentHashMap.newKeySet();

    static boolean isJailed(UUID player) {
        return JAILED.contains(player);
    }

    @SubscribeEvent
    public void onJailed(PlayerJailedEvent event) {
        if (event.getPlayer() != null) {
            JAILED.add(event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent
    public void onReleased(PlayerReleasedFromJailEvent event) {
        if (event.getPlayer() != null) {
            JAILED.remove(event.getPlayer().getUUID());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Dropped, not kept: the sentence read in QuestsCrimeCompat is the durable answer on relog.
        JAILED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        JAILED.clear();
    }
}
