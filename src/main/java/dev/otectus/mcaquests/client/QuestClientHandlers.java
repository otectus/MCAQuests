package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import net.minecraft.client.Minecraft;

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
}
