package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.ClientJournalData;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.client.ClientProjectData;
import dev.otectus.mcaquests.client.ClientQuestData;
import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side payload handlers, referenced <em>only</em> from the S2C registration method-refs in
 * {@link QuestNetwork}. This replaces the old per-packet
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} wrappers.
 *
 * <p>This class does load on a dedicated server — resolving the registration method references is
 * enough to bring it in. What keeps the server safe is that every mention of a client-only class
 * sits inside a {@code playToClient} handler body, and those bodies never execute server-side, so
 * the client classes they name are never resolved there (verified by the dedicated-server smoke
 * test). Handlers run on the client main thread — NeoForge's payload default — which reproduces the
 * old {@code ctx.enqueueWork(...)} semantics.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    public static void handleQuestMenuData(QuestMenuDataS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.openMenu(msg);
    }

    public static void handleQuestLogSync(QuestLogSyncS2CPacket msg, IPayloadContext context) {
        ClientQuestData.update(msg.entries());
    }

    public static void handleQuestReadyToast(QuestReadyToastS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.showReadyToast(msg.title());
    }

    public static void handleProjectMenuData(ProjectMenuDataS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.onProjectMenuData(msg.villagerUuid(), msg.cards());
    }

    public static void handleProjectLogSync(ProjectLogSyncS2CPacket msg, IPayloadContext context) {
        ClientProjectData.updateProjects(msg.entries());
    }

    public static void handleProjectPhaseToast(ProjectPhaseToastS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.showProjectToast(msg.projectTitle(), msg.phaseLabel());
    }

    public static void handleReputationTierToast(ReputationTierToastS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.showReputationTierToast(msg.tierName());
    }

    public static void handleJournalSync(JournalSyncS2CPacket msg, IPayloadContext context) {
        ClientJournalData.update(msg);
    }

    public static void handleSituationToast(SituationToastS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.showSituationToast(msg.title());
    }

    public static void handleFtbqEditorIds(FtbqEditorIdsS2CPacket msg, IPayloadContext context) {
        ClientKnownIds.update(msg);
    }
}
