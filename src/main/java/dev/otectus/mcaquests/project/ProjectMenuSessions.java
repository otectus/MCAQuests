package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which villager's project menu each player last received, so progress made elsewhere reaches a menu
 * that is already open (1.7.0).
 *
 * <p>Before this a project screen only changed when its own player contributed. Walls laid by a friend,
 * a building registered, a phase advanced by the periodic sweep or an operator repair all left an open
 * menu showing the old numbers until it was closed and reopened. The client caches a pushed menu and
 * redraws an open screen in place, so resending is harmless to a player who has since closed it.
 *
 * <p>Refreshes are coalesced: a burst of block placements asks once, and the next flush — at most every
 * {@link #FLUSH_INTERVAL_TICKS} — sends one menu per remembered player.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ProjectMenuSessions {

    static final int FLUSH_INTERVAL_TICKS = 10;
    /** A menu older than this is assumed closed and is not refreshed. */
    static final long SESSION_TICKS = 20L * 60L * 5L;

    private record Session(UUID villager, long openedAt) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static volatile boolean refreshRequested;

    private ProjectMenuSessions() {
    }

    /** Remembers that {@code player} was just sent {@code villager}'s project menu. */
    static void opened(ServerPlayer player, UUID villager) {
        SESSIONS.put(player.getUUID(), new Session(villager, player.level().getGameTime()));
    }

    /** Asks for every remembered menu to be resent on the next flush. */
    public static void refreshAll(MinecraftServer server) {
        refreshRequested = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !refreshRequested || event.getServer() == null
                || event.getServer().getTickCount() % FLUSH_INTERVAL_TICKS != 0) {
            return;
        }
        refreshRequested = false;
        flush(event.getServer());
    }

    private static void flush(MinecraftServer server) {
        for (Map.Entry<UUID, Session> entry : Map.copyOf(SESSIONS).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || level.getGameTime() - entry.getValue().openedAt() > SESSION_TICKS) {
                SESSIONS.remove(entry.getKey());
                continue;
            }
            Entity villager = level.getEntity(entry.getValue().villager());
            if (villager == null || !McaCompat.canPlayerInteract(player, villager)) {
                continue;
            }
            ProjectManager.sendProjectMenu(player, villager);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    static void clearSessionState() {
        SESSIONS.clear();
        refreshRequested = false;
    }
}
