package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.compat.map.MapBinding.Member;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves the mapping-mod manifests against a real JourneyMap or Xaero jar, in its own class loader.
 *
 * <p>The bindings match by name, arity and parameter hints, so nothing about them is checked by
 * compilation — a method that moves, gains a parameter or loses an overload produces a silently
 * unbound member and no waypoints, in a player's game. This is where that is caught instead.
 *
 * <p>Separate from the ordinary suite because it needs jars nobody can resolve from a Maven: both
 * mods are CurseForge-only, so the paths are supplied by whoever runs it.
 *
 * <pre>
 * ./gradlew mapProbeTest -PjourneymapJar=libs/journeymap-forge-1.20.1-6.0.4.jar \
 *                        -PxaeroJar=libs/xaerominimap-forge-1.20.1-26.4.2.jar
 * </pre>
 *
 * <p>Either may be given alone; a mod with no jar supplied is skipped rather than failed.
 *
 * <h2>The JourneyMap jar is two jars</h2>
 *
 * <p>{@code IClientAPI}, {@code Waypoint} and {@code WaypointFactory} do not live in the JourneyMap
 * jar. They live in {@code META-INF/jarjar/journeymap-api-forge-*.jar} inside it, which Forge unpacks
 * at runtime and this test has to unpack for itself — probing the outer jar alone would leave the
 * whole API unbound and the run would pass vacuously while proving nothing.
 *
 * <p>The mod jars are <b>not</b> deobfuscated first, for the reason the Townstead probe gives: only
 * Minecraft's methods and fields carry SRG names in a production jar, never its class names, so the
 * {@code BlockPos} and {@code ResourceLocation} parameter hints line up exactly as they are.
 */
class MapBindingProbeTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final String JOURNEYMAP_PROPERTY = "mcaquests.map.probe.journeymap";
    private static final String XAERO_PROPERTY = "mcaquests.map.probe.xaero";
    /** Matches the name XaeroWaypoints reports, so a failure message names the same mod. */
    private static final String XAERO_DISPLAY_NAME = "Xaero's Minimap";

    @Test
    @DisplayName("every JourneyMap member the waypoint integration needs resolves against a real jar")
    void journeyMapManifestResolves() throws IOException {
        List<URL> urls = jarUrls(JOURNEYMAP_PROPERTY);
        Assumptions.assumeFalse(urls.isEmpty(),
                "no JourneyMap jar supplied; pass -PjourneymapJar=<path> to run this");

        probe(urls, "JourneyMap", "journeymap.", "api.v2.client.IClientAPI",
                JourneyMapWaypoints.MANIFEST, resolution -> {
                    assertEquals(List.of(), resolution.missing(),
                            "these JourneyMap members did not bind, so quest waypoints would silently "
                                    + "not appear. Check the overload hints on "
                                    + "WaypointFactory.createWaypoint first: it has four forms and two "
                                    + "of them take five arguments");
                    assertTrue(resolution.isBound());
                });
    }

    @Test
    @DisplayName("the JourneyMap client API singleton is reachable without registering a plugin")
    void journeyMapSingletonIsReachable() throws IOException {
        List<URL> urls = jarUrls(JOURNEYMAP_PROPERTY);
        Assumptions.assumeFalse(urls.isEmpty(),
                "no JourneyMap jar supplied; pass -PjourneymapJar=<path> to run this");

        // The whole integration rests on this: JourneyMap's documented entry point is an annotated
        // class implementing an interface, neither of which can be produced reflectively, and the
        // implementation happens to be an enum singleton that can. If that stops being true, the
        // integration needs rethinking rather than patching.
        probe(urls, "JourneyMap", "journeymap.", "api.v2.client.IClientAPI",
                JourneyMapWaypoints.MANIFEST, resolution -> assertTrue(
                        resolution.enumConstant(JourneyMapWaypoints.CLIENT_API_IMPL, "INSTANCE") != null,
                        "journeymap.api.client.impl.ClientAPI.INSTANCE did not resolve; this build may "
                                + "no longer expose its client API as an enum singleton"));
    }

    @Test
    @DisplayName("every Xaero member the waypoint integration needs resolves against a real jar")
    void xaeroManifestResolves() throws IOException {
        List<URL> urls = jarUrls(XAERO_PROPERTY);
        Assumptions.assumeFalse(urls.isEmpty(),
                "no Xaero's Minimap jar supplied; pass -PxaeroJar=<path> to run this");

        probe(urls, XAERO_DISPLAY_NAME, "xaero.", "common.XaeroMinimapSession",
                XaeroWaypoints.MANIFEST, resolution -> {
                    assertEquals(List.of(), resolution.missing(),
                            "these Xaero members did not bind, so quest waypoints would silently not "
                                    + "appear. Check the Waypoint constructor hint first: the "
                                    + "all-primitive form shares its arity with one taking "
                                    + "WaypointColor and WaypointPurpose, and only the primitive one "
                                    + "can be called without naming a Xaero type");
                    assertTrue(resolution.isBound());
                });
    }

    /**
     * Resolves the manifest and hands the result to {@code check} <b>while the loader is still open</b>.
     *
     * <p>A {@code Resolution} outlives nothing: it holds method handles and classes, and anything that
     * asks it to load one more class - {@code enumConstant} does, to reach JourneyMap's singleton -
     * gets a {@code ClassNotFoundException} the moment the loader is closed. Returning the resolution
     * and closing on the way out looks correct and fails only on the assertions that matter.
     */
    private static void probe(List<URL> urls, String modName, String root, String probeClass,
                              List<Member> manifest,
                              java.util.function.Consumer<MapBinding.Resolution> check) {
        // Parent is the test loader, which already carries Minecraft: the manifests hint at BlockPos
        // and ResourceLocation, and two copies of those would never compare equal.
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                MapBindingProbeTest.class.getClassLoader())) {
            check.accept(MapBinding.resolve(modName, root, probeClass, manifest, loader));
        } catch (IOException e) {
            throw new AssertionError("could not open the jar for " + modName, e);
        }
    }

    /**
     * The jars named by {@code property}, plus any jar-in-jar API they carry.
     *
     * <p>Empty when the property is unset, which is how a mod with no jar supplied is skipped.
     */
    private static List<URL> jarUrls(String property) throws IOException {
        String value = System.getProperty(property, "");
        List<URL> urls = new ArrayList<>();
        for (String path : value.split(File.pathSeparator)) {
            if (path.isBlank()) {
                continue;
            }
            Path jar = Path.of(path.trim());
            assertTrue(Files.isRegularFile(jar), "no such jar: " + jar.toAbsolutePath());
            urls.add(jar.toUri().toURL());
            urls.addAll(nestedApiJars(jar));
        }
        return urls;
    }

    /** Extracts {@code META-INF/jarjar/*.jar} to temporary files and returns their URLs. */
    private static List<URL> nestedApiJars(Path jar) throws IOException {
        List<URL> urls = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("META-INF/jarjar/") || !entry.getName().endsWith(".jar")) {
                    continue;
                }
                Path extracted = Files.createTempFile("mcaquests-probe-", ".jar");
                extracted.toFile().deleteOnExit();
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
                }
                urls.add(extracted.toUri().toURL());
            }
        }
        return urls;
    }
}
