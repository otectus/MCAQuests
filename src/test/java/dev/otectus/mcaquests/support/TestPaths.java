package dev.otectus.mcaquests.support;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves repository paths for the repo-introspection tests (built-in pack parsing, locale parity,
 * classfile scans).
 *
 * <p>PORT: ModDevGradle's {@code unitTest} runner executes tests with {@code build/minecraft-junit}
 * as the working directory, so the old bare relative paths ({@code src/main/resources/...},
 * {@code build/classes/java/main}) no longer resolve. The build injects
 * {@code -Dmcaquests.projectRoot}; when the property is absent (e.g. an IDE runs the test from the
 * project root directly) we walk up from the CWD to the first directory containing
 * {@code settings.gradle}.
 */
public final class TestPaths {

    private static final Path ROOT = locate();

    private TestPaths() {
    }

    public static Path projectRoot() {
        return ROOT;
    }

    /** {@code resolve("src/main/resources")} → absolute path under the project root. */
    public static Path resolve(String relative) {
        return ROOT.resolve(relative);
    }

    private static Path locate() {
        String prop = System.getProperty("mcaquests.projectRoot");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        Path cwd = Path.of("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("settings.gradle"))) {
                return p;
            }
        }
        return cwd;
    }
}
