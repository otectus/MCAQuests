package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.IconButton;
import dev.otectus.mcaquests.client.gui.McaButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.network.JournalArchiveEntry;
import dev.otectus.mcaquests.network.JournalVillageEntry;
import dev.otectus.mcaquests.network.OpenStandingC2SPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.RequestJournalC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Keybind/button-accessible progression journal (spec 0.7.0): village reputations and tiers, earned
 * titles, and a completed-quest archive. Read-only; data is requested from the server on open.
 *
 * <p>With MCA: Reputation canonical, each village row carries a View Deeds link (§29.7) that asks the
 * server to open Reputation's standing screen for that village — the journal links to the same screen
 * everyone else uses rather than drawing its own version, so the two can never disagree.
 *
 * <p>This screen was the odd one out and carried a real bug. It had <b>no {@code ScrollView} and no
 * scissor at all</b>: it measured its own height <em>while drawing</em>, so scrolled content painted
 * straight over the title and around the Back button. And its View Deeds link was a hand-rolled
 * rectangle rebuilt every frame and hit-tested by hand, which meant it could be clicked and nothing
 * else — no keyboard, no focus, no narrator.
 *
 * <p>Both are fixed by laying the page out <em>before</em> drawing it. {@link #rows} is built once
 * from the cached data; the height it totals is what the view scrolls, the draw pass only walks it,
 * and the deeds links are real {@link IconButton}s placed from it.
 */
public class JournalScreen extends McaQuestsScreen {

    /** Height of a reputation bar row, including the gap under it. */
    private static final int BAR_ROW = 8;
    private static final int DEEDS_W = 14;

    /**
     * One laid-out line. Building these up front is what lets the page be measured without being
     * drawn — the thing this screen could not previously do.
     *
     * @param bar     a {@code current}/{@code max} pair to draw a progress bar under the row, or null
     * @param deeds   the village this row links to, or null
     * @param heading whether a rule is drawn under the row; stated rather than inferred from the
     *                colour, so restyling a heading cannot silently remove its rule
     */
    private record Row(Component text, int indent, int colour, int height,
                       @Nullable GuiTextures.Sprite icon, @Nullable int[] bar,
                       @Nullable JournalVillageEntry deeds, boolean heading) {

        static Row of(Component text, int indent, int colour, int height) {
            return new Row(text, indent, colour, height, null, null, null, false);
        }

        static Row icon(Component text, int indent, int colour, int height, GuiTextures.Sprite icon) {
            return new Row(text, indent, colour, height, icon, null, null, false);
        }

        static Row heading(Component text, GuiTextures.Sprite icon) {
            return new Row(text, 0, Palette.HEADING, 12, icon, null, null, true);
        }
    }

    private final List<Row> rows = new ArrayList<>();

    /** The data the current layout was built from, so {@link #tick} can notice the server's reply. */
    private List<JournalVillageEntry> renderedVillages = List.of();
    private List<Component> renderedTitles = List.of();
    private List<JournalArchiveEntry> renderedArchive = List.of();

    public JournalScreen() {
        super(Component.translatable("mcaquests.screen.journal.title"));
    }

    @Override
    protected int tabStripHeight() {
        return TAB_H;
    }

    private int wrapWidth() {
        return Math.max(1, contentWidth() - 4);
    }

    @Override
    protected void init() {
        super.init();
        // Request a fresh server-authoritative snapshot each time the journal opens.
        QuestNetwork.CHANNEL.sendToServer(new RequestJournalC2SPacket());
        buildRows();

        addBookTabs(BookTab.JOURNAL);
        addRenderableWidget(McaButton.create(Component.translatable("mcaquests.button.back"), b -> onClose())
                .bounds(centerX() - 50, footerButtonY(), 100, 20)
                .build());
    }

    @Override
    public void tick() {
        // The journal is requested on open and arrives a tick or two later; the caches swap their list
        // reference on update, so an identity check is a cheap, sufficient "the reply landed" signal.
        if (renderedVillages != ClientJournalData.villages()
                || renderedTitles != ClientJournalData.globalTitles()
                || renderedArchive != ClientJournalData.archive()) {
            rebuildWidgets();
        }
    }

    /** Lays the whole page out in content space, and places the deeds links while doing it. */
    private void buildRows() {
        rows.clear();
        renderedVillages = ClientJournalData.villages();
        renderedTitles = ClientJournalData.globalTitles();
        renderedArchive = ClientJournalData.archive();

        if (isEmpty()) {
            view.setContentHeight(0);
            return;
        }

        rows.add(Row.heading(section("mcaquests.screen.journal.villages"), GuiTextures.ICON_VILLAGE));
        if (renderedVillages.isEmpty()) {
            rows.add(Row.of(dim("mcaquests.screen.journal.no_villages"), 4, Palette.SUBTITLE, 11));
        }
        for (JournalVillageEntry village : renderedVillages) {
            Component nameLine = village.villageName().copy()
                    .append(Component.literal("  ").append(village.currentTier())
                            .withStyle(ChatFormatting.GOLD));
            rows.add(new Row(nameLine, 0, Palette.TITLE, 11, null, null,
                    ClientJournalData.reputationPresent() ? village : null, false));
            Component repLine = village.nextThreshold() >= 0
                    ? Component.translatable("mcaquests.screen.journal.rep_next",
                            village.reputation(), village.nextTier(), village.nextThreshold())
                    : Component.translatable("mcaquests.screen.journal.rep_max", village.reputation());
            // The bar is derived entirely from numbers the packet already carried; a reputation of
            // 63 toward 75 is a fact the player had to do arithmetic on before.
            int[] bar = village.nextThreshold() > 0
                    ? new int[]{village.reputation(), village.nextThreshold()}
                    : null;
            rows.add(new Row(repLine, 4, Palette.OBJECTIVE, bar != null ? 10 + BAR_ROW : 10,
                    null, bar, null, false));
            for (Component title : village.titles()) {
                rows.add(Row.icon(title, 8, Palette.PLAYER_TITLE, 10, GuiTextures.ICON_TITLE));
            }
            rows.add(Row.of(Component.empty(), 0, Palette.TEXT, 4));
        }

        rows.add(Row.of(Component.empty(), 0, Palette.TEXT, 6));
        rows.add(Row.heading(section("mcaquests.screen.journal.titles"), GuiTextures.ICON_STAR));
        if (renderedTitles.isEmpty()) {
            rows.add(Row.of(dim("mcaquests.screen.journal.no_titles"), 4, Palette.SUBTITLE, 11));
        }
        for (Component title : renderedTitles) {
            rows.add(Row.icon(title, 8, Palette.PLAYER_TITLE, 10, GuiTextures.ICON_TITLE));
        }

        rows.add(Row.of(Component.empty(), 0, Palette.TEXT, 6));
        rows.add(Row.heading(section("mcaquests.screen.journal.archive"), GuiTextures.ICON_BOOK));
        if (renderedArchive.isEmpty()) {
            rows.add(Row.of(dim("mcaquests.screen.journal.no_archive"), 4, Palette.SUBTITLE, 11));
        }
        for (JournalArchiveEntry entry : renderedArchive) {
            Component line = entry.count() > 1
                    ? entry.questTitle().copy().append(Component.literal("  x" + entry.count())
                            .withStyle(ChatFormatting.DARK_GRAY))
                    : entry.questTitle();
            rows.add(Row.icon(line, 8, Palette.OBJECTIVE, 10, GuiTextures.ICON_CHECK));
        }

        int y = 0;
        for (Row row : rows) {
            if (row.deeds() != null) {
                IconButton link = IconButton.of(contentRight() - DEEDS_W - 2, view.screenY(y),
                        GuiTextures.ICON_REPUTATION,
                        Component.translatable("mcaquests.screen.journal.view_deeds"),
                        Component.translatable("mcaquests.screen.journal.view_deeds"),
                        b -> openDeeds(row.deeds()));
                link.setWidth(DEEDS_W);
                link.setHeight(12);
                addScrolledWidget(link, y, 12);
            }
            y += row.height();
        }
        view.setContentHeight(y);
    }

    private void openDeeds(JournalVillageEntry village) {
        // The server validates the address before opening anything (§29.7); Reputation's screen
        // arrives with this journal as its parent, so Back returns here.
        QuestNetwork.CHANNEL.sendToServer(
                new OpenStandingC2SPacket(village.dimension(), village.villageId()));
    }

    private boolean isEmpty() {
        return ClientJournalData.villages().isEmpty()
                && ClientJournalData.globalTitles().isEmpty()
                && ClientJournalData.archive().isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderPanel(graphics);

        if (rows.isEmpty()) {
            renderEmptyState(graphics, Component.translatable("mcaquests.screen.journal.empty"));
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        applyScrolledVisibility();

        beginContentClip(graphics);
        int y = 0;
        for (Row row : rows) {
            renderRow(graphics, row, y);
            y += row.height();
        }
        endContentClip(graphics);
        renderScrollbar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics graphics, Row row, int contentY) {
        int screenY = view.screenY(contentY);
        int x = contentLeft() + row.indent();
        if (row.icon() != null) {
            Panel.icon(graphics, row.icon(), x - 14, screenY - 4);
        }
        if (!row.text().getString().isEmpty()) {
            CardText.draw(graphics, this.font, row.text(), x, screenY,
                    Math.max(1, wrapWidth() - row.indent() - (row.deeds() != null ? DEEDS_W + 4 : 0)),
                    row.colour());
        }
        if (row.bar() != null) {
            Panel.bar(graphics, x, screenY + 11, wrapWidth() - row.indent() - 8,
                    row.bar()[0], row.bar()[1], GuiTextures.BAR_GREEN);
        }
        if (row.heading()) {
            Panel.divider(graphics, contentLeft(), screenY + 9, contentWidth());
        }
    }

    private Component section(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.BOLD);
    }

    private Component dim(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }
}
