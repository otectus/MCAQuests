package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.client.gui.IconButton;
import dev.otectus.mcaquests.client.gui.Palette;
import dev.otectus.mcaquests.client.gui.Panel;
import dev.otectus.mcaquests.client.gui.Scrollbar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The window every MCA: Quests screen is drawn inside.
 *
 * <p>Before this class each screen derived its own geometry from {@code this.width} and
 * {@code this.height} every frame — {@code centerX - 150} here, {@code this.height - 36} there — and
 * drew text rows straight onto vanilla's dimmed world with no frame at all. Roughly forty such call
 * sites existed across four screens, each free to disagree with the others, and three of them
 * carried a byte-identical private copy of the scrollbar.
 *
 * <p>The panel is <b>adaptive rather than fixed</b>. Every sprite it uses is nine-sliced, so instead
 * of one hard-coded 176×166 window the frame is sized to the screen and clamped into a range that
 * stays legible from a 320×240-equivalent display at GUI scale 4 up to a maximised 4K one. That is
 * the acceptance bar the Townstead compatibility spec sets, and a fixed sheet could not have met it.
 *
 * <p>Subclasses lay their content out in {@code ScrollView}'s content space (starting at 0), measure
 * its height up front so buttons can be placed under it, and draw between {@link #beginContentClip}
 * and {@link #endContentClip}. Interactive widgets inside that space are <em>hidden</em> rather than
 * clipped when they scroll out of view: {@code super.render()} draws widgets outside our scissor and
 * so cannot clip them, and {@code AbstractWidget.clicked} tests {@code visible}, so hiding doubles as
 * blocking the click.
 */
abstract class McaQuestsScreen extends Screen {

    /** Thickness of the window frame; content starts inside it. Matches the sprite's 8px slice. */
    protected static final int FRAME = PanelGeometry.FRAME;
    /** The band the title and any tabs sit on. */
    protected static final int HEADER_H = PanelGeometry.HEADER_H;
    /** The band the footer buttons sit on. A 20px button with 3px above and below. */
    protected static final int FOOTER_H = PanelGeometry.FOOTER_H;
    /** Breathing room between the well's edge and the content inside it. */
    protected static final int PAD = PanelGeometry.PAD;
    /** Height of a tab above the window frame, for screens that have tabs. */
    protected static final int TAB_H = 22;

    /** Content pixels one arrow-key press scrolls; the same step the mouse wheel takes. */
    private static final int LINE_SCROLL = 12;

    protected final ScrollView view = new ScrollView();
    protected final Scrollbar scrollbar = new Scrollbar();

    /**
     * The widgets that live inside the scrolled content, each with the content-space y and height it
     * was laid out at.
     *
     * <p>All four screens kept their own private list of exactly this shape and their own copy of the
     * reposition-and-hide loop. Hoisting it is what lets the keyboard work here rather than four
     * times: {@link #keyPressed} has to be able to unhide a widget below the fold before Tab can
     * reach it, and put it back afterwards.
     */
    protected final List<ScrolledWidget> scrolledWidgets = new ArrayList<>();

    /** A widget in the scrolled content, remembered with its content-space y so scrolling can move it. */
    protected record ScrolledWidget(AbstractWidget widget, int contentY, int height) {
    }

    /** Rebuilt on every {@code init}, so a resize recomputes rather than patches. */
    private PanelGeometry geometry = new PanelGeometry(0, 0, 0, 0);

    protected McaQuestsScreen(Component title) {
        super(title);
    }

    /**
     * Pixels reserved above the window for a tab strip. Zero unless a subclass draws tabs, so a modal
     * screen does not pay for a strip it never shows.
     */
    protected int tabStripHeight() {
        return 0;
    }

    @Override
    protected void init() {
        geometry = new PanelGeometry(this.width, this.height, tabStripHeight(), extraHeaderHeight());
        view.setViewport(contentTop(), contentBottom());
        scrolledWidgets.clear();
    }

    /**
     * Adds a widget that scrolls with the content. Positioning and hiding are then
     * {@link #applyScrolledVisibility}'s job, so no screen has to remember to do both.
     */
    protected void addScrolledWidget(AbstractWidget widget, int contentY, int height) {
        addRenderableWidget(widget);
        scrolledWidgets.add(new ScrolledWidget(widget, contentY, height));
    }

    /**
     * Moves every scrolled widget to where the current scroll puts it, and hides the ones that are
     * not wholly inside the well.
     *
     * <p>Hidden rather than clipped: {@code super.render()} draws widgets outside our scissor and so
     * cannot clip them, and {@code AbstractWidget.clicked} tests {@code visible}, so hiding doubles
     * as blocking the click. Call it from {@code render} before {@code super.render}.
     */
    protected void applyScrolledVisibility() {
        for (ScrolledWidget scrolled : scrolledWidgets) {
            scrolled.widget().setY(view.screenY(scrolled.contentY()));
            scrolled.widget().visible = view.isFullyVisible(scrolled.contentY(), scrolled.height());
        }
    }

    // --- geometry ---------------------------------------------------------------------------
    // All of it derived by PanelGeometry, which is pure and unit-tested; these are pass-throughs so
    // the screens read the same as they did before the extraction.

    /**
     * Fixed rows between the title band and the well, for a screen that carries its own header — the
     * offer menu's villager portrait, name, profession and hearts. Zero elsewhere, so no screen pays
     * for a band it does not draw.
     */
    protected int extraHeaderHeight() {
        return 0;
    }

    protected int panelWidth() {
        return geometry.panelWidth();
    }

    protected int panelHeight() {
        return geometry.panelHeight();
    }

    protected int leftPos() {
        return geometry.left();
    }

    protected int topPos() {
        return geometry.top();
    }

    protected int centerX() {
        return geometry.centerX();
    }

    /** Left edge of the inset well. */
    protected int wellLeft() {
        return geometry.wellLeft();
    }

    /** Right edge of the inset well, exclusive. */
    protected int wellRight() {
        return geometry.wellRight();
    }

    protected int wellTop() {
        return geometry.wellTop();
    }

    protected int wellBottom() {
        return geometry.wellBottom();
    }

    /** Where the scrollbar track sits. Always reserved, so the layout does not shift as content grows. */
    protected int scrollbarX() {
        return geometry.scrollbarX();
    }

    protected int contentLeft() {
        return geometry.contentLeft();
    }

    /** Right edge of the text column, clear of the scrollbar gutter. */
    protected int contentRight() {
        return geometry.contentRight();
    }

    protected int contentWidth() {
        return geometry.contentWidth();
    }

    protected int contentTop() {
        return geometry.contentTop();
    }

    protected int contentBottom() {
        return geometry.contentBottom();
    }

    /** Bottom of the title band — where a screen's own header rows begin. */
    protected int headerBandBottom() {
        return geometry.headerBandBottom();
    }

    /** Y of the footer button row, so subclasses do not each re-derive it. */
    protected int footerButtonY() {
        return geometry.footerButtonY();
    }

    // --- chrome -----------------------------------------------------------------------------

    /** Vanilla's dim, then the window, its header and footer bands, and the inset well. */
    protected void renderPanel(GuiGraphics graphics) {
        Panel.window(graphics, leftPos(), topPos(), panelWidth(), panelHeight());
        Panel.header(graphics, wellLeft(), topPos() + FRAME, wellRight() - wellLeft(), HEADER_H);
        Panel.footer(graphics, wellLeft(), topPos() + panelHeight() - FRAME - FOOTER_H,
                wellRight() - wellLeft(), FOOTER_H);
        Panel.well(graphics, wellLeft(), wellTop(), wellRight() - wellLeft(),
                wellBottom() - wellTop());
        centered(graphics, this.title, centerX(), topPos() + FRAME + (HEADER_H - 8) / 2, Palette.TEXT);
    }

    /** Clips drawing to the well. Always pair with {@link #endContentClip}. */
    protected void beginContentClip(GuiGraphics graphics) {
        graphics.enableScissor(wellLeft() + 1, wellTop() + 1, wellRight() - 1, wellBottom() - 1);
    }

    protected void endContentClip(GuiGraphics graphics) {
        graphics.disableScissor();
    }

    protected Scrollbar.Geometry scrollGeometry() {
        return new Scrollbar.Geometry(scrollbarX(), view.top(), view.bottom(),
                view.thumbTop(), view.thumbHeight(), view.maxScroll());
    }

    protected void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        scrollbar.render(graphics, scrollGeometry(), mouseX, mouseY);
    }

    /**
     * Centres an empty-state line in the well. Every screen had its own copy of this at
     * {@code this.height / 2}, which put it below the panel once there was a panel to be inside.
     */
    protected void renderEmptyState(GuiGraphics graphics, Component message) {
        centered(graphics, message, centerX(), (wellTop() + wellBottom()) / 2 - 4, Palette.EMPTY);
    }

    /**
     * Draws {@code text} centred on {@code centreX} <em>without</em> a shadow.
     *
     * <p>Vanilla's {@code GuiGraphics.drawCenteredString} has no shadowless overload — it always
     * passes {@code true}. On the dark panel these screens used to have, that was right; on
     * vanilla's container grey a shadow is a dark offset copy of the glyph and reads as a smear.
     * So the centring is done here and the draw goes through the overload that can say no.
     */
    protected void centered(GuiGraphics graphics, Component text, int centreX, int y, int colour) {
        graphics.drawString(this.font, text, centreX - this.font.width(text) / 2, y, colour, false);
    }

    // --- tabs -------------------------------------------------------------------------------

    /**
     * The two pages of the player's own book.
     *
     * <p>They were two separate screens reached by a footer button, so getting from the journal back
     * to the log meant Back, then Journal again. The villager offer menu and the project menu are
     * deliberately <em>not</em> tabs here: both are about one villager standing in front of you, and
     * neither is somewhere you leaf to.
     */
    protected enum BookTab {
        LOG("mcaquests.screen.log.title", GuiTextures.ICON_QUEST),
        JOURNAL("mcaquests.screen.journal.title", GuiTextures.ICON_BOOK);

        private final String titleKey;
        private final GuiTextures.Sprite icon;

        BookTab(String titleKey, GuiTextures.Sprite icon) {
            this.titleKey = titleKey;
            this.icon = icon;
        }

        Component title() {
            return Component.translatable(titleKey);
        }
    }

    /** Width of one tab. Wide enough for a 16px glyph with a pixel of air either side. */
    private static final int TAB_W = 26;

    /**
     * How far the strip starts in from the inside of the window frame. Flush against it, the first
     * tab's own bevel merged with the frame's corner bevel and read as a tab clipped by the panel.
     */
    private static final int TAB_INSET = 4;

    /**
     * Adds the tab strip above the window. The current tab is drawn selected and does nothing when
     * pressed, the way vanilla's creative tabs behave.
     */
    protected void addBookTabs(BookTab active) {
        int x = leftPos() + FRAME + TAB_INSET;
        for (BookTab tab : BookTab.values()) {
            boolean selected = tab == active;
            // The tab is drawn taller than the strip it reserves so its bottom edge tucks under the
            // window frame, which is what makes the selected one read as continuous with the panel.
            IconButton button = new IconButton(x, topPos() - TAB_H, TAB_W, TAB_H + FRAME,
                    tab.title(), tab.icon,
                    selected ? IconButton.Look.TAB_SELECTED : IconButton.Look.TAB_UNSELECTED,
                    b -> {
                        if (!selected) {
                            openTab(tab);
                        }
                    });
            button.setTooltip(Tooltip.create(tab.title()));
            addRenderableWidget(button);
            x += TAB_W + 2;
        }
    }

    private void openTab(BookTab tab) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(switch (tab) {
            case LOG -> new QuestLogScreen();
            case JOURNAL -> new JournalScreen();
        });
    }

    // --- tooltips ---------------------------------------------------------------------------
    // The mod had none of these. Vanilla positions and draws whatever is set here at the end of
    // Screen.renderWithTooltip, so nothing needs to be queued or drawn by hand.

    /** A one-component tooltip at the cursor. */
    protected void tooltip(Component line) {
        setTooltipForNextRenderPass(Tooltip.splitTooltip(this.minecraft, line));
    }

    /** A multi-line tooltip at the cursor, each line wrapped the way vanilla wraps its own. */
    protected void tooltip(List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> split = new ArrayList<>();
        for (Component line : lines) {
            split.addAll(this.font.split(line, 170));
        }
        setTooltipForNextRenderPass(split);
    }

    /** The item's own tooltip, exactly as the inventory would show it. */
    protected void itemTooltip(ItemStack stack) {
        if (this.minecraft == null || stack.isEmpty()) {
            return;
        }
        tooltip(getTooltipFromItem(this.minecraft, stack));
    }

    /** Whether the cursor is inside a rectangle. */
    protected static boolean inRect(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * As {@link #inRect}, but also inside the well.
     *
     * <p>Scrolled content is clipped to the well when it is drawn, so a row that has scrolled halfway
     * out is only half visible — without this second test it would still answer hover over the part
     * that is no longer on screen.
     */
    protected boolean hoveringInWell(int x, int y, int width, int height, int mouseX, int mouseY) {
        return inRect(x, y, width, height, mouseX, mouseY)
                && mouseY >= wellTop() && mouseY < wellBottom();
    }

    // --- input ------------------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 1.21 splits the wheel into two axes; only the vertical one ever scrolled these screens.
        view.scrollBy(-(int) (scrollY * 12));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbar.mouseClicked(mouseX, mouseY, scrollGeometry())) {
            applyScrollDrag(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollbar.isDragging()) {
            applyScrollDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollbar.isDragging()) {
            scrollbar.release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * The keyboard's half of scrolling, which the screens did not have at all: arrows, PageUp/PageDown
     * and Home/End move the view, and Tab reaches the widgets below the fold.
     *
     * <p>Tab needs the two extra steps because a scrolled-away widget is {@code visible = false}, and
     * vanilla's focus search skips those — so every screen's last Abandon button was unreachable
     * without a mouse. The widgets are shown, vanilla is asked to move focus as it normally would, the
     * view is scrolled to wherever focus landed, and the visibility rule is applied again.
     *
     * <p>Anything not handled here goes to {@code super}, so Escape still closes the screen and a
     * focused widget still gets its own keys.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> view.scrollBy(-LINE_SCROLL);
            case GLFW.GLFW_KEY_DOWN -> view.scrollBy(LINE_SCROLL);
            case GLFW.GLFW_KEY_PAGE_UP -> view.scrollBy(-view.viewportHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> view.scrollBy(view.viewportHeight());
            case GLFW.GLFW_KEY_HOME -> view.scrollBy(-view.maxScroll());
            case GLFW.GLFW_KEY_END -> view.scrollBy(view.maxScroll());
            case GLFW.GLFW_KEY_TAB -> {
                return tabThroughScrolledWidgets(keyCode, scanCode, modifiers);
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    private boolean tabThroughScrolledWidgets(int keyCode, int scanCode, int modifiers) {
        for (ScrolledWidget scrolled : scrolledWidgets) {
            scrolled.widget().visible = true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        scrollFocusIntoView();
        applyScrolledVisibility();
        return handled;
    }

    /** Brings whatever now has focus into the well, when it is one of the scrolled widgets. */
    private void scrollFocusIntoView() {
        GuiEventListener focused = getFocused();
        for (ScrolledWidget scrolled : scrolledWidgets) {
            if (scrolled.widget() == focused) {
                view.scrollIntoView(scrolled.contentY(), scrolled.height());
                return;
            }
        }
    }

    private void applyScrollDrag(double mouseY) {
        OptionalInt target = scrollbar.scrollFor(mouseY, scrollGeometry());
        if (target.isPresent()) {
            view.scrollBy(target.getAsInt() - view.scroll());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Sprite for a difficulty band, or {@code null} when the quest declares none. */
    protected static GuiTextures.Sprite difficultyPips(String difficulty) {
        if (difficulty == null) {
            return null;
        }
        return switch (difficulty) {
            case "easy" -> GuiTextures.ICON_PIP_EASY;
            case "medium" -> GuiTextures.ICON_PIP_MEDIUM;
            case "hard" -> GuiTextures.ICON_PIP_HARD;
            default -> null;
        };
    }
}
