package dev.otectus.mcaquests.mixin;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.network.OpenQuestMenuC2SPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import forge.net.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Appends a "Quests" button to MCA's villager menu. MCA builds each menu in
 * {@code AbstractDynamicScreen#setLayout(key)}; we inject at TAIL for the top-level {@code "main"}
 * menu, place our button just below MCA's existing buttons, and on press send our own
 * {@link OpenQuestMenuC2SPacket} (the server then opens our quest screen). Client-only; see
 * {@code mcaquests.mixins.json}.
 *
 * <p>{@code remap = false}: {@code setLayout} is MCA's own method (no vanilla obfuscation mapping),
 * so Mixin must match it by literal name. Adding the widget goes through {@link ScreenInvoker} (a
 * mixin on the vanilla {@code Screen}) so the obfuscated {@code addRenderableWidget} maps correctly.
 */
@Mixin(forge.net.mca.client.gui.AbstractDynamicScreen.class)
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

            // Match MCA's column, and sit just below the bottom-most existing button.
            int x = screen.width / 2 - 90;
            int width = 80;
            int height = 20;
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
            int y = bottom == Integer.MIN_VALUE ? screen.height / 2 : bottom + 2;

            Button quests = Button.builder(
                            Component.translatable("gui.button.mcaquests.quests"),
                            b -> QuestNetwork.CHANNEL.sendToServer(new OpenQuestMenuC2SPacket(villagerUuid)))
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
}
