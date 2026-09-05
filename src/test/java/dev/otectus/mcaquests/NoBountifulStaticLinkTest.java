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
 * Standing tripwire: <b>no compiled class may reference a Bountiful type.</b>
 *
 * <p><b>No exemption list at all</b>, exactly as {@link NoTownsteadStaticLinkTest} and
 * {@link NoIceAndFireStaticLinkTest} have none. Bountiful is an optional mod that most installations
 * will not have, and it is Kotlin: linking against one of its types would fail not only when the mod
 * is absent but whenever kotlinforforge or Kambrik is a version it did not expect.
 *
 * <p><b>The binding is invisible to this scan by design.</b> {@code BountifulBinding} holds
 * Bountiful's package as a <em>dotted</em> literal for {@code Class.forName}, and a dotted literal
 * can never collide with the slash-form internal names the needle looks for — those are the form the
 * JVM uses for a real class, method or field reference.
 *
 * <p>The one place that genuinely needs the slash form is the jar resource path
 * {@code io/ejekta/bountiful/components/BountyStack.class}, which the hook probe reads out of the
 * mod file. That string is <b>assembled at runtime</b> from the dotted class name
 * ({@code BountifulBinding.bountyStackResource()}) rather than written out, precisely so it never
 * appears in a constant pool. Writing it as a literal would trip this test — which is the correct
 * outcome, because a scan that had to exempt a file would no longer prove anything about that file.
 *
 * @see NoIceAndFireStaticLinkTest the same technique, applied to Ice &amp; Fire
 */
class NoBountifulStaticLinkTest {

    private static final byte[] NEEDLE = "io/ejekta/bountiful/".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("no compiled class references io.ejekta.bountiful")
    void noCompiledClassReferencesBountiful() throws IOException {
        List<String> violations = scan();

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference io.ejekta.bountiful. Every Bountiful access must go "
                        + "through BountifulBinding's reflective handles, and every class name must "
                        + "stay a dotted string literal, so the mod keeps loading with Bountiful "
                        + "absent and with a Kotlin stack it was not built against. Offenders: "
                        + violations);
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
