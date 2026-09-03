package dev.otectus.mcaquests;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
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
 * Standing tripwire: <b>Xaero's Minimap is never named in our bytecode, JourneyMap is named only
 * inside {@code compat/journeymap/}, and nothing outside a guarded package reaches into one.</b>
 *
 * <p>The same technique {@link NoTownsteadStaticLinkTest} applies to Townstead, and for a sharper
 * version of the same reason: neither mapping mod is installed for most players, and a class that
 * names a type they do not have is a class that cannot load.
 *
 * <h2>The two mods are guarded differently, because they arrive differently</h2>
 *
 * <p><b>Xaero</b> ships no API package, no annotations and no service loader, so the integration goes
 * looking for it — and can therefore do so entirely by name. {@code MapBinding} matches by name, arity
 * and parameter hints and adapts every handle to an all-{@code Object} shape, so no Xaero class is
 * named anywhere in our bytecode and this rule has <b>no exemption list at all</b>, not even for
 * {@code compat/map} itself.
 *
 * <p><b>JourneyMap</b> comes the other way: it discovers an annotated class implementing
 * {@code IClientPlugin} and calls it. An annotation has to be in the class file and an interface has
 * to be in the {@code implements} clause, so that entry point cannot be built reflectively and
 * {@code compat/journeymap/} is compiled against {@code journeymap-api-neoforge}. The guarantee is
 * therefore containment rather than absence: those classes may name JourneyMap, nothing else may name
 * them, and JourneyMap's own annotation scan — which only runs when JourneyMap is installed — is the
 * only thing that ever loads them.
 *
 * <p>All four scans byte-search the raw constant pool of every {@code .class} under
 * {@code build/classes/java/main} for the modified-UTF8 encoding of an <em>internal (slash)</em> name,
 * which is the form the JVM uses for a real class reference. The Xaero binding deliberately holds its
 * package root as a <em>dotted</em> string, which can never collide with the needle.
 *
 * @see NoMcaStaticLinkTest the same technique, applied to MCA itself
 * @see NoTownsteadStaticLinkTest the same technique, applied to Townstead
 */
class NoMinimapStaticLinkTest {

    /**
     * Trailing slash on purpose, on both: it is what separates the guarded packages from the
     * always-loaded seam types beside them — {@code compat/MapWaypointBackend} and
     * {@code compat/WaypointSpec} sit directly in {@code compat}, and every class that drives the map
     * layer names those.
     */
    private static final String XAERO_PACKAGE_PREFIX = "dev/otectus/mcaquests/compat/map/";
    private static final String JOURNEYMAP_PACKAGE_PREFIX =
            "dev/otectus/mcaquests/compat/journeymap/";

    private static final byte[] JOURNEYMAP_NEEDLE = "journeymap/".getBytes(StandardCharsets.UTF_8);
    private static final byte[] XAERO_NEEDLE = "xaero/".getBytes(StandardCharsets.UTF_8);

    private static final byte[] XAERO_PACKAGE_NEEDLE =
            XAERO_PACKAGE_PREFIX.getBytes(StandardCharsets.UTF_8);
    private static final byte[] JOURNEYMAP_PACKAGE_NEEDLE =
            JOURNEYMAP_PACKAGE_PREFIX.getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("only the JourneyMap package names a JourneyMap type")
    void onlyTheJourneyMapPackageReferencesJourneyMap() throws IOException {
        List<String> violations = scan(JOURNEYMAP_NEEDLE, JOURNEYMAP_PACKAGE_PREFIX);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + JOURNEYMAP_PACKAGE_PREFIX + " statically reference "
                        + "journeymap.*. JourneyMap's API is a compileOnly dependency that most "
                        + "players do not have at runtime, so a class naming it anywhere else cannot "
                        + "load. Offenders: " + violations);
    }

    @Test
    @DisplayName("no compiled class names a Xaero's Minimap type")
    void noCompiledClassReferencesXaero() throws IOException {
        List<String> violations = scan(XAERO_NEEDLE, null);

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference xaero.*. Xaero's Minimap ships no API package at all, "
                        + "so every access must resolve by name through MapBinding. Offenders: "
                        + violations);
    }

    @Test
    @DisplayName("only the map package itself reaches the map package")
    void noAlwaysLoadedClassReferencesTheXaeroPackage() throws IOException {
        List<String> violations = scan(XAERO_PACKAGE_NEEDLE, XAERO_PACKAGE_PREFIX);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + XAERO_PACKAGE_PREFIX + " reference it directly. The only "
                        + "sanctioned entry point is MapWaypointCompat's Class.forName on a dotted "
                        + "class name, which is invisible to this scan by design. Offenders: "
                        + violations);
    }

    @Test
    @DisplayName("nothing outside the JourneyMap package reaches the JourneyMap package")
    void noAlwaysLoadedClassReferencesTheJourneyMapPackage() throws IOException {
        List<String> violations = scan(JOURNEYMAP_PACKAGE_NEEDLE, JOURNEYMAP_PACKAGE_PREFIX);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + JOURNEYMAP_PACKAGE_PREFIX + " reference it directly. Nothing "
                        + "here may: those classes name JourneyMap types, so loading one on a "
                        + "dedicated server or a client without the mod is a NoClassDefFoundError. "
                        + "JourneyMap's own annotation scan is the only sanctioned entry point. "
                        + "Offenders: " + violations);
    }

    /**
     * Every compiled class containing {@code needle}, less those under {@code exemptPrefix}.
     *
     * @param exemptPrefix the one package allowed to hold the reference, or {@code null} when none is
     */
    private static List<String> scan(byte[] needle, @Nullable String exemptPrefix) throws IOException {
        Path classesDir = TestPaths.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                if (exemptPrefix != null && relative.startsWith(exemptPrefix)) {
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
