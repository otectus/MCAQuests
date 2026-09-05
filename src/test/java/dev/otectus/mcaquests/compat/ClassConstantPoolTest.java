package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.compat.bountiful.BountifulBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The constant-pool reader, checked against a class file this build produced.
 *
 * <p>Using a compiled test class as the subject is the point: it is a real class file from a real
 * javac, with a real pool containing every tag the walk has to skip past, and nothing about the
 * assertion depends on a third-party jar being installed. If the walk mishandles a tag it will
 * either throw or lose the constants after it, and both show up here as a needle that is not found.
 */
class ClassConstantPoolTest {

    /** A distinctive literal, so finding it proves the reader read <em>this</em> class. */
    private static final String NEEDLE = "mcaquests-class-constant-pool-test-needle";

    private static Path ownClassFile() {
        return Path.of("build", "classes", "java", "test",
                ClassConstantPoolTest.class.getName().replace('.', '/') + ".class");
    }

    @Test
    @DisplayName("a string constant in a compiled class is found")
    void findsAStringConstant() {
        Path classFile = ownClassFile();
        assertTrue(Files.isRegularFile(classFile),
                classFile.toAbsolutePath() + " does not exist; run `./gradlew test`, which compiles "
                        + "the test sources this reads");

        Set<String> constants = ClassConstantPool.utf8Constants(classFile);
        assertTrue(constants.contains(NEEDLE),
                "the walk lost or skipped a constant; it found " + constants.size() + " of them");
        assertTrue(constants.contains("findsAStringConstant"),
                "method names are UTF-8 constants too, and the hook probe relies on that");
    }

    @Test
    @DisplayName("a missing or unreadable file is an empty answer, never a throw")
    void missingFileIsEmpty() {
        assertTrue(ClassConstantPool.utf8Constants(Path.of("build", "no-such-file.class")).isEmpty());
        assertTrue(ClassConstantPool.utf8Constants(new byte[]{1, 2, 3}).isEmpty(),
                "bytes that are not a class file are an answer of \"could not confirm\", not an error");
    }

    @Test
    @DisplayName("declares() needs the name and the descriptor, not just one of them")
    void declaresNeedsBoth() {
        Set<String> constants = Set.of("tryCashIn", "(I)Z");
        assertTrue(ClassConstantPool.declares(constants, "tryCashIn", "(I)Z"));
        assertFalse(ClassConstantPool.declares(constants, "tryCashIn", "(J)Z"),
                "a method of that name taking different arguments is not the one the hook can use");
        assertFalse(ClassConstantPool.declares(constants, "cashIn", "(I)Z"));
    }

    @Test
    @DisplayName("a class with no tryCashIn is not mistaken for Bountiful's BountyData")
    void thisClassIsNotBountyData() {
        assertFalse(BountifulBinding.tryCashInPresent(ownClassFile()),
                "the hook must only be offered for a class that really declares the method it needs");
        assertFalse(BountifulBinding.tryCashInPresent(null));
    }

    /** Keeps {@link #NEEDLE} in this class's constant pool; javac would fold away an unused field. */
    @Test
    @DisplayName("the needle is a live constant of this class")
    void needleIsLive() {
        assertFalse(NEEDLE.isEmpty());
    }
}
