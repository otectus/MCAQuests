package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.client.marker.MarkerColours;
import dev.otectus.mcaquests.compat.map.MapBinding.Member;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
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
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks both map integrations against a real JourneyMap or Xaero jar, in its own class loader.
 *
 * <p>The two halves ask different questions, because the two integrations are bound differently.
 *
 * <p><b>Xaero</b> ships no API at all, so its manifest matches by name, arity and parameter hints and
 * nothing about it is checked by compilation — a method that moves, gains a parameter or loses an
 * overload produces a silently unbound member and no waypoints, in a player's game. This is where that
 * is caught instead.
 *
 * <p><b>JourneyMap</b> is compiled against, so javac already pins every member the backend calls. What
 * goes unchecked there is <em>drift</em>: the API the game loads is the {@code journeymap-api-forge}
 * jar-in-jar inside the mod, while the API this build compiles against comes from a Maven mirror. They
 * are the same artifact today. Nothing but this test would notice the day they stopped being.
 *
 * <p>Separate from the ordinary suite because it needs jars nobody can resolve from a Maven: both mods
 * are CurseForge-only, so the paths are supplied by whoever runs it.
 *
 * <pre>
 * ./gradlew mapProbeTest -PjourneymapJar=libs/journeymap-forge-1.20.1-6.0.4.jar \
 *                        -PxaeroJar=libs/xaerominimap-forge-1.20.1-26.4.2.jar
 * </pre>
 *
 * <p>Either may be given alone; a mod with no jar supplied is skipped rather than failed. Pass
 * {@code -PrequireMapJars=true} to invert that: a release gate must not be allowed to pass on a run
 * that quietly probed nothing.
 *
 * <h2>The JourneyMap jar is two jars</h2>
 *
 * <p>{@code IClientAPI}, {@code Waypoint} and {@code WaypointFactory} do not live in the JourneyMap
 * jar. They live in {@code META-INF/jarjar/journeymap-api-forge-*.jar} inside it, which Forge unpacks
 * at runtime and this test has to unpack for itself — probing the outer jar alone would find no API
 * whatsoever and the run would pass vacuously while proving nothing.
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
    /** Set by {@code -PrequireMapJars=true}: a jar that was not supplied fails instead of skipping. */
    private static final String REQUIRE_PROPERTY = "mcaquests.map.probe.require";
    /** Matches the name XaeroWaypoints reports, so a failure message names the same mod. */
    private static final String XAERO_DISPLAY_NAME = "Xaero's Minimap";

    /**
     * Every API member {@code compat/journeymap} compiles against, and the class it comes from.
     *
     * <p>Class name to method name and parameter count, which is as much as a class file can be asked
     * about without also resolving Minecraft's half of every signature. A member that has been
     * renamed, moved or given another parameter turns up here as a missing entry — the same failure a
     * player would report as "JourneyMap stopped showing quest destinations", except that a compiled
     * backend meeting a changed API throws {@code NoSuchMethodError} rather than failing quietly.
     */
    private static final Map<String, List<String>> JOURNEYMAP_API = Map.of(
            "journeymap.api.v2.common.JourneyMapPlugin", List.of("apiVersion/0"),
            "journeymap.api.v2.client.IClientPlugin", List.of("getModId/0", "initialize/1"),
            "journeymap.api.v2.client.IClientAPI",
            List.of("addWaypoint/2", "removeWaypoint/2", "getWaypoint/2"),
            "journeymap.api.v2.common.waypoint.WaypointFactory", List.of("createWaypoint/5"),
            "journeymap.api.v2.common.waypoint.Waypoint",
            List.of("getId/0", "setName/1", "setBlockPos/1", "setColor/1", "setDimensions/1",
                    "setPrimaryDimension/1", "setShowOnMap/1", "setShowInWorld/1", "setShowBeacon/1"));

    @Test
    @DisplayName("the API JourneyMap ships still declares every member the plugin compiles against")
    void journeyMapEmbeddedApiMatchesCompiledApi() throws IOException {
        List<URL> urls = jarUrls(JOURNEYMAP_PROPERTY);
        requireJars(urls, "no JourneyMap jar supplied; pass -PjourneymapJar=<path> to run this");

        // Parent-last for journeymap.*, parent-first for everything else: the question is what the
        // *shipped* jar declares, and an ordinary parent-first loader would answer it with the
        // compile-time API already on the test classpath -- the one artifact this test may not
        // consult. Minecraft still has to come from the parent, because half of these signatures
        // name a Minecraft type and getMethods() resolves every one of them.
        try (ShippedApiLoader loader = new ShippedApiLoader(urls.toArray(new URL[0]))) {
            List<String> missing = new ArrayList<>();
            JOURNEYMAP_API.forEach((className, members) -> {
                Class<?> type;
                try {
                    type = Class.forName(className, false, loader);
                } catch (ClassNotFoundException e) {
                    missing.add(className + " (whole class)");
                    return;
                }
                for (String member : members) {
                    int slash = member.indexOf('/');
                    String name = member.substring(0, slash);
                    int arity = Integer.parseInt(member.substring(slash + 1));
                    boolean found = Stream.of(type.getMethods())
                            .anyMatch(m -> m.getName().equals(name) && m.getParameterCount() == arity);
                    if (!found) {
                        missing.add(className + '#' + member);
                    }
                }
            });

            assertEquals(List.of(), missing.stream().sorted().toList(),
                    "the JourneyMap jar's own API no longer declares these, so the compiled plugin "
                            + "would throw NoSuchMethodError in a player's game. Either "
                            + "journeymap_api_version is behind the shipped mod, or the API changed "
                            + "and compat/journeymap must change with it");
        }
    }

    @Test
    @DisplayName("every essential Xaero member the waypoint integration needs resolves against a real jar")
    void xaeroEssentialManifestResolves() throws IOException {
        List<URL> urls = jarUrls(XAERO_PROPERTY);
        requireJars(urls, "no Xaero's Minimap jar supplied; pass -PxaeroJar=<path> to run this");

        probe(urls, XAERO_DISPLAY_NAME, "xaero.", "common.XaeroMinimapSession",
                XaeroWaypoints.ESSENTIAL, List.of(), resolution -> {
                    assertEquals(List.of(), resolution.missing(),
                            "these Xaero members did not bind, so quest waypoints would silently not "
                                    + "appear. Check the Waypoint constructor hint first: the "
                                    + "all-primitive form shares its arity with one taking "
                                    + "WaypointColor and WaypointPurpose, and only the primitive one "
                                    + "can be called without naming a Xaero type");
                    assertTrue(resolution.isBound());
                });
    }

    @Test
    @DisplayName("Xaero's colour and purpose palettes resolve their constants by name")
    void xaeroOptionalPaletteResolves() throws IOException {
        List<URL> urls = jarUrls(XAERO_PROPERTY);
        requireJars(urls, "no Xaero's Minimap jar supplied; pass -PxaeroJar=<path> to run this");

        // Optional by contract: a build without them still gets waypoints, in the palette's first
        // colour. This is what stops that quietly becoming the normal case — and what replaced the
        // hard-coded ordinal table, which matched exactly one Xaero build and said so nowhere.
        probe(urls, XAERO_DISPLAY_NAME, "xaero.", "common.XaeroMinimapSession",
                XaeroWaypoints.ESSENTIAL, XaeroWaypoints.OPTIONAL, resolution -> {
                    assertEquals(List.of(), resolution.missingOptional(),
                            "Xaero's WaypointColor/WaypointPurpose enums moved; every quest waypoint "
                                    + "would fall back to the first colour in the palette");
                    assertTrue(resolution.enumConstant(XaeroWaypoints.PURPOSE_ENUM, "NORMAL")
                            instanceof Enum<?>, "WaypointPurpose.NORMAL did not resolve");
                    for (GuidanceKind kind : GuidanceKind.values()) {
                        String colour = MarkerColours.xaeroColourName(kind);
                        assertTrue(resolution.enumConstant(XaeroWaypoints.COLOUR_ENUM, colour)
                                        instanceof Enum<?>,
                                "WaypointColor." + colour + " did not resolve, so " + kind
                                        + " waypoints would fall back to the first colour");
                    }
                });
    }

    /** A loader that answers for {@code journeymap.*} itself and defers everything else upwards. */
    private static final class ShippedApiLoader extends URLClassLoader {

        private ShippedApiLoader(URL[] urls) {
            super(urls, MapBindingProbeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("journeymap.")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    found = findClass(name);
                }
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    /**
     * Skips when no jar was supplied, unless the caller said one was required.
     *
     * <p>A skip reports green, which is right for a developer who has neither mod on disk and wrong
     * for the release matrix, where a green run that resolved nothing is the failure mode this whole
     * test exists to prevent.
     */
    private static void requireJars(List<URL> urls, String message) {
        if (!urls.isEmpty()) {
            return;
        }
        if (Boolean.parseBoolean(System.getProperty(REQUIRE_PROPERTY, "false"))) {
            fail(message + " (required by -PrequireMapJars=true)");
        }
        Assumptions.abort(message);
    }

    /**
     * Resolves the manifest and hands the result to {@code check} <b>while the loader is still open</b>.
     *
     * <p>A {@code Resolution} outlives nothing: it holds method handles and classes, and anything that
     * asks it to load one more class - {@code enumConstant} does, to read an ordinal off Xaero's
     * palette - gets a {@code ClassNotFoundException} the moment the loader is closed. Returning the
     * resolution and closing on the way out looks correct and fails only on the assertions that matter.
     */
    private static void probe(List<URL> urls, String modName, String root, String probeClass,
                              List<Member> essential, List<Member> optional,
                              java.util.function.Consumer<MapBinding.Resolution> check) {
        // Parent is the test loader, which already carries Minecraft: the manifests hint at BlockPos
        // and ResourceLocation, and two copies of those would never compare equal.
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                MapBindingProbeTest.class.getClassLoader())) {
            check.accept(MapBinding.resolve(modName, root, probeClass, essential, optional,
                    loader));
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
