package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the player reached a new reputation tier with a village — show a toast (spec 0.7.0). */
public record ReputationTierToastS2CPacket(Component tierName) implements CustomPacketPayload {

    public static final Type<ReputationTierToastS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "reputation_tier_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReputationTierToastS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ReputationTierToastS2CPacket::encode, ReputationTierToastS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        NetComponents.write(buf, this.tierName);
    }

    public static ReputationTierToastS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReputationTierToastS2CPacket(NetComponents.read(buf));
    }
}
