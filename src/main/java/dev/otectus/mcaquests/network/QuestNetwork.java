package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Our own Forge {@link SimpleChannel} (independent of MCA's "cobalt" network). Registered during
 * common setup. All quest packets flow through here (spec section 20).
 */
public final class QuestNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(McaQuests.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private QuestNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, OpenQuestMenuC2SPacket.class,
                OpenQuestMenuC2SPacket::encode, OpenQuestMenuC2SPacket::decode, OpenQuestMenuC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestDecisionC2SPacket.class,
                QuestDecisionC2SPacket::encode, QuestDecisionC2SPacket::decode, QuestDecisionC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestTurnInC2SPacket.class,
                QuestTurnInC2SPacket::encode, QuestTurnInC2SPacket::decode, QuestTurnInC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestAbandonC2SPacket.class,
                QuestAbandonC2SPacket::encode, QuestAbandonC2SPacket::decode, QuestAbandonC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestMenuDataS2CPacket.class,
                QuestMenuDataS2CPacket::encode, QuestMenuDataS2CPacket::decode, QuestMenuDataS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestLogSyncS2CPacket.class,
                QuestLogSyncS2CPacket::encode, QuestLogSyncS2CPacket::decode, QuestLogSyncS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, QuestReadyToastS2CPacket.class,
                QuestReadyToastS2CPacket::encode, QuestReadyToastS2CPacket::decode, QuestReadyToastS2CPacket::handle);
    }
}
