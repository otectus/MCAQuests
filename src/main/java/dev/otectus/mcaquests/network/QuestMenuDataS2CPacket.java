package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: the data needed to render the villager's quest menu (spec section 20). Phase 0
 * carries villager identity + favor + an overall {@link QuestMenuStatus}; offer/quest details are
 * added in Phase 1.
 */
public record QuestMenuDataS2CPacket(UUID villagerUuid,
                                     Component villagerName,
                                     String professionId,
                                     int favor,
                                     QuestMenuStatus status) {

    public static void encode(QuestMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeComponent(msg.villagerName);
        buf.writeUtf(msg.professionId);
        buf.writeVarInt(msg.favor);
        buf.writeEnum(msg.status);
    }

    public static QuestMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestMenuDataS2CPacket(
                buf.readUUID(),
                buf.readComponent(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readEnum(QuestMenuStatus.class));
    }

    public static void handle(QuestMenuDataS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // QuestClientHandlers is only ever classloaded on the physical client.
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.openMenu(msg)));
        context.setPacketHandled(true);
    }
}
