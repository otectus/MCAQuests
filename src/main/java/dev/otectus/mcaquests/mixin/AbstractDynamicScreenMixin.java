package dev.otectus.mcaquests.mixin;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.network.OpenQuestMenuC2SPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.conczin.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Appends a "Quests" button to MCA's villager menu. MCA builds each menu in
 * {@code AbstractDynamicScreen#setLayout(key)}; we inject at TAIL for the top-level {@code "main"}
 * menu, place our button directly below the "Interact" button, and on press send our own
 * {@link OpenQuestMenuC2SPacket} (the server then opens our quest screen). Client-only; see
 * {@code mcaquests.mixins.json}.
 *
 * <p>{@code remap = false}: {@code setLayout} is MCA's own method (no vanilla obfuscation mapping),
 * so Mixin must match it by literal name. The widget is registered through {@link ScreenAccessor}
 * (vanilla {@code Screen} field accessors) so the obfuscated fields map correctly.
 */
@Mixin(net.conczin.mca.client.gui.AbstractDynamicScreen.class)
public abstract class AbstractDynamicScreenMixin {

    @Inject(method = "setLayout(Ljava/lang/String;)V", at = @At("TAIL"), require = 1, remap = false)
    private void mcaquests$addQuestButton(String key, CallbackInfo ci) {
        try {
            if (!McaQuestsConfig.CLIENT.showQuestButtonInMcaMenu.get() || !"main".equals(key)) {
                return;
            }
            Object self = this;
            if (!(self instanceof InteractScreen)) {
                return;
            }
            UUID villagerUuid = ((InteractScreenAccessor) self).getVillager().asEntity().getUUID();
            Screen screen = (Screen) self;

            int x;
            int y;
            int width;
            int height;
            Button interact = mcaquests$findButton(screen, "gui.button.interact");
            if (interact != null) {
                // Directly below Interact; skip any visible button slot to avoid overlap
                // (e.g. Trade/Inventory/Work when those are shown).
                Button talk = mcaquests$findButton(screen, "gui.button.talk");
                int rowStride = (talk != null && talk.getY() != interact.getY())
                        ? Math.abs(interact.getY() - talk.getY())
                        : interact.getHeight() + 1;
                x = interact.getX();
                width = interact.getWidth();
                height = interact.getHeight();
                y = interact.getY() + rowStride;
                while (mcaquests$rowOccupied(screen, y, height)) {
                    y += rowStride;
                }
            } else {
                // Fallback: below the bottom-most button.
                x = screen.width / 2 - 90;
                width = 80;
                height = 20;
                int bottom = Integer.MIN_VALUE;
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof Button button) {
                        int childBottom = button.getY() + button.getHeight();
                        if (childBottom > bottom) {
                            bottom = childBottom;
                            x = button.getX();
                            width = button.getWidth();
                        }
                    }
                }
                y = bottom == Integer.MIN_VALUE ? screen.height / 2 : bottom + 2;
            }

            Button quests = Button.builder(
                            Component.translatable("gui.button.mcaquests.quests"),
                            b -> PacketDistributor.sendToServer(new OpenQuestMenuC2SPacket(villagerUuid)))
                    .bounds(x, y, width, height)
                    .build();
            ScreenAccessor lists = (ScreenAccessor) self;
            lists.mcaquests$getRenderables().add(quests);
            lists.mcaquests$getChildren().add(quests);
            lists.mcaquests$getNarratables().add(quests);
        } catch (Throwable t) {
            McaQuests.LOGGER.warn("[MCA: Quests] Failed to add the Quests button to MCA's menu", t);
        }
    }

    @Nullable
    private static Button mcaquests$findButton(Screen screen, String identifierKey) {
        Component target = Component.translatable(identifierKey);
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && target.equals(button.getMessage())) {
                return button;
            }
        }
        return null;
    }

    /** True if a visible button occupies the row centred near {@code y}. */
    private static boolean mcaquests$rowOccupied(Screen screen, int y, int height) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.visible && Math.abs(button.getY() - y) < height) {
                return true;
            }
        }
        return false;
    }
}
