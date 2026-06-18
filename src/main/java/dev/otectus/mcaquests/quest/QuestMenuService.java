package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server-side entry point for opening a villager's quest menu. Both the client {@code Open} packet
 * and the Phase 0 debug interaction funnel through here, so all validation lives in one place.
 *
 * <p>This is intentionally a thin placeholder for Phase 0: it reports the villager's identity and a
 * {@link QuestMenuStatus#NO_QUESTS} status. Phase 1 replaces the body with real offer selection.
 */
public final class QuestMenuService {

    private QuestMenuService() {
    }

    /** Resolve the villager by UUID (it may have moved/unloaded), validate, then open. */
    public static void openFromPacket(ServerPlayer player, UUID villagerUuid) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity villager = level.getEntity(villagerUuid);
        if (villager == null || !McaCompat.canPlayerInteract(player, villager)) {
            return; // Later: send a friendly QuestErrorS2CPacket instead of silently ignoring.
        }
        open(player, villager);
    }

    public static void open(ServerPlayer player, Entity villager) {
        Component name = McaCompat.getVillagerDisplayName(villager);
        String professionId = McaCompat.getProfessionId(villager)
                .map(ResourceLocation::toString)
                .orElse("");
        int favor = McaCompat.getFavor(player, villager);

        QuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new QuestMenuDataS2CPacket(villager.getUUID(), name, professionId, favor, QuestMenuStatus.NO_QUESTS));
    }
}
