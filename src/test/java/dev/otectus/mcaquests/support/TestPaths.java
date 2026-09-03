package dev.otectus.mcaquests.support;

import java.nio.file.Path;

/**
 * Resolves a repository-relative path for the tests that read the repo itself (bundled pack parsing,
 * locale parity, doc coverage, the class-file tripwires).
 *
 * <p>Those tests used to say {@code Path.of("src/main/resources/...")} and rely on the working
 * directory being the project directory. Under ModDevGradle's {@code unitTest} runner it is not: the
 * test JVM starts in {@code build/minecraft-junit}, because the runner sets up a Minecraft-shaped run
 * directory around the suite. The {@code test} task therefore passes the project directory as
 * {@code mcaquests.projectRoot} and every such path goes through here.
 */
public final class TestPaths {

    private TestPaths() {
    }

    /** The project directory, from the {@code mcaquests.projectRoot} system property. */
    public static Path projectRoot() {
        String root = System.getProperty("mcaquests.projectRoot");
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("mcaquests.projectRoot is not set; the `test` task must pass it "
                    + "(the MDG unit-test runner's working directory is build/minecraft-junit, not the project)");
        }
        return Path.of(root);
    }

    /** A repository-relative path, resolved against {@link #projectRoot()}. */
    public static Path of(String first, String... more) {
        return projectRoot().resolve(Path.of(first, more));
    }
}
