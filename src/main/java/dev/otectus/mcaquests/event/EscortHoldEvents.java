package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.escort.EscortHoldRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Pays out the releases {@code EscortHoldRegistry} queued for villagers nothing could reach.
 *
 * <p>Abandoning an escort has to un-freeze the escortee, and the escortee may be in an unloaded chunk
 * at that moment — a quest is abandonable from the log wherever the player is standing. The release is
 * queued instead, and this is where it lands: the first time that villager joins a level again, before
 * anything else can see it frozen.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class EscortHoldEvents {

    private EscortHoldEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        Optional<UUID> owner = EscortHoldRegistry.claimRelease(entity.getUUID());
        if (owner.isEmpty()) {
            return;
        }
        // The same calls the abandon path makes for a loaded escortee, so a villager released late is
        // left in the state one released on time would have been. Follow is the exception: it is a
        // state about one player, so it is only reset when that player is online to name.
        McaCompat.releaseVillagerHold(entity);
        McaCompat.stopVillagerLeading(entity);
        MinecraftServer server = entity.getServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner.get());
        if (player != null) {
            McaCompat.setQuestGiverFollow(player, entity, false);
        }
    }
}
