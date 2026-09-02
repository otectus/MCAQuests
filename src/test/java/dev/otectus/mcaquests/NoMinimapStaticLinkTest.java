package dev.otectus.mcaquests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing tripwire: <b>no compiled class may reference a JourneyMap or Xaero's Minimap type, and
 * nothing outside {@code dev/otectus/mcaquests/compat/map/} may reference that package.</b>
 *
 * <p>The same technique {@link NoTownsteadStaticLinkTest} applies to Townstead, and for a sharper
 * version of the same reason. Neither mapping mod is redistributable and neither is published to a
 * Maven this build can reach: the jars live in a gitignored {@code libs/} folder on one machine. A
 * compile-time dependency would therefore mean the mod could only be built by somebody who had
 * separately downloaded two files from CurseForge — and the resulting classes would be unloadable for
 * every player without both mods installed, which is most of them.
 *
 * <p>So the integration is reflection-only end to end, and this has <b>no exemption list</b> for the
 * first scan — not even for {@code compat/map} itself. {@code MapBinding} matches by name, arity and
 * parameter hints, and adapts every handle to an all-{@code Object} shape, so no JourneyMap or Xaero
 * class is named anywhere in our bytecode.
 *
 * <p>Both scans byte-search the raw constant pool of every {@code .class} under
 * {@code build/classes/java/main} for the modified-UTF8 encoding of an <em>internal (slash)</em>
 * name, which is the form the JVM uses for a real class reference. The bindings deliberately hold
 * their package roots as <em>dotted</em> strings, which can never collide with the needle.
 *
 * @see NoMcaStaticLinkTest the same technique, applied to MCA itself
 * @see NoTownsteadStaticLinkTest the same technique, applied to Townstead
 */
class NoMinimapStaticLinkTest {

    private static final String EXEMPT_PACKAGE_PREFIX = "dev/otectus/mcaquests/compat/map/";

    private static final byte[] JOURNEYMAP_NEEDLE = "journeymap/".getBytes(StandardCharsets.UTF_8);
    private static final byte[] XAERO_NEEDLE = "xaero/".getBytes(StandardCharsets.UTF_8);

    /**
     * Trailing slash on purpose: it is what separates the guarded package {@code compat/map/} from
     * the always-loaded seam types {@code compat/MapWaypointBridge} and
     * {@code compat/NoopMapWaypointBridge}, which sit in {@code compat} with a capital {@code M} and
     * an {@code N}.
     */
    private static final byte[] GUARDED_PACKAGE_NEEDLE =
            "dev/otectus/mcaquests/compat/map/".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("no compiled class names a JourneyMap type")
    void noCompiledClassReferencesJourneyMap() throws IOException {
        List<String> violations = scan(JOURNEYMAP_NEEDLE, false);

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference journeymap.*. Every JourneyMap access must resolve by "
                        + "name through MapBinding, because its API ships as a jar-in-jar that this "
                        + "build cannot depend on and that most players do not have. Offenders: "
                        + violations);
    }

    @Test
    @DisplayName("no compiled class names a Xaero's Minimap type")
    void noCompiledClassReferencesXaero() throws IOException {
        List<String> violations = scan(XAERO_NEEDLE, false);

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference xaero.*. Xaero's Minimap ships no API package at all, "
                        + "so every access must resolve by name through MapBinding. Offenders: "
                        + violations);
    }

    @Test
    @DisplayName("only the map package itself reaches the map package")
    void noAlwaysLoadedClassReferencesTheGuardedPackage() throws IOException {
        List<String> violations = scan(GUARDED_PACKAGE_NEEDLE, true);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + EXEMPT_PACKAGE_PREFIX + " reference it directly. The only "
                        + "sanctioned entry point is MapWaypointCompat's Class.forName on a dotted "
                        + "class name, which is invisible to this scan by design. Offenders: "
                        + violations);
    }

    private static List<String> scan(byte[] needle, boolean exemptGuardedPackage) throws IOException {
        Path classesDir = Paths.get("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                if (exemptGuardedPackage && relative.startsWith(EXEMPT_PACKAGE_PREFIX)) {
                    return;
                }
                try {
                    if (containsNeedle(Files.readAllBytes(p), needle)) {
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
