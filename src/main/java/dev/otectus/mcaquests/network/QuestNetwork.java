package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Our own payload channel (independent of MCA's "cobalt" network). Registered on the mod bus via
 * {@link RegisterPayloadHandlersEvent}. All quest packets flow through here (spec section 20).
 */
public final class QuestNetwork {

    // Bumped to 8 — the 1.21.1/NeoForge payload rewrite: the SimpleChannel is gone and every
    // packet is a CustomPacketPayload on a versioned PayloadRegistrar. A registrar-version mismatch (or a
    // client without the mod) cannot join — the same hard-mismatch behaviour the SimpleChannel handshake
    // gave us. (7 was v1.1.0: the whole built-in pack moved from literal text to translation keys, so quest
    // titles and dialogue travel as translatable Components; a pre-1.1.0 client has none of those keys in
    // its lang file and would render raw ids ("mcaquests.quest.farmer_wheat_request.dialogue.offer") for
    // every quest — rejecting the mismatch is far better than a UI full of translation keys. 6 was the
    // abandon-from-log packet and a giver UUID on QuestLogEntry; 5 was task M5.1: the FTB editor known-ids
    // sync packet; 4 was v0.8.0: the "village needs help" situation toast packet; 3 was v0.7.0: the
    // reputation tier-up toast and journal request/sync packets; 2 was v0.4.0: the community-project
    // menu/log/contribute packets.) Save data is unaffected by the version string.
    private static final String PROTOCOL_VERSION = "8";

    private QuestNetwork() {
    }

    /** Mod-bus listener; wired up in the {@code McaQuests} constructor. Registering late throws. */
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(McaQuests.MOD_ID).versioned(PROTOCOL_VERSION);

        registrar.playToServer(OpenQuestMenuC2SPacket.TYPE,
                OpenQuestMenuC2SPacket.STREAM_CODEC, OpenQuestMenuC2SPacket::handle);
        registrar.playToServer(QuestDecisionC2SPacket.TYPE,
                QuestDecisionC2SPacket.STREAM_CODEC, QuestDecisionC2SPacket::handle);
        registrar.playToServer(QuestTurnInC2SPacket.TYPE,
                QuestTurnInC2SPacket.STREAM_CODEC, QuestTurnInC2SPacket::handle);
        registrar.playToServer(QuestAbandonC2SPacket.TYPE,
                QuestAbandonC2SPacket.STREAM_CODEC, QuestAbandonC2SPacket::handle);
        registrar.playToClient(QuestMenuDataS2CPacket.TYPE,
                QuestMenuDataS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleQuestMenuData);
        registrar.playToClient(QuestLogSyncS2CPacket.TYPE,
                QuestLogSyncS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleQuestLogSync);
        registrar.playToClient(QuestReadyToastS2CPacket.TYPE,
                QuestReadyToastS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleQuestReadyToast);

        // v0.4.0 — community projects.
        registrar.playToServer(ProjectContributeC2SPacket.TYPE,
                ProjectContributeC2SPacket.STREAM_CODEC, ProjectContributeC2SPacket::handle);
        registrar.playToClient(ProjectMenuDataS2CPacket.TYPE,
                ProjectMenuDataS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleProjectMenuData);
        registrar.playToClient(ProjectLogSyncS2CPacket.TYPE,
                ProjectLogSyncS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleProjectLogSync);
        registrar.playToClient(ProjectPhaseToastS2CPacket.TYPE,
                ProjectPhaseToastS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleProjectPhaseToast);

        // v0.7.0 — progression: tier-up toast + journal request/sync.
        registrar.playToClient(ReputationTierToastS2CPacket.TYPE,
                ReputationTierToastS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleReputationTierToast);
        registrar.playToServer(RequestJournalC2SPacket.TYPE,
                RequestJournalC2SPacket.STREAM_CODEC, RequestJournalC2SPacket::handle);
        registrar.playToClient(JournalSyncS2CPacket.TYPE,
                JournalSyncS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleJournalSync);

        // v0.8.0 — Living Village: situation "needs help" toast.
        registrar.playToClient(SituationToastS2CPacket.TYPE,
                SituationToastS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleSituationToast);

        // Task M5.1 — FTB Quests editor known-ids sync (registered unconditionally; only the send is
        // gated on FTB Quests being loaded + syncFtbqEditorIds, see FtbqEditorIdsSync).
        registrar.playToClient(FtbqEditorIdsS2CPacket.TYPE,
                FtbqEditorIdsS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleFtbqEditorIds);

        // Abandon from the quest log — no villager interaction required, so a quest whose giver is gone
        // is still droppable.
        registrar.playToServer(QuestAbandonFromLogC2SPacket.TYPE,
                QuestAbandonFromLogC2SPacket.STREAM_CODEC, QuestAbandonFromLogC2SPacket::handle);
    }
}
