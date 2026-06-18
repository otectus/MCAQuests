package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client mod-bus setup: the Quest Log keybind and the HUD tracker overlay (spec section 21). */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QuestClientSetup {

    /** Unbound by default so it never clashes; the player assigns it in Controls. */
    public static final KeyMapping OPEN_LOG = new KeyMapping(
            "key.mcaquests.quest_log", InputConstants.UNKNOWN.getValue(), "key.categories.mcaquests");

    private QuestClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_LOG);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("quest_tracker", new QuestHudOverlay());
    }
}
