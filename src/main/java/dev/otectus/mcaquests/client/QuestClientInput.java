package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opens the Quest Log when the keybind is pressed (spec section 21). */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestClientInput {

    private QuestClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // PORT: Post replaces the old TickEvent.ClientTickEvent + phase == END guard.
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
