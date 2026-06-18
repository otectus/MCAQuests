package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-only landing point for server packets. Referenced exclusively through
 * {@code DistExecutor} so it is never classloaded on a dedicated server.
 */
public final class QuestClientHandlers {

    private QuestClientHandlers() {
    }

    public static void openMenu(QuestMenuDataS2CPacket data) {
        Minecraft.getInstance().setScreen(new QuestMenuScreen(data));
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
}
