package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Placeholder quest menu shown after a villager interaction (spec section 21). Phase 0 only proves
 * the round-trip by displaying the villager's name, profession, and favor. Phase 5 makes this an
 * NPC-centred conversation screen.
 */
public class QuestMenuScreen extends Screen {

    private final QuestMenuDataS2CPacket data;

    public QuestMenuScreen(QuestMenuDataS2CPacket data) {
        super(Component.translatable("mcaquests.screen.quests.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 40, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, data.villagerName(), centerX, 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.profession", data.professionId()), centerX, 58, 0xA0A0A0);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.label.favor", data.favor()), centerX, 72, 0xA0A0A0);
        graphics.drawCenteredString(this.font,
                Component.translatable("mcaquests.status.no_quests"), centerX, 100, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
