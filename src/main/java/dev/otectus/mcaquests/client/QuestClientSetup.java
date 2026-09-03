package dev.otectus.mcaquests.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.map.MapSyncDirtyFlag;
import dev.otectus.mcaquests.client.marker.MarkerSettings;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client mod-bus setup: the keybinds and the HUD tracker overlay (spec section 21).
 *
 * <p>Only the tracker toggle is bound out of the box. A mod that claims letters on a player's keyboard
 * takes them from whatever else wanted them, and the log and the journal are both reachable from the
 * villager menu and from each other's tab strip — so they are registered unbound, and a player who
 * wants a key for them says so in Controls.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_tracker"), new QuestHudOverlay());
    }

    /**
     * Binds Xaero's Minimap, if it is installed.
     *
     * <p>Client setup rather than common: a waypoint is a thing on one player's map, both supported
     * mods are client mods, and a dedicated server has no business loading either binding. JourneyMap
     * is not looked for here at all — it discovers its own plugin and registers itself.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(dev.otectus.mcaquests.client.map.MapWaypointCompat::init);
    }

    /**
     * Throws away the client's cached view of its own config whenever that config is (re)loaded.
     *
     * <p>The marker reads eight keys and would otherwise read them every frame; caching them means
     * something has to say when the cache is wrong, and this is the only event that knows. The map
     * layer is told the same way, so turning a waypoint toggle off clears its waypoints on the next
     * tick instead of at the next quest update.
     *
     * <p>Config events can fire off the client thread, so the body touches nothing but an atomic —
     * no {@code Minecraft}, no render state, no collections.
     */
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        invalidateClientCaches(event);
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        invalidateClientCaches(event);
    }

    private static void invalidateClientCaches(ModConfigEvent event) {
        if (event.getConfig().getSpec() == McaQuestsConfig.CLIENT_SPEC) {
            MarkerSettings.invalidate();
            MapSyncDirtyFlag.set();
        }
    }
}
