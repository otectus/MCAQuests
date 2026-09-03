package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a quest became ready to turn in — show a toast + sound (spec sections 20, 21). */
public record QuestReadyToastS2CPacket(Component title) implements CustomPacketPayload {

    public static final Type<QuestReadyToastS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_ready_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestReadyToastS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestReadyToastS2CPacket::encode, QuestReadyToastS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        NetComponents.write(buf, this.title);
    }

    public static QuestReadyToastS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestReadyToastS2CPacket(NetComponents.read(buf));
    }
}
