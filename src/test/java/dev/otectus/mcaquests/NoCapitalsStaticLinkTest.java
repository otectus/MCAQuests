package dev.otectus.mcaquests;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing tripwire: <b>no compiled class may reference an MCA Capitals type.</b>
 *
 * <p><b>No exemption list at all</b>, exactly as {@link NoTownsteadStaticLinkTest} and
 * {@link NoBountifulStaticLinkTest} have none. Capitals is an optional mod most installations will
 * not have, and it is itself compiled against MCA — so linking one of its types would fail not only
 * when Capitals is absent but whenever the installed MCA is not the one it was built against.
 *
 * <p><b>The binding is invisible to this scan by design.</b> {@code CapitalsBinding} holds Capitals'
 * package as a <em>dotted</em> literal for {@code Class.forName}, and a dotted literal can never
 * collide with the slash-form internal names the needle looks for — those are the form the JVM uses
 * for a real class, method or field reference.
 *
 * @see NoBountifulStaticLinkTest the same technique, applied to Bountiful
 */
class NoCapitalsStaticLinkTest {

    private static final byte[] NEEDLE = "com/majesttyx/mcacapitals/".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("no compiled class references com.majesttyx.mcacapitals")
    void noCompiledClassReferencesCapitals() throws IOException {
        List<String> violations = scan();

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference com.majesttyx.mcacapitals. Every Capitals access must "
                        + "go through CapitalsBinding's reflective handles, and every class name must "
                        + "stay a dotted string literal, so the mod keeps loading with Capitals absent "
                        + "and with an MCA it was not built against. Offenders: " + violations);
    }

    private static List<String> scan() throws IOException {
        Path classesDir = TestPaths.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                try {
                    if (containsNeedle(Files.readAllBytes(p), NEEDLE)) {
                        violations.add(relative);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return violations;
    }

    private static boolean containsNeedle(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
