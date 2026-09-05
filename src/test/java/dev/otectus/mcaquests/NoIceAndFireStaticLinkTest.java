package dev.otectus.mcaquests;

import dev.otectus.mcaquests.support.TestPaths;
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
 * Standing tripwire: <b>no compiled class may reference a type from either build of Ice &amp; Fire.</b>
 *
 * <p><b>No exemption list at all</b>, exactly as {@link NoTownsteadStaticLinkTest} has none. The Ice
 * &amp; Fire integration is registry-only end to end: it asks {@code BuiltInRegistries} and the world's
 * dynamic registries what exists and never calls the mod, so there is no binding, no handle and no
 * reason for either package to appear in our bytecode.
 *
 * <p>The reason to enforce it is the mod id. Two different mods publish as {@code iceandfire} — the
 * original {@code com.github.alexthe666.iceandfire} and Community Edition
 * {@code com.iafenvoy.iceandfire} — so a static reference to either would not merely fail when the
 * mod is absent, it would fail on <em>the other build</em>, which is present, loaded, and reports the
 * mod id we asked about. That is the worst kind of crash: one that only reproduces on half of the
 * installs.
 *
 * <p><b>The flavour probe is invisible to this scan by design.</b>
 * {@code IceAndFireFlavor.CE_CLASS} and {@code ORIGINAL_CLASS} hold the two entry points as
 * <em>dotted</em> string literals for {@code Class.forName}, and a dotted literal can never collide
 * with the slash-form internal names the needles below look for — those are the form the JVM uses for
 * a real class, method or field reference. So the probe keeps working and the tripwire keeps meaning
 * what it says.
 *
 * <p>Scanning is a plain byte search over the raw constant pool of every {@code .class} under
 * {@code build/classes/java/main}: this only has to prove a string is absent, not parse bytecode, so
 * the test stays dependency-free and runs on any JDK.
 *
 * @see NoTownsteadStaticLinkTest the same technique, applied to Townstead
 * @see NoMcaStaticLinkTest the same technique, applied to MCA itself
 */
class NoIceAndFireStaticLinkTest {

    private static final byte[] COMMUNITY_EDITION_NEEDLE =
            "com/iafenvoy/iceandfire/".getBytes(StandardCharsets.UTF_8);

    private static final byte[] ORIGINAL_NEEDLE =
            "com/github/alexthe666/iceandfire/".getBytes(StandardCharsets.UTF_8);

    @Test
    void noCompiledClassReferencesCommunityEdition() throws IOException {
        List<String> violations = scan(COMMUNITY_EDITION_NEEDLE);

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference com.iafenvoy.iceandfire. The Ice & Fire integration "
                        + "is registry-only: every question about its content must go through a "
                        + "BuiltInRegistries lookup, so the mod keeps loading with the original build, "
                        + "with neither, and with a version that has renamed the class. Offenders: "
                        + violations);
    }

    @Test
    void noCompiledClassReferencesTheOriginalMod() throws IOException {
        List<String> violations = scan(ORIGINAL_NEEDLE);

        assertTrue(violations.isEmpty(),
                "Class(es) statically reference com.github.alexthe666.iceandfire. Both builds publish "
                        + "as mod id 'iceandfire', so linking against either crashes on the other. "
                        + "Offenders: " + violations);
    }

    private static List<String> scan(byte[] needle) throws IOException {
        Path classesDir = TestPaths.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
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
