package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opens the Quest Log when the keybind is pressed (spec section 21). */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestClientInput {

    private QuestClientInput() {
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
    }
}
