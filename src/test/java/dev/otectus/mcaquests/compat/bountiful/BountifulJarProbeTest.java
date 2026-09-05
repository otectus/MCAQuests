package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.ClassConstantPool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays {@link BountifulBinding}'s expectations against a real Bountiful jar.
 *
 * <p>The manifest is a set of claims about somebody else's mod, and only the mod can prove them:
 *
 * <pre>
 *   ./gradlew bountifulProbeTest -PbountifulJar=&lt;path&gt;
 * </pre>
 *
 * <p><b>No class from the jar is ever loaded.</b> The jar is opened as a zip and constant pools are
 * parsed by hand. That is not merely the tripwire's rule — Bountiful is Kotlin and needs
 * kotlinforforge and Kambrik to load at all, neither of which is on this build's classpath, so a
 * probe that loaded anything could not run.
 *
 * <p>Skipped rather than failed when no jar is supplied: Bountiful is CurseForge-only, so the path
 * comes from whoever runs it.
 */
class BountifulJarProbeTest {

    // Built from the dotted class names rather than written out, for the reason BountifulBinding
    // assembles its own resource path: a literal slash-form "io/ejekta/bountiful/..." is exactly the
    // needle NoBountifulStaticLinkTest scans for, and main and test classes are scanned alike.
    private static final String PACKAGE = "io.ejekta.bountiful.bounty.".replace('.', '/');
    private static final String BOUNTY_DATA = PACKAGE + "BountyData.class";
    private static final String BOUNTY_INFO = PACKAGE + "BountyInfo.class";
    private static final String BOUNTY_INFO_COMPANION = PACKAGE + "BountyInfo$Companion.class";
    private static final String BOUNTY_RARITY = PACKAGE + "BountyRarity.class";

    private static final String CASH_IN_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z";

    /** Kambrik's item-data base class, which is where the rarity reader actually lives. */
    private static final String ITEM_DATA_JSON = "io.ejekta.kambrik.serial.ItemDataJson".replace('.', '/');

    @Test
    @DisplayName("BountyData declares tryCashIn with the exact descriptor the hook needs")
    void cashInMethodIsHookable() throws IOException {
        try (ZipFile zip = open()) {
            Set<String> constants = constants(zip, BOUNTY_DATA);
            assertTrue(ClassConstantPool.declares(constants, "tryCashIn", CASH_IN_DESCRIPTOR),
                    "BountyData no longer declares tryCashIn" + CASH_IN_DESCRIPTOR + "; the cash-in "
                            + "hook cannot be applied to this build and the bridge will fall back to "
                            + "data-only");
            assertTrue(constants.contains("getObjectives") && constants.contains("getRewards"),
                    "BountyData's objective/reward accessors have been renamed; READ_OBJECTIVES will "
                            + "not bind");
        }
    }

    @Test
    @DisplayName("the same class file, read through the production probe, says the hook is possible")
    void productionProbeAgrees() throws IOException {
        Path extracted = extract(BOUNTY_DATA);
        assertTrue(BountifulBinding.tryCashInPresent(extracted),
                "BountifulBinding.tryCashInPresent disagrees with the jar it is meant to read");
    }

    @Test
    @DisplayName("BountyInfo carries the rarity, and its companion inherits Kambrik's stack reader")
    void rarityIsWhereTheBindingExpects() throws IOException {
        try (ZipFile zip = open()) {
            assertTrue(constants(zip, BOUNTY_INFO).contains("getRarity"),
                    "BountyInfo.getRarity is gone; READ_RARITY will not bind and min_rarity quests "
                            + "will not be offered");

            // The companion declares no ItemStack method of its own: the reader is inherited from
            // Kambrik's ItemDataJson, where it is generic and so erases to an Object return. That is
            // why BountifulBinding matches by shape over getMethods() -- which includes inherited
            // methods -- rather than looking for a BountyInfo return type that no descriptor has.
            assertTrue(constants(zip, BOUNTY_INFO_COMPANION).contains(ITEM_DATA_JSON),
                    "BountyInfo$Companion no longer extends Kambrik's ItemDataJson, so the "
                            + "(ItemStack) -> BountyInfo reader the binding shape-matches may have "
                            + "moved; re-check BountifulBinding.bindByShape against the new base class");
        }
    }

    @Test
    @DisplayName("BountyRarity still has exactly the five ranks BountyRarity mirrors")
    void rarityRanksMatchOurEnum() throws IOException {
        try (ZipFile zip = open()) {
            Set<String> constants = constants(zip, BOUNTY_RARITY);
            for (BountyRarity rank : BountyRarity.values()) {
                if (rank == BountyRarity.UNKNOWN) {
                    continue; // ours, never Bountiful's
                }
                assertTrue(constants.contains(rank.name()),
                        "Bountiful no longer has the rank " + rank + "; a pack asking for it would "
                                + "silently never match");
            }
        }
    }

    @Test
    @DisplayName("the jar ships bounty pools at the depth our conditional pack mirrors")
    void dataLayoutIsAsShipped() throws IOException {
        try (ZipFile zip = open()) {
            assertTrue(zip.stream().anyMatch(entry ->
                            entry.getName().startsWith("data/bountiful/bounty_pools/")),
                    "no bounty_pools in the jar; our pools are written to the same depth and would "
                            + "not be found");
            assertTrue(zip.stream().anyMatch(entry ->
                            entry.getName().startsWith("data/bountiful/bounty_decrees/")),
                    "no bounty_decrees in the jar; our decree would not be found");
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static ZipFile open() throws IOException {
        return new ZipFile(jar().toFile());
    }

    /** The supplied jar, or a skipped test when this run was not given one. */
    private static Path jar() {
        String value = System.getProperty("mcaquests.bountiful.probe.jar", "").trim();
        Assumptions.assumeFalse(value.isEmpty(),
                "no jar supplied for -Dmcaquests.bountiful.probe.jar; skipping");
        Path path = Paths.get(value);
        assertTrue(Files.isRegularFile(path), value + " is not a file");
        return path;
    }

    private static Set<String> constants(ZipFile zip, String entryName) throws IOException {
        return ClassConstantPool.utf8Constants(bytes(zip, entryName));
    }

    private static byte[] bytes(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        assertTrue(entry != null, zip.getName() + " has no " + entryName
                + "; the class has been renamed or moved and the binding needs updating.");
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    /** One class file on disk, so the production {@code Path}-taking probe can be run against it. */
    private static Path extract(String entryName) throws IOException {
        try (ZipFile zip = open()) {
            Path out = Files.createTempFile("mcaquests-bountiful-probe", ".class");
            out.toFile().deleteOnExit();
            Files.write(out, bytes(zip, entryName));
            return out;
        }
    }
}
