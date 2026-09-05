package dev.otectus.mcaquests.compat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the UTF-8 constants out of a {@code .class} file without loading it.
 *
 * <p>This exists so a probe can ask "does this class declare that method, with that descriptor?"
 * about a mod we must never link against. Loading the class would be the very thing the static-link
 * tripwires forbid, and would fail anyway: a published mod names Minecraft members by their SRG
 * names, so initialising one inside a dev-mapped JVM throws before it could answer anything.
 *
 * <p>A constant pool walk is enough, because every method name and every method descriptor in a class
 * file is a {@code CONSTANT_Utf8} entry. It is deliberately coarse — a name and a descriptor being
 * present does not prove they belong to the <em>same</em> method — and that is the right trade here:
 * the answer only decides whether an optional hook is offered at all, and the hook itself verifies
 * the real shape before it applies.
 *
 * <p>Dependency-free on purpose. Adding a bytecode library to the production classpath to read six
 * strings would be a shipped dependency for a diagnostic.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html">JVMS §4.4</a>
 */
public final class ClassConstantPool {

    private static final int MAGIC = 0xCAFEBABE;

    private ClassConstantPool() {
    }

    /**
     * Every UTF-8 constant in the class file at {@code path}, or an empty set when it cannot be read.
     *
     * <p>Never throws. A missing, truncated or unfamiliar class file is an answer of "we could not
     * confirm anything", which every caller already has to handle — the mod may simply not be
     * installed — and turning it into an exception would take a reload down over a diagnostic.
     */
    public static Set<String> utf8Constants(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return utf8Constants(in.readAllBytes());
        } catch (IOException | RuntimeException e) {
            return Set.of();
        }
    }

    /** As {@link #utf8Constants(Path)}, for bytes already in hand (a zip entry, say). */
    public static Set<String> utf8Constants(byte[] classFile) {
        try {
            return walk(ByteBuffer.wrap(classFile));
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    /**
     * True when the class declares {@code memberName} and {@code descriptor} somewhere in its pool.
     *
     * <p>The descriptor is what makes this worth checking at all: a method called {@code tryCashIn}
     * that takes different arguments is not the method we can hook, and a hook that applied to it
     * would be worse than no hook.
     */
    public static boolean declares(Set<String> constants, String memberName, String descriptor) {
        return constants.contains(memberName) && constants.contains(descriptor);
    }

    private static Set<String> walk(ByteBuffer buf) {
        Set<String> out = new HashSet<>();
        if (buf.remaining() < 10 || buf.getInt() != MAGIC) {
            return Set.of();
        }
        buf.getShort(); // minor version
        buf.getShort(); // major version

        int poolCount = buf.getShort() & 0xFFFF;
        for (int index = 1; index < poolCount; index++) {
            int tag = buf.get() & 0xFF;
            switch (tag) {
                case 1 -> { // Utf8
                    byte[] bytes = new byte[buf.getShort() & 0xFFFF];
                    buf.get(bytes);
                    out.add(new String(bytes, StandardCharsets.UTF_8));
                }
                case 3, 4 -> buf.getInt();                  // Integer, Float
                case 5, 6 -> {                               // Long, Double
                    buf.getLong();
                    index++; // these occupy two pool slots (JVMS 4.4.5)
                }
                case 7, 8, 16, 19, 20 -> buf.getShort();     // *_index forms
                case 15 -> {                                 // MethodHandle
                    buf.get();
                    buf.getShort();
                }
                case 9, 10, 11, 12, 17, 18 -> buf.getInt();  // pairs of indices
                // An unknown tag means the walk has lost the plot; whatever was collected up to here
                // is still true, and guessing past it would invent constants that are not there.
                default -> {
                    return out;
                }
            }
        }
        return out;
    }
}
