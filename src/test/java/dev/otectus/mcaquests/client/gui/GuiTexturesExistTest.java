package dev.otectus.mcaquests.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sprite sheet and the code that blits it cannot drift apart.
 *
 * <p>A wrong UV is the cheapest mistake in a GUI and the most expensive to find: nothing throws,
 * nothing logs, the build is green, and the bug is a button wearing a corner of the scrollbar that
 * somebody notices three releases later. There is no compiler between {@link GuiTextures} and
 * {@code tools/gen_gui_textures.py}, so this is the only thing standing between them.
 *
 * <p>The generator writes {@code gui_layout.json} beside the PNGs it draws, naming every rectangle it
 * actually painted. This asserts that each {@code Sprite} constant matches that manifest exactly, and
 * that the manifest describes files which exist and are the size the nine-slice maths assumes.
 *
 * <p>It deliberately does not re-run the generator: the committed PNGs are what ships, and a test
 * that regenerates them would pass on a machine where the committed art was stale.
 */
class GuiTexturesExistTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/mcaquests/textures/gui");
    /**
     * The atlas manifest lives with the generator rather than with the PNGs: the game never reads it,
     * and a shipped jar should not carry ten kilobytes of something only this test looks at.
     */
    private static final Path LAYOUT = Path.of("tools/gui_layout.json");

    /** Both sheets must be this size: {@code blitRepeating}'s common overload assumes it. */
    private static final int EXPECTED_SHEET = 256;

    private static JsonObject layout() {
        assertTrue(Files.isRegularFile(LAYOUT),
                "missing " + LAYOUT.toAbsolutePath() + " — run: python tools/gen_gui_textures.py");
        try {
            return JsonParser.parseString(Files.readString(LAYOUT, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every {@code public static final Sprite} on {@link GuiTextures}. */
    private static List<Field> spriteFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : GuiTextures.class.getDeclaredFields()) {
            if (field.getType() == GuiTextures.Sprite.class
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())) {
                fields.add(field);
            }
        }
        assertTrue(fields.size() > 20, "GuiTextures should declare the whole atlas; found " + fields.size());
        return fields;
    }

    private static GuiTextures.Sprite value(Field field) {
        try {
            return (GuiTextures.Sprite) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("cannot read " + field.getName(), e);
        }
    }

    @Test
    @DisplayName("both sheets exist and are 256x256")
    void sheetsExistAtTheAssumedSize() throws IOException {
        for (String name : new String[]{"panel.png", "icons.png"}) {
            Path file = ASSETS.resolve(name);
            assertTrue(Files.isRegularFile(file),
                    "missing " + file.toAbsolutePath() + " — run: python tools/gen_gui_textures.py");
            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(EXPECTED_SHEET, image.getWidth(), name + " width");
            assertEquals(EXPECTED_SHEET, image.getHeight(), name + " height");
        }
        assertEquals(EXPECTED_SHEET, GuiTextures.SHEET);
        assertEquals(EXPECTED_SHEET, layout().get("sheet").getAsInt(),
                "the generator and GuiTextures disagree about the sheet size");
    }

    @Test
    @DisplayName("every sprite constant matches a rectangle the generator actually drew")
    void everySpriteMatchesTheManifest() {
        JsonObject manifest = layout();
        JsonObject panel = manifest.getAsJsonObject("panel");
        JsonObject icons = manifest.getAsJsonObject("icons");
        List<String> problems = new ArrayList<>();

        for (Field field : spriteFields()) {
            GuiTextures.Sprite sprite = value(field);
            boolean isIcon = sprite.sheet().equals(GuiTextures.ICON_SHEET);
            JsonObject group = isIcon ? icons : panel;
            JsonObject match = null;
            for (String name : group.keySet()) {
                JsonObject entry = group.getAsJsonObject(name);
                if (entry.get("u").getAsInt() == sprite.u() && entry.get("v").getAsInt() == sprite.v()) {
                    match = entry;
                    break;
                }
            }
            if (match == null) {
                problems.add(field.getName() + " points at (" + sprite.u() + "," + sprite.v()
                        + ") on " + (isIcon ? "icons.png" : "panel.png") + ", where nothing is drawn");
                continue;
            }
            if (match.get("w").getAsInt() != sprite.width() || match.get("h").getAsInt() != sprite.height()) {
                problems.add(field.getName() + " is " + sprite.width() + "x" + sprite.height()
                        + " but the generator drew " + match.get("w").getAsInt() + "x"
                        + match.get("h").getAsInt());
            }
            if (match.get("sliceX").getAsInt() != sprite.sliceX()
                    || match.get("sliceY").getAsInt() != sprite.sliceY()) {
                problems.add(field.getName() + " declares slices "
                        + sprite.sliceX() + "/" + sprite.sliceY() + " but the generator drew it for "
                        + match.get("sliceX").getAsInt() + "/" + match.get("sliceY").getAsInt());
            }
        }
        assertTrue(problems.isEmpty(), "sprite atlas and GuiTextures disagree:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("no two sprite constants claim the same rectangle")
    void spritesAreDistinct() {
        TreeSet<String> seen = new TreeSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Field field : spriteFields()) {
            GuiTextures.Sprite sprite = value(field);
            String key = sprite.sheet() + "@" + sprite.u() + "," + sprite.v();
            if (!seen.add(key)) {
                duplicates.add(field.getName() + " reuses " + key);
            }
        }
        assertTrue(duplicates.isEmpty(),
                "two constants for one rectangle is a copy-paste, not a design:\n  "
                        + String.join("\n  ", duplicates));
    }

    @Test
    @DisplayName("every sprite is inside its sheet, and a nine-sliced one has room for its border")
    void spritesFitAndCanBeSliced() {
        List<String> problems = new ArrayList<>();
        for (Field field : spriteFields()) {
            GuiTextures.Sprite sprite = value(field);
            if (sprite.u() < 0 || sprite.v() < 0
                    || sprite.u() + sprite.width() > EXPECTED_SHEET
                    || sprite.v() + sprite.height() > EXPECTED_SHEET) {
                problems.add(field.getName() + " runs off the sheet");
            }
            // An inset wider than half the sprite leaves no middle band to tile on that axis, so the
            // sprite would stretch instead of nine-slicing and its border would smear. The two axes
            // are checked separately because they are set separately -- a divider is 48x3 and needs a
            // real horizontal inset with almost none vertically.
            if (sprite.sliceX() * 2 > sprite.width()) {
                problems.add(field.getName() + " has sliceX " + sprite.sliceX()
                        + ", too large for a sprite " + sprite.width() + " wide");
            }
            if (sprite.sliceY() * 2 > sprite.height()) {
                problems.add(field.getName() + " has sliceY " + sprite.sliceY()
                        + ", too large for a sprite " + sprite.height() + " tall");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n  ", problems));
    }
}
