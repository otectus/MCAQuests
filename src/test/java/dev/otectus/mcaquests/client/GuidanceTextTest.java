package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.support.TestPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one place a destination becomes text.
 *
 * <p>Three surfaces draw that line — the tracker, the quest log and the world marker's label — and the
 * point of {@link GuidanceText} is that they cannot phrase it differently. The distance and the
 * bearing need a live player and are covered by {@code MarkerGeometryTest} and {@code HudDirectionTest};
 * what is checked here is the part that is pure and the part that silently breaks.
 *
 * <p>The silent breakage is a mistyped translation key. Minecraft renders an unknown key as the key
 * itself, so {@code mcaquests.hud.target_aprox_coords} would put a raw identifier on the HUD of every
 * player with an approximate destination, and nothing would throw.
 */
class GuidanceTextTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final Path EN_US =
            TestPaths.of("src/main/resources/assets/mcaquests/lang/en_us.json");

    private static GuidanceTarget target(boolean approximate, boolean lastKnown) {
        return new GuidanceTarget(GuidanceKind.STRUCTURE, OptionalInt.empty(),
                new BlockPos(1024, 68, -330), Level.OVERWORLD, Component.literal("Fortress"),
                24, approximate, lastKnown, 0.0F);
    }

    @Test
    @DisplayName("clipboard coordinates are space-separated and unlocalised")
    void plainCoordinates() {
        // Not read, pasted: /tp, a minimap's coordinate box and a chat message all want this spacing,
        // and none of them want a locale's decimal comma in the middle of it.
        assertEquals("1024 68 -330", GuidanceText.plain(new BlockPos(1024, 68, -330)));
        assertEquals("-4096 -48 -30000000",
                GuidanceText.plain(new BlockPos(-4096, -48, -30_000_000)));
    }

    @Test
    @DisplayName("last-known beats approximate when choosing how to word the line")
    void lastKnownWinsOverApproximate() {
        // They are different claims: "about 400 blocks" is about precision, "last seen 400 blocks away"
        // is about age. A position can honestly be both, and the stronger caveat is the one to print.
        assertEquals("mcaquests.hud.target_last_known", GuidanceText.key(target(true, true)));
        assertEquals("mcaquests.hud.target_last_known_coords",
                GuidanceText.coordinatesKey(target(true, true)));

        assertEquals("mcaquests.hud.target_approx", GuidanceText.key(target(true, false)));
        assertEquals("mcaquests.hud.target", GuidanceText.key(target(false, false)));
    }

    @Test
    @DisplayName("every line this can produce names a translation key that exists")
    void everyKeyExists() {
        JsonObject en = load();
        List<String> missing = new ArrayList<>();
        for (boolean approximate : new boolean[]{false, true}) {
            for (boolean lastKnown : new boolean[]{false, true}) {
                GuidanceTarget target = target(approximate, lastKnown);
                check(en, missing, GuidanceText.key(target));
                check(en, missing, GuidanceText.coordinatesKey(target));
                check(en, missing, target.approximate()
                        ? "mcaquests.marker.label_approx" : "mcaquests.marker.label");
            }
        }
        check(en, missing, "mcaquests.hud.coords");
        check(en, missing, "mcaquests.hud.other_dimension");
        check(en, missing, "mcaquests.hud.other_dimension_coords");

        assertTrue(missing.isEmpty(), "Minecraft renders an unknown key as the key itself, so these "
                + "would appear verbatim on the tracker: " + missing);
    }

    @Test
    @DisplayName("the coordinate keys take exactly the arguments the formatter passes")
    void placeholderCounts() {
        // A key with the wrong number of placeholders throws at render time, taking the HUD with it.
        JsonObject en = load();
        assertEquals(3, placeholders(en, "mcaquests.hud.coords"), "x, y and z");
        for (String key : List.of("mcaquests.hud.target_coords", "mcaquests.hud.target_approx_coords",
                "mcaquests.hud.target_last_known_coords")) {
            assertEquals(4, placeholders(en, key), key + " takes label, distance, bearing, coordinates");
        }
        assertEquals(3, placeholders(en, "mcaquests.hud.other_dimension_coords"),
                "label, dimension and coordinates — the bearing is the one thing it cannot honestly say");
    }

    private static void check(JsonObject en, List<String> missing, String key) {
        if (!en.has(key)) {
            missing.add(key);
        }
    }

    private static int placeholders(JsonObject en, String key) {
        assertTrue(en.has(key), "missing key " + key);
        return (int) java.util.regex.Pattern.compile("%(?:\\d+\\$)?[sd]")
                .matcher(en.get(key).getAsString()).results().count();
    }

    private static JsonObject load() {
        try {
            return JsonParser.parseString(Files.readString(EN_US, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
