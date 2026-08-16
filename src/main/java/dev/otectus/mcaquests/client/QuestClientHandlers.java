package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.UUID;

/**
 * Client-only landing point for server packets. Referenced exclusively from
 * {@code ClientPayloadHandlers} (the S2C payload-handler class) so it is never classloaded on a
 * dedicated server.
 */
public final class QuestClientHandlers {

    private QuestClientHandlers() {
    }

    public static void openMenu(QuestMenuDataS2CPacket data) {
        Minecraft.getInstance().setScreen(new QuestMenuScreen(data));
    }

    /** Caches the latest project menu for a villager and live-refreshes the project screen if it's open. */
    public static void onProjectMenuData(UUID villagerUuid, List<ProjectCard> cards) {
        ClientProjectData.cacheMenu(villagerUuid, cards);
        if (Minecraft.getInstance().screen instanceof ProjectMenuScreen open && open.villagerUuid().equals(villagerUuid)) {
            Minecraft.getInstance().setScreen(new ProjectMenuScreen(villagerUuid, cards));
        }
    }

    /** Opens the project screen for a villager from the cached menu (the "View Project" button). */
    public static void openProjectMenu(UUID villagerUuid) {
        List<ProjectCard> cards = ClientProjectData.menuFor(villagerUuid);
        if (!cards.isEmpty()) {
            Minecraft.getInstance().setScreen(new ProjectMenuScreen(villagerUuid, cards));
        }
    }

    public static void showReadyToast(Component questTitle) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McaQuestsConfig.CLIENT.showQuestToasts.get()) {
            minecraft.getToasts().addToast(new QuestToast(questTitle));
        }
        if (McaQuestsConfig.CLIENT.playQuestSounds.get()) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
        }
    }

    public static void showReputationTierToast(Component tierName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McaQuestsConfig.CLIENT.showQuestToasts.get()) {
            minecraft.getToasts().addToast(new ReputationTierToast(tierName));
        }
        if (McaQuestsConfig.CLIENT.playQuestSounds.get()) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
        }
    }

    public static void showProjectToast(Component projectTitle, Component phaseLabel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McaQuestsConfig.CLIENT.showQuestToasts.get()) {
            minecraft.getToasts().addToast(new ProjectToast(projectTitle, phaseLabel));
        }
        if (McaQuestsConfig.CLIENT.playQuestSounds.get()) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
        }
    }

    public static void showSituationToast(Component title) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McaQuestsConfig.CLIENT.showSituationToast.get()) {
            minecraft.getToasts().addToast(new SituationToast(title));
            if (McaQuestsConfig.CLIENT.playQuestSounds.get()) {
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
            }
        }
    }
}
