package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.network.JournalArchiveEntry;
import dev.otectus.mcaquests.network.JournalVillageEntry;
import dev.otectus.mcaquests.network.OpenStandingC2SPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.RequestJournalC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keybind/button-accessible progression journal (spec 0.7.0): village reputations and tiers, earned
 * titles, and a completed-quest archive. Read-only; data is requested from the server on open.
 *
 * <p>With MCA: Reputation canonical, each village row carries a View Deeds link (§29.7) that asks the
 * server to open Reputation's standing screen for that village — the journal links to the same screen
 * everyone else uses rather than drawing its own version, so the two can never disagree.
 */
public class JournalScreen extends Screen {

    /** A clickable View Deeds region, rebuilt every frame because the list scrolls. */
    private record DeedsLink(int x0, int y0, int x1, int y1, JournalVillageEntry entry) {
        boolean contains(double x, double y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    private int scroll;
    private int contentHeight;
    private final List<DeedsLink> deedsLinks = new ArrayList<>();

    public JournalScreen() {
        super(Component.translatable("mcaquests.screen.journal.title"));
    }

    @Override
    protected void init() {
        // Request a fresh server-authoritative snapshot each time the journal opens.
        QuestNetwork.CHANNEL.sendToServer(new RequestJournalC2SPacket());
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX - 50, this.height - 36, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, getTitle(), centerX, 14, 0xFFFFFF);

        List<JournalVillageEntry> villages = ClientJournalData.villages();
        List<Component> globalTitles = ClientJournalData.globalTitles();
        List<JournalArchiveEntry> archive = ClientJournalData.archive();

        int left = centerX - 150;
        int y = 36 - scroll;
        int top = y;

        if (villages.isEmpty() && globalTitles.isEmpty() && archive.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("mcaquests.screen.journal.empty"), centerX, this.height / 2, 0xA0A0A0);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(this.font, section("mcaquests.screen.journal.villages"), left, y, 0x5CC8FF);
        y += 12;
        if (villages.isEmpty()) {
            graphics.drawString(this.font, dim("mcaquests.screen.journal.no_villages"), left + 4, y, 0x9A9A9A);
            y += 11;
        }
        deedsLinks.clear();
        for (JournalVillageEntry v : villages) {
            Component nameLine = v.villageName().copy()
                    .append(Component.literal("  ").append(v.currentTier()).withStyle(ChatFormatting.GOLD));
            graphics.drawString(this.font, nameLine, left, y, 0xFFE08A);
            if (ClientJournalData.reputationPresent()) {
                Component link = Component.translatable("mcaquests.screen.journal.view_deeds")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
                int linkX = left + this.font.width(nameLine) + 8;
                graphics.drawString(this.font, link, linkX, y, 0x5CC8FF);
                deedsLinks.add(new DeedsLink(linkX, y, linkX + this.font.width(link), y + 9, v));
            }
            y += 11;
            Component repLine = v.nextThreshold() >= 0
                    ? Component.translatable("mcaquests.screen.journal.rep_next",
                            v.reputation(), v.nextTier(), v.nextThreshold())
                    : Component.translatable("mcaquests.screen.journal.rep_max", v.reputation());
            graphics.drawString(this.font, repLine, left + 4, y, 0xBFBFBF);
            y += 10;
            for (Component title : v.titles()) {
                graphics.drawString(this.font, Component.literal("  + ").append(title), left + 4, y, 0xC8A2FF);
                y += 9;
            }
            y += 4;
        }

        y += 6;
        graphics.drawString(this.font, section("mcaquests.screen.journal.titles"), left, y, 0x5CC8FF);
        y += 12;
        if (globalTitles.isEmpty()) {
            graphics.drawString(this.font, dim("mcaquests.screen.journal.no_titles"), left + 4, y, 0x9A9A9A);
            y += 11;
        }
        for (Component title : globalTitles) {
            graphics.drawString(this.font, Component.literal("  + ").append(title), left + 4, y, 0xC8A2FF);
            y += 10;
        }

        y += 6;
        graphics.drawString(this.font, section("mcaquests.screen.journal.archive"), left, y, 0x5CC8FF);
        y += 12;
        if (archive.isEmpty()) {
            graphics.drawString(this.font, dim("mcaquests.screen.journal.no_archive"), left + 4, y, 0x9A9A9A);
            y += 11;
        }
        for (JournalArchiveEntry entry : archive) {
            Component line = Component.literal("  - ").append(entry.questTitle());
            if (entry.count() > 1) {
                line = line.copy().append(Component.literal("  x" + entry.count()).withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.drawString(this.font, line, left + 4, y, 0xBFBFBF);
            y += 10;
        }

        contentHeight = y - top;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int viewport = this.height - 80;
        int max = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(max, scroll - (int) (delta * 12)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (DeedsLink link : deedsLinks) {
                if (link.contains(mouseX, mouseY)) {
                    // The server validates the address before opening anything (§29.7); Reputation's
                    // screen arrives with this journal as its parent, so Back returns here.
                    QuestNetwork.CHANNEL.sendToServer(new OpenStandingC2SPacket(
                            link.entry().dimension(), link.entry().villageId()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component section(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.BOLD);
    }

    private Component dim(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
