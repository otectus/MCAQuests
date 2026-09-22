package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the quest catalogue changed under an open menu (a {@code /reload}), so a client
 * showing a villager's Quests menu asks for a fresh one (1.7.0). Carries nothing; a client with no quest
 * menu open ignores it, so nothing opens that the player had closed.
 */
public record QuestMenusStaleS2CPacket() implements CustomPacketPayload {

    public static final QuestMenusStaleS2CPacket INSTANCE = new QuestMenusStaleS2CPacket();

    public static final Type<QuestMenusStaleS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_menus_stale"));

    public static final StreamCodec<ByteBuf, QuestMenusStaleS2CPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
