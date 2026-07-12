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

    // Bumped to 5 for task M5.1 — adds the FTB editor known-ids sync packet. (4 was v0.8.0: the
    // "village needs help" situation toast packet; 3 was v0.7.0: the reputation tier-up toast and
    // journal request/sync packets; 2 was v0.4.0: the community-project menu/log/contribute packets.)
    // The channel handshake requires matching client+server (save data is unaffected).
    private static final String PROTOCOL_VERSION = "5";

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

        // v0.7.0 — progression: tier-up toast + journal request/sync.
        CHANNEL.registerMessage(nextId++, ReputationTierToastS2CPacket.class,
                ReputationTierToastS2CPacket::encode, ReputationTierToastS2CPacket::decode, ReputationTierToastS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, RequestJournalC2SPacket.class,
                RequestJournalC2SPacket::encode, RequestJournalC2SPacket::decode, RequestJournalC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, JournalSyncS2CPacket.class,
                JournalSyncS2CPacket::encode, JournalSyncS2CPacket::decode, JournalSyncS2CPacket::handle);

        // v0.8.0 — Living Village: situation "needs help" toast.
        CHANNEL.registerMessage(nextId++, SituationToastS2CPacket.class,
                SituationToastS2CPacket::encode, SituationToastS2CPacket::decode, SituationToastS2CPacket::handle);

        // Task M5.1 — FTB Quests editor known-ids sync (registered unconditionally; only the send is
        // gated on FTB Quests being loaded + syncFtbqEditorIds, see FtbqEditorIdsSync).
        CHANNEL.registerMessage(nextId++, FtbqEditorIdsS2CPacket.class,
                FtbqEditorIdsS2CPacket::encode, FtbqEditorIdsS2CPacket::decode, FtbqEditorIdsS2CPacket::handle);
    }
}
