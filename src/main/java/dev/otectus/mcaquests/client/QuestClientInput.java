package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens the Quest Log when the keybind is pressed (spec section 21). */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestClientInput {

    private QuestClientInput() {
    }

    /** Drops the quest-target outlines so none can survive into the next world we join. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHighlightData.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (QuestClientSetup.OPEN_LOG.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new QuestLogScreen());
            }
        }
        while (QuestClientSetup.TOGGLE_HUD.consumeClick()) {
            ClientQuestData.toggleHud();
        }
        while (QuestClientSetup.OPEN_JOURNAL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JournalScreen());
            }
        }
    }
}
