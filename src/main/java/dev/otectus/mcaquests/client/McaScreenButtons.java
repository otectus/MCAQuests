package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.mixin.ScreenAccessor;
import dev.otectus.mcaquests.network.OpenQuestMenuC2SPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Appends a "Quests" button to MCA's villager menu, and re-appends it whenever MCA rebuilds the menu.
 *
 * <h2>Why this is not a mixin</h2>
 *
 * <p>This used to be two mixins into MCA — an {@code @Inject} at the tail of
 * {@code AbstractDynamicScreen#setLayout} and an {@code @Accessor} for {@code InteractScreen}'s
 * private {@code villager} field. Both named MCA classes at compile time, so both broke when MCA
 * renamed its package root, and an {@code @Accessor}'s descriptor is validated against the target
 * field's declared type — meaning the accessor <em>cannot</em> be made package-agnostic the way
 * {@link dev.otectus.mcaquests.compat.mca.McaBinding} makes everything else. Keeping them would have
 * meant one mixin class per package root plus a config plugin to select between them: four classes
 * for one button, none of it exercisable in dev, and a failed injector is fatal at screen-open.
 *
 * <p>Ordinary Forge screen events do the same job with no MCA name anywhere, which is also why
 * {@code mcaquests.mixins.json} can keep {@code "required": true} — nothing it targets can be absent.
 *
 * <h2>Why two events</h2>
 *
 * <p>{@link ScreenEvent.Init.Post} fires once, when the screen is first built. MCA rebuilds its widget
 * list every time the player moves between sub-menus, which would silently drop our button — that is
 * exactly what the old {@code setLayout} TAIL injection handled. {@link ScreenEvent.Render.Pre} is the
 * replacement: it re-places the button whenever the menu is showing MCA's top-level layout and our
 * button is not on it. In the steady state that costs one reference compare plus a short widget scan.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class McaScreenButtons {

    /**
     * MCA's interaction screen, matched by class-name suffix so it resolves under any package root.
     * The leading dot keeps it from matching some unrelated mod's {@code ...FooInteractScreen}.
     */
    private static final String INTERACT_SCREEN_SUFFIX = ".client.gui.InteractScreen";

    /** MCA's own field holding the villager. Named identically in every MCA version checked. */
    private static final String VILLAGER_FIELD = "villager";

    /** Present only on MCA's top-level ("main") layout — the only layout that gets our button. */
    private static final String MAIN_LAYOUT_MARKER = "gui.button.interact";
    private static final String TALK_BUTTON = "gui.button.talk";
    private static final String QUESTS_BUTTON = "gui.button.mcaquests.quests";

    /** Single-entry cache for "is this MCA's screen?", keyed by identity. Client render thread only. */
    private static Screen cachedScreen;
    private static boolean cachedIsMcaScreen;

    /** Reflective handle on MCA's {@code villager} field, resolved once per screen class. */
    private static Class<?> cachedFieldOwner;
    private static Field cachedVillagerField;

    /** Logged at most once, so a broken MCA layout cannot spam the client log every frame. */
    private static boolean warned;

    private McaScreenButtons() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        tryPlace(event.getScreen());
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        tryPlace(event.getScreen());
    }

    /**
     * Whether this is MCA's interaction screen — the one whose Quests button opens our menu.
     *
     * <p>Shared with {@link QuestClientHandlers#openMenu}, which has to know whether the screen it is
     * about to replace is the one that asked for the menu. Uncached, unlike {@link #tryPlace}: it is
     * called once per menu packet, not once per frame.
     */
    static boolean isMcaInteractScreen(@Nullable Screen screen) {
        return screen != null && screen.getClass().getName().endsWith(INTERACT_SCREEN_SUFFIX);
    }

    /** Places the Quests button if this is MCA's main menu and the button is not already on it. */
    private static void tryPlace(Screen screen) {
        try {
            if (screen == null || !McaQuestsConfig.CLIENT.showQuestButtonInMcaMenu.get()) {
                return;
            }
            if (screen != cachedScreen) {
                cachedScreen = screen;
                cachedIsMcaScreen = screen.getClass().getName().endsWith(INTERACT_SCREEN_SUFFIX);
            }
            if (!cachedIsMcaScreen) {
                return;
            }
            Button interact = findButton(screen, MAIN_LAYOUT_MARKER);
            if (interact == null) {
                return; // a sub-menu, not the "main" layout — nothing to attach to
            }
            if (findButton(screen, QUESTS_BUTTON) != null) {
                return; // already placed on this build of the layout
            }
            UUID villagerUuid = villagerUuidOf(screen);
            if (villagerUuid == null) {
                return;
            }
            addQuestsButton(screen, interact, villagerUuid);
        } catch (Throwable t) {
            warnOnce("Failed to add the Quests button to MCA's menu", t);
        }
    }

    /**
     * Positions the button directly below Interact, skipping any visible button slot so it never
     * overlaps Trade/Inventory/Work when those are shown. Falls back to below the bottom-most button.
     */
    private static void addQuestsButton(Screen screen, Button interact, UUID villagerUuid) {
        // Row stride is measured from the Interact/Talk gap when Talk is on a different row, so the
        // button lines up with MCA's own spacing rather than assuming a fixed height.
        Button talk = findButton(screen, TALK_BUTTON);
        int rowStride = (talk != null && talk.getY() != interact.getY())
                ? Math.abs(interact.getY() - talk.getY())
                : interact.getHeight() + 1;
        int x = interact.getX();
        int width = interact.getWidth();
        int height = interact.getHeight();
        int y = interact.getY() + rowStride;
        while (rowOccupied(screen, y, height)) {
            y += rowStride;
        }

        Button quests = Button.builder(
                        Component.translatable(QUESTS_BUTTON),
                        b -> PacketDistributor.sendToServer(new OpenQuestMenuC2SPacket(villagerUuid)))
                .bounds(x, y, width, height)
                .build();
        ScreenAccessor lists = (ScreenAccessor) screen;
        lists.mcaquests$getRenderables().add(quests);
        lists.mcaquests$getChildren().add(quests);
        lists.mcaquests$getNarratables().add(quests);
    }

    /**
     * The UUID of the villager this screen was opened for, read reflectively from MCA's private
     * {@code villager} field. {@code InteractScreen} is always constructed with an entity-backed
     * villager, so the {@link Entity} branch covers every real case; the {@code asEntity()} fallback
     * exists for MCA's player-backed {@code VillagerLike} wrappers.
     */
    @Nullable
    private static UUID villagerUuidOf(Screen screen) {
        Object villager = readVillagerField(screen);
        if (villager instanceof Entity entity) {
            return entity.getUUID();
        }
        if (villager == null) {
            return null;
        }
        try {
            Object entity = villager.getClass().getMethod("asEntity").invoke(villager);
            return entity instanceof Entity e ? e.getUUID() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Object readVillagerField(Screen screen) {
        try {
            Class<?> owner = screen.getClass();
            if (owner != cachedFieldOwner) {
                Field field = owner.getDeclaredField(VILLAGER_FIELD);
                field.setAccessible(true);
                cachedVillagerField = field;
                cachedFieldOwner = owner;
            }
            return cachedVillagerField == null ? null : cachedVillagerField.get(screen);
        } catch (Throwable t) {
            cachedFieldOwner = null;
            cachedVillagerField = null;
            warnOnce("Could not read MCA's villager field; the Quests button is unavailable", t);
            return null;
        }
    }

    @Nullable
    private static Button findButton(Screen screen, String identifierKey) {
        Component target = Component.translatable(identifierKey);
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && target.equals(button.getMessage())) {
                return button;
            }
        }
        return null;
    }

    /**
     * True if any visible widget occupies the row centred near {@code y}.
     *
     * <p>Deliberately every {@link AbstractWidget}, not only {@link Button}. Townstead adds its own
     * controls to this screen -- Pose among them -- and a mod that draws something other than a plain
     * Button would otherwise be invisible to this check and get the Quests button placed on top of it.
     * The cost of being generous is one extra instanceof per widget, once per layout rebuild.
     */
    private static boolean rowOccupied(Screen screen, int y, int height) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                    && overlapsRow(widget, y, height)) {
                return true;
            }
        }
        return false;
    }

    /** Rows overlap when their vertical spans do, rather than when their tops happen to be close. */
    private static boolean overlapsRow(AbstractWidget widget, int y, int height) {
        int top = widget.getY();
        int bottom = top + widget.getHeight();
        return top < y + height && bottom > y;
    }

    private static void warnOnce(String message, Throwable t) {
        if (!warned) {
            warned = true;
            McaQuests.LOGGER.warn("[MCA: Quests] {}", message, t);
        }
    }
}
