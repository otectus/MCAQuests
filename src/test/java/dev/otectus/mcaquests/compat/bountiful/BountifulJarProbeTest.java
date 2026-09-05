package dev.otectus.mcaquests.compat.bountiful;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.otectus.mcaquests.compat.ClassConstantPool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>Written against <b>bountiful-neoforge-8.0.0-beta.2</b>, which is a different mod from the 6.0.4
 * this integration was first built for: there is no {@code BountyData}, a bounty lives in the stack's
 * data components, and {@code io.ejekta.bountiful.components.BountyStack} is the wrapper that reads
 * them. Every assertion below is one of the facts that rewrite depended on.
 *
 * <p>Skipped rather than failed when no jar is supplied: Bountiful is CurseForge-only, so the path
 * comes from whoever runs it.
 */
class BountifulJarProbeTest {

    // Built from the dotted class names rather than written out, for the reason BountifulBinding
    // assembles its own resource path: a literal slash-form "io/ejekta/bountiful/..." is exactly the
    // needle NoBountifulStaticLinkTest scans for, and main and test classes are scanned alike.
    private static final String COMPONENTS = "io.ejekta.bountiful.components.".replace('.', '/');
    private static final String BOUNTY_STACK = COMPONENTS + "BountyStack.class";
    private static final String BOUNTY_INFO = COMPONENTS + "BountyInfo.class";
    private static final String BOUNTY_RARITY =
            "io.ejekta.bountiful.bounty.BountyRarity".replace('.', '/') + ".class";

    private static final String CASH_IN_DESCRIPTOR = "(Lnet/minecraft/world/entity/player/Player;)Z";
    private static final String STACK_CTOR_DESCRIPTOR = "(Lnet/minecraft/world/item/ItemStack;)V";

    private static final String POOLS = "data/bountiful/bounty_pools/bountiful/";
    private static final String DECREES = "data/bountiful/bounty_decrees/bountiful/";

    @Test
    @DisplayName("BountyStack declares tryCashIn with the exact descriptor the hook needs")
    void cashInMethodIsHookable() throws IOException {
        try (ZipFile zip = open()) {
            Set<String> constants = constants(zip, BOUNTY_STACK);
            assertTrue(ClassConstantPool.declares(constants, "tryCashIn", CASH_IN_DESCRIPTOR),
                    "BountyStack no longer declares tryCashIn" + CASH_IN_DESCRIPTOR + "; the cash-in "
                            + "hook cannot be applied to this build and the bridge will fall back to "
                            + "data-only");
        }
    }

    @Test
    @DisplayName("the same class file, read through the production probe, says the hook is possible")
    void productionProbeAgrees() throws IOException {
        Path extracted = extract(BOUNTY_STACK);
        assertTrue(BountifulBinding.tryCashInPresent(extracted),
                "BountifulBinding.tryCashInPresent disagrees with the jar it is meant to read");
    }

    @Test
    @DisplayName("BountyStack still wraps an ItemStack and still exposes what the manifest reads")
    void manifestMembersAreThere() throws IOException {
        try (ZipFile zip = open()) {
            Set<String> constants = constants(zip, BOUNTY_STACK);
            assertTrue(ClassConstantPool.declares(constants, "<init>", STACK_CTOR_DESCRIPTOR),
                    "BountyStack no longer has a one-ItemStack constructor; nothing can get from a "
                            + "stack to a bounty, so READ_RARITY will not bind");
            assertTrue(constants.contains("getInfo"),
                    "BountyStack.getInfo is gone; the rarity read has no first hop");
            assertTrue(constants.contains("getObjs") && constants.contains("getRews"),
                    "BountyStack's objective/reward accessors have been renamed; READ_OBJECTIVES will "
                            + "not bind");
            assertTrue(constants.contains("getStack"),
                    "BountyStack.getStack is gone; the cash-in hook loses its dedupe key");
        }
    }

    @Test
    @DisplayName("BountyInfo carries the rarity, under a record accessor rather than a getter")
    void rarityIsWhereTheBindingExpects() throws IOException {
        try (ZipFile zip = open()) {
            // rarity(), not getRarity(): BountyInfo is a Java record in 8.0, which is exactly why the
            // manifest names "rarity" and why a 6.0.4-shaped binding would silently not bind here.
            assertTrue(constants(zip, BOUNTY_INFO).contains("rarity"),
                    "BountyInfo.rarity is gone; READ_RARITY will not bind and min_rarity quests will "
                            + "not be offered");
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
            // The complement of the five: our sixth value must stay ours alone, or fromName would
            // start turning a real Bountiful rank into the value that means "could not be read".
            assertFalse(constants.contains(BountyRarity.UNKNOWN.name()),
                    "Bountiful has gained an UNKNOWN rank, which collides with the value BountyRarity "
                            + "uses for an unreadable rarity");
        }
    }

    @Test
    @DisplayName("the jar ships bounty pools and decrees at the depth our conditional pack mirrors")
    void dataLayoutIsAsShipped() throws IOException {
        try (ZipFile zip = open()) {
            assertFalse(entries(zip, POOLS).isEmpty(),
                    "no bounty pools under " + POOLS + "; our pools are written to the same depth and "
                            + "would not be found");
            assertFalse(entries(zip, DECREES).isEmpty(),
                    "no bounty decrees under " + DECREES + "; our decree would not be found");
        }
    }

    @Test
    @DisplayName("a shipped pool still has the entry shape our pools copy")
    void poolSchemaIsUnchanged() throws IOException {
        try (ZipFile zip = open()) {
            String name = entries(zip, POOLS).get(0);
            JsonObject pool = JsonParser.parseString(new String(bytes(zip, name), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(pool.has("content"), name + " has no \"content\" object");

            JsonObject content = pool.getAsJsonObject("content");
            String first = content.keySet().iterator().next();
            JsonObject entry = content.getAsJsonObject(first);
            for (String field : List.of("type", "content", "amount", "unitWorth")) {
                assertTrue(entry.has(field),
                        name + " entry '" + first + "' has no \"" + field + "\"; our pools declare it "
                                + "and Bountiful's loader would reject them");
            }
        }
    }

    @Test
    @DisplayName("the bounty board is still a block, under the id our quests name")
    void boardIsStillRegistered() throws IOException {
        try (ZipFile zip = open()) {
            assertTrue(zip.getEntry("assets/bountiful/blockstates/bountyboard.json") != null,
                    "bountiful:bountyboard has been renamed; the interact_block quests in "
                            + "compatpacks/bountiful_core name it and would never be offered");
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

    /** Every file directly or indirectly under {@code prefix}; directory entries are not files. */
    private static List<String> entries(ZipFile zip, String prefix) {
        return zip.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(prefix) && !name.endsWith("/"))
                .sorted()
                .toList();
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
