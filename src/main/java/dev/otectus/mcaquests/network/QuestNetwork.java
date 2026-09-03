package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Our own payload channel (independent of MCA's "cobalt" network). Registered on the mod bus via
 * {@link RegisterPayloadHandlersEvent}. All quest packets flow through here (spec section 20).
 */
public final class QuestNetwork {

    // Bumped to 15 — the NeoForge payload rewrite, carrying the same logical data: the SimpleChannel
    // is gone and every packet is a CustomPacketPayload on a versioned PayloadRegistrar. A
    // registrar-version mismatch (or a client without the mod) cannot join, which is the same hard
    // mismatch the SimpleChannel handshake gave us.
    // (14 was GuidanceTarget carrying the target entity's bounding-box height, so the marker
    // can anchor its glyph on the body of an entity the client cannot currently see rather than at
    // the transmitted feet position.)
    // (13 was a destination for every quest, not just the marked one. QuestGuidanceS2CPacket
    // now carries a GuidanceSnapshot (one ActiveGuidance per quest, plus the index of the one the
    // marker stands on) rather than a single optional target, GuidanceTarget says whether a position
    // is somebody's last known whereabouts, and QuestLogEntry.TargetHint is gone — it was a second,
    // dimensionless way of saying the same thing and only ever named a villager.)
    // (12 was objective guidance. QuestGuidanceS2CPacket and QuestTrackC2SPacket were new,
    // CardObjective carries an objective's state as an enum rather than a single "unavailable" flag,
    // and QuestLogEntry says which quest the player is following.) (11 was v1.5.0's interface: cards
    // gained per-objective progress, reward preview stacks and a difficulty band; 10 was
    // QuestLogEntry carrying whether a quest is suspended, so the log could say a quest is waiting on
    // a mod that is no longer installed instead of showing a counter that nothing can advance; 9 was
    // per-player quest-target highlighting — HighlightTargetsS2CPacket was new and QuestLogEntry
    // gained the bound target's name and position for the HUD's direction cue.
    // (8 was the journal's View Deeds link (§29.7): JournalVillageEntry gained the village's dimension
    // and id, JournalSyncS2CPacket gained whether MCA: Reputation is canonical, and
    // OpenStandingC2SPacket was new; 7 was v1.1.0: the built-in pack moved to translation keys;
    // 6 was the abandon-from-log packet and a giver UUID on QuestLogEntry; 5 was task M5.1: the FTB
    // editor known-ids sync packet; 4 was v0.8.0: the "village needs help" situation toast packet;
    // 3 was v0.7.0: the reputation tier-up toast and journal request/sync packets; 2 was v0.4.0: the
    // community-project menu/log/contribute packets.)
    // The handshake requires matching client+server (save data is unaffected).
    private static final String PROTOCOL_VERSION = "15";

    private QuestNetwork() {
    }

    /**
     * Mod-bus listener; wired up in the {@code McaQuests} constructor. Registering late throws.
     *
     * <p>Every S2C handler is a lambda calling into {@link ClientPayloadHandlers}, never a method
     * reference: a method reference is linked when this method runs, which would resolve the
     * client-only bridge class on a dedicated server. A lambda body defers that to the first
     * invocation, which server-side never happens.
     */
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
        registrar.playToClient(QuestMenuDataS2CPacket.TYPE, QuestMenuDataS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleQuestMenuData(payload, context));
        registrar.playToClient(QuestLogSyncS2CPacket.TYPE, QuestLogSyncS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleQuestLogSync(payload, context));
        registrar.playToClient(QuestReadyToastS2CPacket.TYPE, QuestReadyToastS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleQuestReadyToast(payload, context));

        // v0.4.0 — community projects.
        registrar.playToServer(ProjectContributeC2SPacket.TYPE,
                ProjectContributeC2SPacket.STREAM_CODEC, ProjectContributeC2SPacket::handle);
        registrar.playToClient(ProjectMenuDataS2CPacket.TYPE, ProjectMenuDataS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleProjectMenuData(payload, context));
        registrar.playToClient(ProjectLogSyncS2CPacket.TYPE, ProjectLogSyncS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleProjectLogSync(payload, context));
        registrar.playToClient(ProjectPhaseToastS2CPacket.TYPE, ProjectPhaseToastS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleProjectPhaseToast(payload, context));

        // v0.7.0 — progression: tier-up toast + journal request/sync.
        registrar.playToClient(ReputationTierToastS2CPacket.TYPE, ReputationTierToastS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleReputationTierToast(payload, context));
        registrar.playToServer(RequestJournalC2SPacket.TYPE,
                RequestJournalC2SPacket.STREAM_CODEC, RequestJournalC2SPacket::handle);
        registrar.playToClient(JournalSyncS2CPacket.TYPE, JournalSyncS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleJournalSync(payload, context));

        // v0.8.0 — Living Village: situation "needs help" toast.
        registrar.playToClient(SituationToastS2CPacket.TYPE, SituationToastS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleSituationToast(payload, context));

        // Task M5.1 — FTB Quests editor known-ids sync (registered unconditionally; only the send is
        // gated on FTB Quests being loaded + syncFtbqEditorIds, see FtbqEditorIdsSync).
        registrar.playToClient(FtbqEditorIdsS2CPacket.TYPE, FtbqEditorIdsS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleFtbqEditorIds(payload, context));

        // Abandon from the quest log — no villager interaction required, so a quest whose giver is gone
        // is still droppable.
        registrar.playToServer(QuestAbandonFromLogC2SPacket.TYPE,
                QuestAbandonFromLogC2SPacket.STREAM_CODEC, QuestAbandonFromLogC2SPacket::handle);

        // §29.7 — the journal's View Deeds link into MCA: Reputation's standing screen. Registered
        // unconditionally like every packet; the handler no-ops unless Reputation is canonical.
        registrar.playToServer(OpenStandingC2SPacket.TYPE,
                OpenStandingC2SPacket.STREAM_CODEC, OpenStandingC2SPacket::handle);

        // Per-player quest-target highlighting — the glow is drawn client-side for the quest owner only,
        // so one player's markers are never visible to everyone else on the server.
        registrar.playToClient(HighlightTargetsS2CPacket.TYPE, HighlightTargetsS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleHighlightTargets(payload, context));

        // v1.5.0 — objective guidance. The marker the player follows, and which quest they follow.
        registrar.playToClient(QuestGuidanceS2CPacket.TYPE, QuestGuidanceS2CPacket.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleQuestGuidance(payload, context));
        registrar.playToServer(QuestTrackC2SPacket.TYPE,
                QuestTrackC2SPacket.STREAM_CODEC, QuestTrackC2SPacket::handle);
    }
}
