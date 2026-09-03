package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.state.PendingHeartsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.UUID;

/**
 * Drains the {@link PendingHeartsData} ledger — hearts granted to a villager who was unloaded at the
 * time — as soon as both halves of the debt are present.
 *
 * <p>Two triggers, because either side can be the missing one: the villager loading
 * ({@link EntityJoinLevelEvent}) and the player logging in ({@link PlayerEvent.PlayerLoggedInEvent}). A
 * debt that cannot be paid yet is simply left in place, so nothing is ever lost and the ledger drains
 * on whichever event completes the pair.
 *
 * <p>Both handlers are cheap in the overwhelmingly common case: the entity-join path rejects
 * non-villagers with a constant-folded type check before it ever touches saved data, and the login
 * path returns immediately when the ledger is empty.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class PendingHeartsEvents {

    private PendingHeartsEvents() {
    }

    /** A villager just loaded: pay anything it is owed by players who are currently online. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        // Cheap type test first — this event fires for every entity in every loaded chunk.
        if (!McaCompat.isMcaVillager(entity)) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        try {
            PendingHeartsData ledger = PendingHeartsData.get(server);
            if (ledger.isEmpty()) {
                return;
            }
            payAll(server, ledger, entity);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("Pending-hearts drain on entity join failed; the debt is kept", t);
        }
    }

    /** A player just logged in: pay anything they owe to villagers that are already loaded. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            PendingHeartsData ledger = PendingHeartsData.get(server);
            if (ledger.isEmpty()) {
                return;
            }
            for (UUID villagerUuid : ledger.villagersOwed()) {
                Entity villager = findLoaded(server, villagerUuid);
                if (villager != null) {
                    payAll(server, ledger, villager);
                }
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("Pending-hearts drain on login failed; the debt is kept", t);
        }
    }

    /** Pays every online player's debt to this villager, leaving offline players' debts queued. */
    private static void payAll(MinecraftServer server, PendingHeartsData ledger, Entity villager) {
        UUID villagerUuid = villager.getUUID();
        Map<UUID, Integer> debts = ledger.owedTo(villagerUuid);
        for (Map.Entry<UUID, Integer> debt : debts.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(debt.getKey());
            if (player == null) {
                continue; // still owed — the login handler will catch it
            }
            McaCompat.addHearts(player, villager, debt.getValue());
            ledger.clear(villagerUuid, debt.getKey());
        }
    }

    /** The villager entity if it is loaded in any of the server's levels, else null. */
    private static Entity findLoaded(MinecraftServer server, UUID uuid) {
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }
}
