package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.escort.EscortHoldRegistry;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Pays out the releases {@code EscortHoldRegistry} queued for villagers nothing could reach, and
 * frees holds nobody owns any more.
 *
 * <p>Abandoning an escort has to un-freeze the escortee, and the escortee may be in an unloaded chunk
 * at that moment — a quest is abandonable from the log wherever the player is standing. The release is
 * queued instead, and this is where it lands: the first time that villager joins a level again, before
 * anything else can see it frozen.
 *
 * <p>Since 1.7.0 a joining villager that carries a hold's marker is also checked against its lease: no
 * lease at all, or an owner who is online and no longer has an active quest bound to this villager,
 * means the hold outlived its escort, and it is released. An owner who is offline keeps the hold until
 * they return, because their quest may still want it.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class EscortHoldEvents {

    private EscortHoldEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        EscortHoldRegistry.attach(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        MinecraftServer server = entity.getServer();
        // Leases first: a villager in the spawn chunks can join before ServerStartedEvent (1.7.0).
        EscortHoldRegistry.attach(server);
        Optional<UUID> owner = EscortHoldRegistry.claimRelease(entity.getUUID());
        if (owner.isEmpty() && !orphaned(entity, server)) {
            return;
        }
        UUID ownerId = owner.orElseGet(() -> EscortHoldRegistry.leaseOf(entity.getUUID())
                .map(EscortHoldRegistry.Lease::owner).orElse(EscortHoldRegistry.NO_OWNER));
        // The same calls the abandon path makes for a loaded escortee, so a villager released late is
        // left in the state one released on time would have been. Follow is the exception: it is a
        // state about one player, so it is only reset when that player is online to name.
        McaCompat.releaseVillagerHold(entity);
        McaCompat.stopVillagerLeading(entity);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (player != null) {
            McaCompat.setQuestGiverFollow(player, entity, false);
        }
    }

    /** A marked villager whose hold no escort still wants. */
    private static boolean orphaned(Entity entity, MinecraftServer server) {
        if (!(entity instanceof Mob mob) || !EscortHoldRegistry.isMarkedHeld(mob)) {
            return false;
        }
        Optional<EscortHoldRegistry.Lease> lease = EscortHoldRegistry.leaseOf(entity.getUUID());
        if (lease.isEmpty()) {
            return true;
        }
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(lease.get().owner());
        if (owner == null) {
            return false;
        }
        return QuestCapabilities.get(owner)
                .map(data -> data.active().stream().noneMatch(active -> boundTo(active, entity.getUUID())))
                .orElse(false);
    }

    /** Whether this quest names the villager as its giver or as any objective's bound target. */
    static boolean boundTo(ActiveQuest active, UUID villager) {
        if (villager.equals(active.villagerUuid())) {
            return true;
        }
        return active.allProgress().stream().anyMatch(progress -> villager.equals(progress.targetUuid()));
    }
}
