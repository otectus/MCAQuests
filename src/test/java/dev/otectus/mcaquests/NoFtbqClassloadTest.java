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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standing tripwire (spec §29.1 #7 / §31.4): no always-loaded class may reference
 * {@code dev.ftb.mods} — only classes under {@code dev/otectus/mcaquests/compat/ftbq/} are
 * permitted to, and even those must never classload unless FTB Quests is actually present
 * (guarded by {@code ModList.isLoaded} in {@code McaQuests}, §10.4).
 *
 * <p>This walks every compiled {@code .class} file under {@code build/classes/java/main} and
 * byte-searches its raw constant pool for the modified-UTF8 encoding of {@code dev/ftb/mods}
 * (the internal-name form the JVM uses for class/method/field references). A plain byte search
 * is sufficient here — we don't need a full bytecode parser, just proof the string is absent —
 * and it keeps this test dependency-free (no ASM/bytecode library) so it runs on any JDK.
 *
 * <p>Re-run this after every later FTB task: it is the enforcement mechanism for the "zero FTB
 * imports outside compat/ftbq" classloading rule, not just a one-time check.
 */
class NoFtbqClassloadTest {

    private static final String EXEMPT_PACKAGE_PREFIX = "dev/otectus/mcaquests/compat/ftbq/";
    private static final byte[] NEEDLE = "dev/ftb/mods".getBytes(StandardCharsets.UTF_8);

    @Test
    void noAlwaysLoadedClassReferencesFtbMods() throws IOException {
        Path classesDir = Paths.get("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                if (relative.startsWith(EXEMPT_PACKAGE_PREFIX)) {
                    return;
                }
                try {
                    if (containsNeedle(Files.readAllBytes(p))) {
                        violations.add(relative);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Class(es) outside " + EXEMPT_PACKAGE_PREFIX + " reference dev.ftb.mods: " + violations);
    }

    private static boolean containsNeedle(byte[] haystack) {
        outer:
        for (int i = 0; i <= haystack.length - NEEDLE.length; i++) {
            for (int j = 0; j < NEEDLE.length; j++) {
                if (haystack[i + j] != NEEDLE[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
