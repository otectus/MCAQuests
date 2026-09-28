package dev.otectus.mcaquests;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Only the guarded adapter package may name an MCA: Crime type (1.7.1). {@code compat/crime/} is loaded
 * by name from {@code CrimeBridge} after the presence check; a Crime reference anywhere else would be a
 * {@code NoClassDefFoundError} on every install without that mod.
 */
class NoCrimeStaticLinkTest {

    @Test
    void onlyTheCrimeAdapterPackageReferencesCrimeTypes() throws Exception {
        Path root = Path.of("build/classes/java/main");
        byte[] needle = "dev/otectus/mcacrime/".getBytes(StandardCharsets.UTF_8);
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("dev/otectus/mcaquests/compat/crime/")) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
                    for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
                    offenders.add(relative);
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(), "MCA: Crime types outside compat/crime/: " + offenders);
    }
}
