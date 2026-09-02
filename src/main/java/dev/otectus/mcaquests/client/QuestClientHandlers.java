package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.UUID;

/**
 * Client-only landing point for server packets. Referenced exclusively through
 * {@code DistExecutor} so it is never classloaded on a dedicated server.
 */
public final class QuestClientHandlers {

    private QuestClientHandlers() {
    }

    /**
     * Opens the villager menu — but only over a screen it is allowed to replace.
     *
     * <p>This used to be an unconditional {@code setScreen}. The packet arrives whenever the server
     * decides to show a menu, and a player who had opened their inventory, a chest or another mod's
     * screen in the meantime was pulled straight out of it. The three cases below are the ones where
     * the menu is what the player asked for: nothing open, one of our own screens, or MCA's interact
     * screen, whose Quests button is what sent the request.
     */
    public static void openMenu(QuestMenuDataS2CPacket data) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null || screen instanceof McaQuestsScreen
                || McaScreenButtons.isMcaInteractScreen(screen)) {
            minecraft.setScreen(new QuestMenuScreen(data));
        }
    }

    /**
     * Caches the latest project menu for a villager and live-refreshes the project screen if it's open.
     *
     * <p>Refreshed <b>in place</b> rather than reopened. The server pushes the whole menu again after
     * every contribution, and replacing the screen threw away the player's scroll position each time —
     * so giving to a project several entries down bounced you back to the top of the list.
     */
    public static void onProjectMenuData(UUID villagerUuid, List<ProjectCard> cards) {
        ClientProjectData.cacheMenu(villagerUuid, cards);
        if (Minecraft.getInstance().screen instanceof ProjectMenuScreen open && open.villagerUuid().equals(villagerUuid)) {
            open.refresh(cards);
        }
    }

    /**
     * Opens the project screen for a villager from the cached menu (the "View Project" button).
     *
     * <p>The screen it opens over becomes its parent, so Back returns to the conversation the player
     * left rather than closing everything.
     */
    public static void openProjectMenu(UUID villagerUuid) {
        List<ProjectCard> cards = ClientProjectData.menuFor(villagerUuid);
        if (!cards.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ProjectMenuScreen(villagerUuid, cards, minecraft.screen));
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

    /** The full set of villagers this player's active quests want outlined; replaces whatever was set. */
    public static void setHighlights(int[] entityIds) {
        ClientHighlightData.update(entityIds);
    }

    /** Where this player's quests are sending them, or an empty snapshot to take the marker away. */
    public static void setGuidance(dev.otectus.mcaquests.quest.guidance.GuidanceSnapshot snapshot) {
        ClientGuidanceData.update(snapshot);
    }
}
