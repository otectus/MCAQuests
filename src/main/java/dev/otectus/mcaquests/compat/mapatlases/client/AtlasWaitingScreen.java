package dev.otectus.mcaquests.compat.mapatlases.client;

import dev.otectus.mcaquests.client.map.MapContextScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AtlasWaitingScreen extends Screen implements MapContextScreen {
    private final Screen parent;
    private final AtlasRuntime runtime;
    AtlasWaitingScreen(Screen parent, AtlasRuntime runtime) {
        super(Component.translatable("mcaquests.atlas.reason.synchronizing")); this.parent = parent; this.runtime = runtime;
    }
    @Override public Screen mapParent() { return parent; }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 - 75, height / 2 + 12, 150, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        graphics.fill(0, 0, width, height, 0xE0101018);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 12, -1);
        super.render(graphics, x, y, partial);
    }
    @Override public void onClose() { runtime.cancel(); minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    @Override public boolean isPauseScreen() { return false; }
}
