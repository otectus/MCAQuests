package dev.otectus.mcaquests;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoUltimaStaticLinkTest {
    @Test
    void productionClassesContainNoUltimaTypeReferences() throws Exception {
        Path root = Path.of("build/classes/java/main");
        byte[] needle = "com/ultimakingdoms/".getBytes(StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                byte[] bytes = Files.readAllBytes(file);
                outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
                    for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
                    offenders.add(root.relativize(file).toString());
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Ultima must remain reflection-only: " + offenders);
    }
}
