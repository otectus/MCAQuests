package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a new situation opened in a nearby village — show the "needs help" toast (0.8.0). */
public record SituationToastS2CPacket(Component title) implements CustomPacketPayload {

    public static final Type<SituationToastS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "situation_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SituationToastS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SituationToastS2CPacket::encode, SituationToastS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        NetComponents.write(buf, this.title);
    }

    public static SituationToastS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new SituationToastS2CPacket(NetComponents.read(buf));
    }
}
