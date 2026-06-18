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
        CHANNEL.registerMessage(nextId++, QuestMenuDataS2CPacket.class,
                QuestMenuDataS2CPacket::encode, QuestMenuDataS2CPacket::decode, QuestMenuDataS2CPacket::handle);
    }
}
