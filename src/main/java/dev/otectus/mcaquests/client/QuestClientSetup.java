package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client mod-bus setup: the keybinds and the HUD tracker overlay (spec section 21).
 *
 * <p>Only the tracker toggle is bound out of the box. A mod that claims letters on a player's keyboard
 * takes them from whatever else wanted them, and the log and the journal are both reachable from the
 * villager menu and from each other's tab strip — so they are registered unbound, and a player who
 * wants a key for them says so in Controls.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QuestClientSetup {

    /** Unbound by default so it never clashes; the player assigns it in Controls. */
    public static final KeyMapping OPEN_LOG = new KeyMapping(
            "key.mcaquests.quest_log", InputConstants.UNKNOWN.getValue(), "key.categories.mcaquests");

    /** Shows/hides the quest tracker HUD; defaults to J and is rebindable in Controls. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.mcaquests.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.mcaquests");

    /**
     * Moves the marker to the next quest in the log. Unbound by default, like every other key here
     * that is not the HUD toggle: a mod has no business claiming a second letter on a player's
     * keyboard, and this is reachable from the pin in the quest log without it.
     */
    public static final KeyMapping CYCLE_TRACKED = new KeyMapping(
            "key.mcaquests.cycle_tracked", InputConstants.UNKNOWN.getValue(), "key.categories.mcaquests");

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
        event.register(CYCLE_TRACKED);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("quest_tracker", new QuestHudOverlay());
    }

    /**
     * Binds JourneyMap and Xaero's Minimap, if either is installed.
     *
     * <p>Client setup rather than common: a waypoint is a thing on one player's map, both mods are
     * client mods, and a dedicated server has no business loading either binding.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(dev.otectus.mcaquests.compat.MapWaypointCompat::init);
    }
}
