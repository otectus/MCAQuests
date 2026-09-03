package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.network.HighlightTargetsS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which entity ids each player currently has highlighted and pushes the set to their client only
 * when it changes.
 *
 * <p>The highlight is recomputed roughly once a second by the active-quest poll, but it almost never
 * differs between polls — a villager stays the same villager. Diffing here keeps that from becoming a
 * packet per player per second for the whole session.
 *
 * <p>State is per-player and purely presentational: it is deliberately <em>not</em> persisted, so a
 * reconnect simply recomputes and resends on the next poll.
 */
public final class HighlightService {

    private static final int[] NONE = new int[0];

    /** Last set sent to each player, so an unchanged set costs nothing. Cleared on logout. */
    private static final Map<UUID, int[]> LAST_SENT = new ConcurrentHashMap<>();

    private HighlightService() {
    }

    /**
     * Sends {@code entityIds} to {@code player} if it differs from what they were last sent.
     * {@code entityIds} is sorted in place so that the same set of villagers compares equal regardless of
     * the order the objectives happened to be walked in.
     */
    public static void send(ServerPlayer player, int[] entityIds) {
        Arrays.sort(entityIds);
        int[] previous = LAST_SENT.get(player.getUUID());
        if (Arrays.equals(previous, entityIds)) {
            return;
        }
        LAST_SENT.put(player.getUUID(), entityIds);
        PacketDistributor.sendToPlayer(player,
                new HighlightTargetsS2CPacket(entityIds));
    }

    /** Clears the tracked set so the next poll resends — used on logout and when highlighting is off. */
    public static void forget(UUID playerId) {
        LAST_SENT.remove(playerId);
    }

    /**
     * Clears any highlight the player currently has. Sent unconditionally through {@link #send} so a
     * player who turns the feature off (or logs into a world with no active quests) does not keep a
     * villager outlined from their last session.
     */
    public static void clear(ServerPlayer player) {
        send(player, NONE);
    }
}
