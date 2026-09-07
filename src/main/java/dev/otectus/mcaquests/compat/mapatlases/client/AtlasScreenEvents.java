package dev.otectus.mcaquests.compat.mapatlases.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.map.MapActionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.Set;
import java.util.stream.Collectors;

/** Registered only after the optional native client binding succeeds. */
public final class AtlasScreenEvents {
    @SubscribeEvent public void init(ScreenEvent.Init.Post event) {
        AtlasRuntime runtime=AtlasRuntime.get();
        if (runtime==null || !runtime.binding().isScreen(event.getScreen())) return;
        event.addListener(Button.builder(Component.translatable("mcaquests.atlas.destinations"), b ->
                Minecraft.getInstance().setScreen(new AtlasDestinationsScreen(event.getScreen(),Set.of())))
                .bounds(6,6,Math.min(145,event.getScreen().width-12),20).build());
    }
    @SubscribeEvent public void opening(ScreenEvent.Opening event) {
        AtlasRuntime runtime=AtlasRuntime.get();
        if (runtime!=null && runtime.rejectLateScreen(event.getNewScreen())) event.setCanceled(true);
    }
    @SubscribeEvent public void render(ScreenEvent.Render.Post event) {
        AtlasRuntime runtime=AtlasRuntime.get();
        if (runtime==null || !runtime.binding().isScreen(event.getScreen())
                || McaQuestsConfig.CLIENT.mapAtlasesLabels.get()==McaQuestsConfig.Client.AtlasLabels.NONE) return;
        AtlasQuestOverlay.hit(event.getScreen(),event.getMouseX(),event.getMouseY()).ifPresent(hit ->
                event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font,
                        AtlasQuestOverlay.tooltip(hit),event.getMouseX(),event.getMouseY()));
    }
    @SubscribeEvent public void click(ScreenEvent.MouseButtonPressed.Pre event) {
        AtlasRuntime runtime=AtlasRuntime.get();
        Screen screen=event.getScreen();
        if (runtime==null || !runtime.binding().isScreen(screen) || event.getButton()!=0
                || Screen.hasShiftDown() || Screen.hasControlDown() || Screen.hasAltDown()
                || runtime.binding().busy(screen)) return;
        if (screen.children().stream().anyMatch(c -> c instanceof AbstractWidget widget
                && widget.isMouseOver(event.getMouseX(),event.getMouseY()))) return;
        AtlasQuestOverlay.hit(screen,event.getMouseX(),event.getMouseY()).ifPresent(hit -> {
            if (hit.quests().size()==1) Minecraft.getInstance().setScreen(new MapActionScreen(screen,hit.quests().get(0).key()));
            else Minecraft.getInstance().setScreen(new AtlasDestinationsScreen(screen,hit.quests().stream().map(s -> s.key()).collect(Collectors.toSet())));
            event.setCanceled(true);
        });
    }
}
