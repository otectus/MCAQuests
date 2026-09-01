package dev.otectus.mcaquests.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Wrapped, hanging-indented text for the quest, log and project cards.
 *
 * <p>All three screens wrapped their <em>dialogue</em> and drew their <b>objective and reward lines
 * unwrapped</b>, straight into a scissor rectangle exactly one card wide. Anything longer than the card
 * was therefore cut off mid-word, with no ellipsis and no way for the player to see the rest — the last
 * objective of a quest could simply be unreadable. It was easy to miss because bundled objectives were
 * short, right up until one was not.
 *
 * <p>Wrapping alone is not enough: the two screens with scrolling cards compute a card's height in
 * advance to position the buttons under it, so a line that wraps to three rows must be <em>counted</em>
 * as three rows or the buttons drift onto the text below. That is why height and drawing live together
 * here rather than being written out twice per screen and kept in step by hand.
 *
 * <p>Continuation lines are indented under the bullet rather than returning to the margin, so a wrapped
 * objective still reads as one item and not as two.
 */
public final class CardText {

    /** Row pitch, matching the one every card in this package already uses. */
    public static final int LINE = 10;

    private CardText() {
    }

    /** How wide the body may be once the bullet has taken its indent. */
    private static int bodyWidth(Font font, String bullet, int width) {
        return Math.max(1, width - font.width(bullet));
    }

    /** Pixels a bulleted, wrapped line will occupy. */
    public static int heightBulleted(Font font, String bullet, Component body, int width) {
        return Math.max(1, font.split(body, bodyWidth(font, bullet, width)).size()) * LINE;
    }

    /** Pixels an unbulleted, wrapped line will occupy. */
    public static int height(Font font, Component body, int width) {
        return Math.max(1, font.split(body, Math.max(1, width)).size()) * LINE;
    }

    /**
     * Draws {@code bullet} once, then {@code body} wrapped and indented under it.
     *
     * @return the y coordinate below the last line drawn, so callers can chain without tracking the
     *         line count themselves — the arithmetic that used to drift
     */
    public static int drawBulleted(GuiGraphics graphics, Font font, String bullet, Component body,
                                   int left, int y, int width, int colour) {
        int indent = font.width(bullet);
        List<FormattedCharSequence> lines = font.split(body, bodyWidth(font, bullet, width));
        if (lines.isEmpty()) {
            return y + LINE;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                graphics.drawString(font, bullet, left, y, colour);
            }
            graphics.drawString(font, lines.get(i), left + indent, y, colour);
            y += LINE;
        }
        return y;
    }

    /** As {@link #drawBulleted}, with no bullet and no indent. */
    public static int draw(GuiGraphics graphics, Font font, Component body,
                           int left, int y, int width, int colour) {
        List<FormattedCharSequence> lines = font.split(body, Math.max(1, width));
        if (lines.isEmpty()) {
            return y + LINE;
        }
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, left, y, colour);
            y += LINE;
        }
        return y;
    }
}
