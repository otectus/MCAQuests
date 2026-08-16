package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Client mod-bus setup: the Quest Log keybind and the HUD tracker overlay (spec section 21). */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestClientSetup {

    /** Unbound by default so it never clashes; the player assigns it in Controls. */
    public static final KeyMapping OPEN_LOG = new KeyMapping(
            "key.mcaquests.quest_log", InputConstants.UNKNOWN.getValue(), "key.categories.mcaquests");

    /** Shows/hides the quest tracker HUD; defaults to J and is rebindable in Controls. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.mcaquests.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.mcaquests");

    /** Opens the progression journal (reputation/titles/archive). Unbound by default. */
    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key.mcaquests.journal", InputConstants.UNKNOWN.getValue(), "key.categories.mcaquests");

    private QuestClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_LOG);
        event.register(TOGGLE_HUD);
        event.register(OPEN_JOURNAL);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // PORT: RegisterGuiOverlaysEvent/IGuiOverlay became RegisterGuiLayersEvent/LayeredDraw.Layer.
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_tracker"),
                new QuestHudOverlay());
    }
}
