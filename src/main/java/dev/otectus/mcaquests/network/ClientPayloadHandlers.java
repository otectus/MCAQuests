package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.ClientJournalData;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.client.ClientProjectData;
import dev.otectus.mcaquests.client.ClientQuestData;
import dev.otectus.mcaquests.client.QuestClientHandlers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side payload handlers, referenced <em>only</em> from the S2C registration lambdas in
 * {@link QuestNetwork}. This replaces the old per-packet
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} wrappers.
 *
 * <p>Registration uses lambdas rather than method references precisely so this class is not linked
 * on a dedicated server: nothing here is ever resolved unless a {@code playToClient} body actually
 * runs, and those bodies only run on a client. It is the one class under {@code network/} allowed to
 * name client-only types (spec §6.8).
 *
 * <p>Handlers run on the client main thread — NeoForge's payload default — which reproduces the old
 * {@code ctx.enqueueWork(...)} semantics without the wrapper.
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

    public static void handleHighlightTargets(HighlightTargetsS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.setHighlights(msg.entityIds());
    }

    public static void handleQuestGuidance(QuestGuidanceS2CPacket msg, IPayloadContext context) {
        QuestClientHandlers.setGuidance(msg.snapshot());
    }
}
