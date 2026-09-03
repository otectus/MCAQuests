package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The card screens wrap the lines they draw, and measure the same lines they wrap.
 *
 * <p>All three drew their objective and reward lines with a bare {@code drawString} into a scissor
 * rectangle exactly one card wide, so anything longer than the card was cut off mid-word — no
 * ellipsis, no scroll, no way to read the rest. It surfaced when a Townstead objective rendered a raw
 * query path and ran off the edge, but the length was the trigger, not the cause: any datapack whose
 * objective ran a few characters long had always been unreadable.
 *
 * <p>The second half is the one that bites silently. Both scrolling screens compute a card's height
 * ahead of drawing it, to place the buttons underneath; a line that wraps to three rows and is counted
 * as one puts the Accept button on top of the text. Wrapping without counting would have traded a
 * visible bug for a subtle one.
 *
 * <p>Rendering cannot be exercised here — a {@code Font} needs a loaded resource pack — so this reads
 * the call sites instead, the same technique the static-link tripwires use. It is a weaker check than
 * a screenshot and a much stronger one than nothing.
 */
class CardTextWrappingTest {

    private static final Path CLIENT = TestPaths.of("src/main/java/dev/otectus/mcaquests/client");

    /** The screens that draw a card of objectives and therefore must wrap and measure. */
    private static final List<String> CARD_SCREENS =
            List.of("QuestMenuScreen.java", "ProjectMenuScreen.java", "QuestLogScreen.java");

    /**
     * A {@code drawString} whose argument list builds a line out of an objective, a reward, a project
     * label or a title. Those are the variable-length ones; a fixed label like "Ready" cannot overflow.
     *
     * <p>Titles were the omission, and both project lists drew theirs raw. A project or a quest is
     * named by a datapack and is as long as its author made it, so it ran off the card exactly as the
     * objective lines used to.
     */
    private static final Pattern UNWRAPPED = Pattern.compile(
            "graphics\\.drawString\\([^;]*?(?:objective|joinRewards|line\\.label\\(\\)|"
                    + "townsteadContext|title\\(\\))[^;]*?;", Pattern.DOTALL);

    @Test
    @DisplayName("no card screen draws a variable-length line without wrapping it")
    void variableLinesAreWrapped() {
        List<String> offenders = new ArrayList<>();
        for (String screen : CARD_SCREENS) {
            Matcher raw = UNWRAPPED.matcher(read(CLIENT.resolve(screen)));
            while (raw.find()) {
                offenders.add(screen + ": " + firstLine(raw.group()));
            }
        }
        assertEquals(List.of(), offenders,
                "these draw a line that can be longer than the card straight into a scissor "
                        + "rectangle, so the overflow is cut off mid-word. Use CardText.drawBulleted "
                        + "or CardText.draw, which wrap and return the y below the last row.");
    }

    /**
     * A screen that wraps must also measure, or its buttons land on top of its text. Checked by
     * insisting the height calculation and the draw both go through the same helper.
     */
    @Test
    @DisplayName("every card screen measures its wrapped lines with the helper that draws them")
    void wrappedLinesAreMeasured() {
        List<String> offenders = new ArrayList<>();
        for (String screen : CARD_SCREENS) {
            String body = read(CLIENT.resolve(screen));
            boolean draws = body.contains("CardText.drawBulleted(") || body.contains("CardText.draw(");
            boolean measures = body.contains("CardText.heightBulleted(") || body.contains("CardText.height(");
            if (draws != measures) {
                offenders.add(screen + (draws ? " wraps but does not measure" : " measures but does not wrap"));
            }
        }
        assertEquals(List.of(), offenders,
                "height and drawing have to agree row for row; a screen that does one and not the "
                        + "other drifts its buttons off the rows they belong to");
    }

    /**
     * The log's height calculation reads a font, so it cannot be static. It was, which is why adding
     * wrapping there required a signature change rather than a one-line edit.
     */
    @Test
    @DisplayName("the quest log measures entries with an instance method, so it can use the font")
    void logHeightsCanMeasureText() {
        String body = read(CLIENT.resolve("QuestLogScreen.java"));
        assertTrue(body.contains("private int entryHeight(") && !body.contains("private static int entryHeight("),
                "entryHeight must be able to reach this.font to measure wrapped text");
        assertTrue(body.contains("private int projectsHeight(")
                        && !body.contains("private static int projectsHeight("),
                "projectsHeight must be able to reach this.font to measure wrapped text");
    }

    private static String firstLine(String snippet) {
        String trimmed = snippet.strip();
        int newline = trimmed.indexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(0, newline).strip() + " …";
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
