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

    // Bumped to 2 for v0.4.0 — adds the community-project menu/log/contribute packets. The channel
    // handshake requires matching client+server (save data is unaffected).
    private static final String PROTOCOL_VERSION = "2";

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

        // v0.4.0 — community projects.
        CHANNEL.registerMessage(nextId++, ProjectContributeC2SPacket.class,
                ProjectContributeC2SPacket::encode, ProjectContributeC2SPacket::decode, ProjectContributeC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, ProjectMenuDataS2CPacket.class,
                ProjectMenuDataS2CPacket::encode, ProjectMenuDataS2CPacket::decode, ProjectMenuDataS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, ProjectLogSyncS2CPacket.class,
                ProjectLogSyncS2CPacket::encode, ProjectLogSyncS2CPacket::decode, ProjectLogSyncS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, ProjectPhaseToastS2CPacket.class,
                ProjectPhaseToastS2CPacket::encode, ProjectPhaseToastS2CPacket::decode, ProjectPhaseToastS2CPacket::handle);
    }
}
