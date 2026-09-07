package dev.otectus.mcaquests.data;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Covers every bundled resource, including conditional packs skipped without their optional mods. */
class ResourceIntegrityTest {
    private static final Path PROJECT = Path.of(System.getProperty("mcaquests.projectRoot", "."));
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");

    @Test
    void allJsonIsWellFormedAndHasNoDuplicateKeys() throws Exception {
        try (var paths = Files.walk(RESOURCES)) {
            var files = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")
                    || path.toString().endsWith(".mcmeta")).sorted().toList();
            assertTrue(files.size() > 300, "resource inventory unexpectedly empty");
            for (Path path : files) {
                try (JsonReader reader = new JsonReader(Files.newBufferedReader(path))) {
                    readValue(reader, path);
                    assertEquals(JsonToken.END_DOCUMENT, reader.peek(), path.toString());
                }
            }
        }
    }

    private static void readValue(JsonReader reader, Path source) throws Exception {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertTrue(keys.add(key), () -> source + " duplicates '" + key + "' at " + reader.getPath());
                    readValue(reader, source);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) readValue(reader, source);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> fail(source + ": unexpected JSON token at " + reader.getPath());
        }
    }

    @Test
    void pngResourcesDecodeAndLiteralTextureReferencesExist() throws Exception {
        try (var paths = Files.walk(RESOURCES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".png")).toList()) {
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, path.toString());
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0, path.toString());
            }
        }
        var texture = Pattern.compile("(?:McaQuests\\.MOD_ID|\"mcaquests\")\\s*,\\s*\"(textures/[^\"]+\\.png)\"");
        try (var paths = Files.walk(PROJECT.resolve("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = texture.matcher(Files.readString(path));
                while (matcher.find()) {
                    Path asset = RESOURCES.resolve("assets/mcaquests").resolve(matcher.group(1));
                    assertTrue(Files.isRegularFile(asset), path + " references missing " + asset);
                }
            }
        }
    }
}
