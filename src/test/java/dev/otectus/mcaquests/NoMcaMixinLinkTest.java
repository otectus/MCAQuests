package dev.otectus.mcaquests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import dev.otectus.mcaquests.support.TestPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Gift bridge's own tripwire: <b>a mixin that targets MCA must still not link MCA</b>.
 *
 * <p>{@link NoMcaStaticLinkTest} already proves no compiled class references an MCA type. This one
 * covers the way that guarantee is most likely to be lost from here on: a mixin into somebody else's
 * class is written by naming that class, and the obvious spellings are wrong in opposite directions.
 * Writing the target in internal form ({@code forge/net/mca/...}) puts an MCA package into the
 * constant pool of a class the mod always ships, so the tripwire fires on every installation, MCA
 * present or not. Writing {@code @Shadow} fields or a typed parameter does the same thing through the
 * descriptor. Both compile perfectly.
 *
 * <p>So the four gift mixins and the classes behind them are checked twice: no internal-form MCA root
 * anywhere in their bytes, and every MCA class they do name — as a target string, a probe key, a
 * diagnostic — spelled with dots. A dotted name is inert: the JVM never resolves it, and this mod's
 * runtime binding is what turns it into a class, if MCA is even installed.
 *
 * <p>Byte-scanned rather than reflected over, for the same reason its older sibling is: the question
 * is whether a string is present, and answering it needs no bytecode library and no class loading.
 *
 * @see NoMcaStaticLinkTest the whole-mod version of the same rule
 */
class NoMcaMixinLinkTest {

    /** Every MCA package root this mod has ever seen, in internal (JVM) form. */
    private static final String[] INTERNAL_ROOTS = {"forge/net/mca", "net/conczin/mca", "net/mca/"};

    /** The same roots as they may legitimately appear: dotted, and therefore inert. */
    private static final String[] DOTTED_ROOTS = {"forge.net.conczin.mca.", "forge.net.mca.",
            "net.conczin.mca.", "net.mca."};

    /** The class MCA's gift command reaches, relative to whichever root is live. */
    private static final String HANDLER_RELATIVE = "entity.interaction.VillagerCommandHandler";

    private static final String[] SCANNED_PACKAGES = {
            "dev/otectus/mcaquests/mixin/mca", "dev/otectus/mcaquests/compat/mca"};

    @Test
    @DisplayName("no gift-bridge class names an MCA package in internal form")
    void giftBridgeClassesNeverLinkMca() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachClass((relative, bytes) -> {
            for (String root : INTERNAL_ROOTS) {
                if (contains(bytes, root)) {
                    violations.add(relative + " -> " + root);
                }
            }
        });

        assertTrue(violations.isEmpty(),
                "A gift-bridge class references an MCA package in internal form. A mixin target must be "
                        + "a dotted string and nothing in these packages may name an MCA type, or the mod "
                        + "links MCA on every installation. Offenders: " + violations);
    }

    @Test
    @DisplayName("all four mixin variants are present and name their target with dots")
    void everyVariantNamesItsTargetDotted() throws IOException {
        List<String> found = new ArrayList<>();
        forEachClass((relative, bytes) -> {
            if (!relative.startsWith("dev/otectus/mcaquests/mixin/mca/")) {
                return;
            }
            for (String root : DOTTED_ROOTS) {
                if (contains(bytes, root + HANDLER_RELATIVE)) {
                    found.add(root);
                }
            }
        });

        for (String root : DOTTED_ROOTS) {
            assertTrue(found.contains(root),
                    "No compiled mixin targets '" + root + HANDLER_RELATIVE + "'. All four package "
                            + "roots ship, because the root cannot be inferred from MCA's version "
                            + "number. Found: " + found);
        }
    }

    private interface ClassVisitor {
        void accept(String relative, byte[] bytes);
    }

    private static void forEachClass(ClassVisitor visitor) throws IOException {
        // PORT: resolved through TestPaths, because MDG's unit-test runner starts the JVM in
        // build/minecraft-junit rather than in the project directory.
        Path classesDir = TestPaths.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classesDir),
                "build/classes/java/main does not exist; run `./gradlew compileJava` (or `test`, "
                        + "which depends on it) before running this test directly.");
        boolean[] any = {false};
        try (Stream<Path> paths = Files.walk(classesDir)) {
            paths.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                String relative = classesDir.relativize(p).toString().replace('\\', '/');
                boolean scanned = false;
                for (String pkg : SCANNED_PACKAGES) {
                    scanned |= relative.startsWith(pkg + "/");
                }
                if (!scanned) {
                    return;
                }
                any[0] = true;
                try {
                    visitor.accept(relative, Files.readAllBytes(p));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        assertTrue(any[0], "No compiled classes found under " + String.join(", ", SCANNED_PACKAGES)
                + "; the scan would pass vacuously.");
    }

    private static boolean contains(byte[] haystack, String needle) {
        byte[] bytes = needle.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= haystack.length - bytes.length; i++) {
            for (int j = 0; j < bytes.length; j++) {
                if (haystack[i + j] != bytes[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
