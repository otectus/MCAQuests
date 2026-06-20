package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.project.ProjectLogEntry;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Keybind-accessible list of the player's active MCA quests (spec section 21). Read-only. */
public class QuestLogScreen extends Screen {

    public QuestLogScreen() {
        super(Component.translatable("mcaquests.screen.log.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 36, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, getTitle(), centerX, 16, 0xFFFFFF);

        List<QuestLogEntry> entries = ClientQuestData.active();
        List<ProjectLogEntry> projects = ClientProjectData.projects();
        if (entries.isEmpty() && projects.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcaquests.status.no_active_quests"), centerX, this.height / 2, 0xA0A0A0);
        } else {
            int left = centerX - 150;
            int y = 40;
            for (QuestLogEntry entry : entries) {
                graphics.drawString(this.font, entry.title().copy()
                                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                .append(entry.giverName().copy().withStyle(ChatFormatting.GRAY)),
                        left, y, entry.ready() ? 0x5CFF5C : 0xFFE08A);
                y += 11;
                if (!entry.chainLabel().getString().isEmpty()) {
                    graphics.drawString(this.font, entry.chainLabel(), left + 2, y, 0x9A9A9A);
                    y += 10;
                }
                for (Component objective : entry.objectives()) {
                    graphics.drawString(this.font, Component.literal("  - ").append(objective), left, y, 0xBFBFBF);
                    y += 10;
                }
                if (entry.ready()) {
                    graphics.drawString(this.font,
                            Component.translatable("mcaquests.status.ready"), left + 6, y, 0x5CFF5C);
                    y += 10;
                }
                y += 6;
            }
            if (!projects.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("mcaquests.screen.log.projects"), left, y, 0x5CC8FF);
                y += 12;
                for (ProjectLogEntry project : projects) {
                    graphics.drawString(this.font, project.title().copy()
                                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                    .append(project.sponsorLabel().copy().withStyle(ChatFormatting.GRAY)),
                            left, y, 0xFFE08A);
                    y += 11;
                    graphics.drawString(this.font, Component.empty().append(project.scopeLabel())
                            .append(Component.literal("  ")).append(project.phaseLabel()), left + 2, y, 0x9A9A9A);
                    y += 10;
                    for (ProjectObjectiveLine line : project.objectives()) {
                        Component text = Component.literal("  - ").append(line.label())
                                .append(Component.literal("  "))
                                .append(Component.translatable("mcaquests.label.project.shared",
                                        line.sharedCurrent(), line.required()));
                        graphics.drawString(this.font, text, left, y, 0xBFBFBF);
                        y += 10;
                        if (line.yourContribution() > 0) {
                            graphics.drawString(this.font,
                                    Component.translatable("mcaquests.label.project.you", line.yourContribution()),
                                    left + 10, y, 0x6FA8DC);
                            y += 9;
                        }
                    }
                    y += 6;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
