package dev.otectus.mcaquests.compat.iceandfire;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays {@link IceAndFireRegistryManifest} against a real Ice &amp; Fire jar.
 *
 * <p>The manifest is a set of expectations about ids in somebody else's mod, and the only thing that
 * can prove an expectation still holds is the mod. Two jars are accepted and either may be given
 * alone, because the whole point is that the two builds differ:
 *
 * <pre>
 *   ./gradlew iceAndFireProbeTest -PiceandfireCeJar=&lt;path&gt; -PiceandfireOriginalJar=&lt;path&gt;
 * </pre>
 *
 * <p><b>No class from either jar is ever loaded.</b> The jar is opened as a zip and the registry
 * class's constant pool is parsed by hand — enough of the class file to walk the pool and no further.
 * Loading would be both the thing the static-link tripwire forbids and impossible in practice: a
 * published mod names Minecraft members by their SRG names, so initialising one in a dev-mapped test
 * JVM throws before it could tell us anything.
 *
 * <p>Skipped rather than failed when a jar is not supplied, because neither is resolvable from a
 * Maven repository: Community Edition is on Modrinth and the original on CurseForge, so the paths
 * come from whoever runs it.
 */
class IceAndFireJarProbeTest {

    private static final String CE_ENTRY = "com/iafenvoy/iceandfire/IceAndFire.class";
    private static final String CE_ENTITIES = "com/iafenvoy/iceandfire/registry/IafEntities.class";
    private static final String CE_ITEMS = "com/iafenvoy/iceandfire/registry/IafItems.class";

    private static final String ORIGINAL_ENTRY = "com/github/alexthe666/iceandfire/IceAndFire.class";
    private static final String ORIGINAL_ENTITIES =
            "com/github/alexthe666/iceandfire/entity/IafEntityRegistry.class";
    private static final String ORIGINAL_ITEMS =
            "com/github/alexthe666/iceandfire/item/IafItemRegistry.class";

    @Test
    void communityEditionJarAgreesWithTheManifest() throws IOException {
        Path jar = jar("mcaquests.iceandfire.probe.ce");

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertTrue(zip.getEntry(CE_ENTRY) != null,
                    jar.getFileName() + " has no " + CE_ENTRY + "; this is not a Community Edition jar, "
                            + "and IceAndFireFlavor.detect would not identify it as one.");

            Set<String> entities = constants(zip, CE_ENTITIES);
            assertMissing(entities, IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES,
                    "quest-safe entities absent from " + CE_ENTITIES);
            // NeoForge CE 2.1.2 uses PartEntity children, not separately registered multipart types.
            // Verify that actual replacement and the remaining technical registry declarations;
            // do not infer a corrected registry spelling merely because the legacy typo vanished.
            Set<String> legacyParts = Set.of("dragon_multipart", "multipart", "hydra_multipart",
                    "cylcops_multipart");
            assertMissing(entities, IceAndFireRegistryManifest.EXCLUDED_TECHNICAL.stream()
                    .filter(id -> !legacyParts.contains(id.getPath())).toList(),
                    "technical entities absent from " + CE_ENTITIES);
            if (!contains(entities, "cylcops_multipart")) {
                assertFalse(contains(entities, "cyclops_multipart"),
                        "CE registered a corrected multipart ID; update the technical exclusion manifest");
                assertTrue(constants(zip, "com/iafenvoy/iceandfire/entity/CyclopsEyeEntity.class")
                        .contains("com/iafenvoy/iceandfire/entity/MultipartPartEntity"),
                        "CE cyclops multipart implementation changed");
                assertTrue(constants(zip, "com/iafenvoy/iceandfire/entity/MultipartPartEntity.class")
                        .contains("net/neoforged/neoforge/entity/PartEntity"),
                        "CE no longer uses NeoForge PartEntity for multipart children");
            }

            for (ResourceLocation myrmex : IceAndFireRegistryManifest.MYRMEX_ENTITIES) {
                assertFalse(contains(entities, myrmex.getPath()),
                        "Community Edition now registers " + myrmex.getPath() + ". Nothing is broken — "
                                + "the myrmex capability is registry-decided and will simply report "
                                + "available — but the manifest's comment about CE is out of date.");
            }

            Set<String> items = constants(zip, CE_ITEMS);
            assertMissing(items, IceAndFireRegistryManifest.DRAGON_SEEKERS,
                    "Dragon Seekers absent from " + CE_ITEMS);
            assertNetheriteArmorDeclarations(zip, items);
        }
    }

    @Test
    void originalJarAgreesWithTheManifest() throws IOException {
        Path jar = jar("mcaquests.iceandfire.probe.original");

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertTrue(zip.getEntry(ORIGINAL_ENTRY) != null,
                    jar.getFileName() + " has no " + ORIGINAL_ENTRY + "; this is not the original mod.");

            Set<String> entities = constants(zip, ORIGINAL_ENTITIES);
            assertMissing(entities, IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES,
                    "quest-safe entities absent from " + ORIGINAL_ENTITIES);
            assertMissing(entities, IceAndFireRegistryManifest.MYRMEX_ENTITIES,
                    "Myrmex absent from " + ORIGINAL_ENTITIES + "; the original mod is the build that "
                            + "has them");
            assertTrue(contains(entities, "cylcops_multipart"),
                    "the original mod still registers the misspelt 'cylcops_multipart'");

            Set<String> items = constants(zip, ORIGINAL_ITEMS);
            for (ResourceLocation seeker : IceAndFireRegistryManifest.DRAGON_SEEKERS) {
                assertFalse(contains(items, seeker.getPath()),
                        "the original mod now has " + seeker.getPath() + "; the dragon_seekers "
                                + "capability is registry-decided, so this is only a documentation drift");
            }
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** CE derives dragon armor IDs; verify the generator inputs and the recipes' exact result IDs. */
    private static void assertNetheriteArmorDeclarations(ZipFile zip, Set<String> items) throws IOException {
        assertTrue(items.contains("dragonarmor_%s_%s"), "CE dragon armor registration format changed");
        Set<String> materials = constants(zip, "com/iafenvoy/iceandfire/data/DragonArmorMaterial.class");
        assertTrue(materials.contains("netherite"), "CE no longer declares the netherite material ID");
        Set<String> parts = constants(zip, "com/iafenvoy/iceandfire/data/DragonArmorPart.class");
        for (ResourceLocation armor : IceAndFireRegistryManifest.NETHERITE_DRAGON_ARMOR) {
            String part = armor.getPath().substring("dragonarmor_netherite_".length());
            assertTrue(parts.contains(part.toUpperCase(Locale.ROOT)), "CE armor part missing: " + part);
            assertTrue(items.contains(armor.getPath().toUpperCase(Locale.ROOT)),
                    "CE armor registry declaration missing: " + armor);
            assertRecipeResult(zip, armor);
        }
        ResourceLocation hippogryph = IceAndFireRegistryManifest.NETHERITE_HIPPOGRYPH_ARMOR;
        assertTrue(items.contains(hippogryph.getPath()), "CE hippogryph armor registration ID changed");
        assertRecipeResult(zip, hippogryph);
    }

    private static void assertRecipeResult(ZipFile zip, ResourceLocation item) throws IOException {
        ZipEntry recipe = zip.getEntry("data/iceandfire/recipes/" + item.getPath() + ".json");
        if (recipe == null) recipe = zip.getEntry("data/iceandfire/recipe/" + item.getPath() + ".json");
        assertTrue(recipe != null, "CE armor recipe missing: " + item);
        try (InputStream input = zip.getInputStream(recipe)) {
            var root = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            var result = root.getAsJsonObject().getAsJsonObject("result");
            var id = result.has("item") ? result.get("item") : result.get("id");
            assertEquals(item.toString(), id.getAsString(), "CE armor recipe targets a different registry ID");
        }
    }

    /** The supplied jar, or a skipped test when this run was not given one. */
    private static Path jar(String property) {
        String value = System.getProperty(property, "").trim();
        Assumptions.assumeFalse(value.isEmpty(),
                "no jar supplied for -D" + property + "; skipping");
        Path path = Paths.get(value);
        assertTrue(Files.isRegularFile(path), value + " is not a file");
        return path;
    }

    private static void assertMissing(Set<String> constants, List<ResourceLocation> expected, String what) {
        List<String> missing = new ArrayList<>();
        for (ResourceLocation id : expected) {
            if (!contains(constants, id.getPath())) {
                missing.add(id.getPath());
            }
        }
        assertTrue(missing.isEmpty(), what + ": " + missing);
    }

    /**
     * Substring rather than equality: a registry class may hold {@code "fire_dragon"} on its own or
     * inside {@code "iceandfire:fire_dragon"}, and which one it is is an implementation detail of the
     * mod, not something the manifest should have an opinion about.
     */
    private static boolean contains(Set<String> constants, String path) {
        for (String constant : constants) {
            if (constant.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /** Every UTF-8 constant in one class of the jar. */
    private static Set<String> constants(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        assertTrue(entry != null, zip.getName() + " has no " + entryName
                + "; the registry class has been renamed and this probe needs updating.");
        try (InputStream in = zip.getInputStream(entry)) {
            return utf8Constants(ByteBuffer.wrap(in.readAllBytes()));
        }
    }

    /**
     * Walks the constant pool and collects every {@code CONSTANT_Utf8}, which is where a registry
     * class's ids live. Deliberately dependency-free, in the same spirit as
     * {@link dev.otectus.mcaquests.support.ClassFileConstants} — that helper reads {@code int}
     * constants and this one needs strings, but neither adds a bytecode library to the test classpath.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html">JVMS §4.4</a>
     */
    private static Set<String> utf8Constants(ByteBuffer buf) {
        Set<String> out = new HashSet<>();
        assertTrue(buf.remaining() >= 10 && buf.getInt() == 0xCAFEBABE, "not a class file");
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
                case 3, 4 -> buf.getInt();                          // Integer, Float
                case 5, 6 -> {                                       // Long, Double
                    buf.getLong();
                    index++; // these occupy two pool slots (JVMS 4.4.5)
                }
                case 7, 8, 16, 19, 20 -> buf.getShort();             // *_index forms
                case 15 -> {                                         // MethodHandle
                    buf.get();
                    buf.getShort();
                }
                case 9, 10, 11, 12, 17, 18 -> buf.getInt();          // pairs of indices
                default -> throw new IllegalStateException(
                        "unknown constant pool tag " + tag + " at index " + index);
            }
        }
        return out;
    }
}
