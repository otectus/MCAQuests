package dev.otectus.mcaquests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing tripwire (Townstead spec §3.6): <b>no always-loaded class may reference a Townstead
 * type</b>. Only classes under {@code dev/otectus/mcaquests/compat/townstead/} may, and even those
 * must never classload unless Townstead is actually present — which
 * {@code TownsteadCompat.init()} guarantees with a {@code ModList.isLoaded} check.
 *
 * <p>This matters more here than the mod count suggests. Townstead is compiled against MCA, so its
 * classes carry MCA descriptors in their own constant pools; a stray import would drag a
 * <em>relocated MCA</em> type into ours and reintroduce exactly the crash
 * {@link NoMcaStaticLinkTest} exists to prevent — a {@code NoClassDefFoundError} thrown from
 * whatever handler happened to touch it first.
 *
 * <p>Both scans byte-search the raw constant pool of every {@code .class} under
 * {@code build/classes/java/main} for the modified-UTF8 encoding of an <em>internal (slash)</em>
 * name, which is the form the JVM uses for a real class, method or field reference. A plain byte
 * search is enough — this only has to prove a string is absent, not parse bytecode — so the test
 * stays dependency-free and runs on any JDK.
 *
 * <p><b>The second scan needs no whitelist</b>, unlike {@link NoFtbqClassloadTest}'s. The single
 * sanctioned entry point, {@code TownsteadCompat}, names the implementation class as a
 * <em>dotted</em> string literal for {@code Class.forName}, and a dotted literal can never collide
 * with the slash form the needle looks for. The always-loaded seam types
 * ({@code compat/TownsteadBridge}, the {@code Townstead*View} records) sit in {@code compat} with a
 * capital {@code T}, which differs from the needle's lowercase {@code t} at the first byte after the
 * package separator, so they never match either.
 *
 * @see NoMcaStaticLinkTest the same technique, applied to MCA itself
 * @see NoFtbqClassloadTest the same technique, applied to the optional FTB Quests integration
 */
class NoTownsteadStaticLinkTest {

    private static final String EXEMPT_PACKAGE_PREFIX = "dev/otectus/mcaquests/compat/townstead/";

    private static final byte[] TOWNSTEAD_NEEDLE =
            "com/aetherianartificer/townstead".getBytes(StandardCharsets.UTF_8);

    /**
     * Trailing slash on purpose: it is what separates the guarded package
     * {@code compat/townstead/} from the always-loaded seam types {@code compat/Townstead*}.
     */
    private static final byte[] GUARDED_PACKAGE_NEEDLE =
            "dev/otectus/mcaquests/compat/townstead/".getBytes(StandardCharsets.UTF_8);

    @Test
    void noAlwaysLoadedClassReferencesATownsteadType() throws IOException {
        List<String> violations = scan(TOWNSTEAD_NEEDLE, relative -> false);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + EXEMPT_PACKAGE_PREFIX + " reference com.aetherianartificer."
                        + "townstead. Every Townstead access must go through TownsteadBridge so the mod "
                        + "keeps loading when Townstead is absent, and so Townstead's own MCA "
                        + "descriptors never reach our constant pool. Offenders: " + violations);
    }

    @Test
    void noAlwaysLoadedClassReferencesTheGuardedPackage() throws IOException {
        List<String> violations = scan(GUARDED_PACKAGE_NEEDLE, relative -> false);

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + EXEMPT_PACKAGE_PREFIX + " reference it directly. The only "
                        + "sanctioned entry point is TownsteadCompat's Class.forName on a dotted class "
                        + "name, which is invisible to this scan by design. Offenders: " + violations);
    }

    private static List<String> scan(byte[] needle, Predicate<String> whitelisted) throws IOException {
        Path classesDir = Paths.get("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                if (relative.startsWith(EXEMPT_PACKAGE_PREFIX) || whitelisted.test(relative)) {
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
