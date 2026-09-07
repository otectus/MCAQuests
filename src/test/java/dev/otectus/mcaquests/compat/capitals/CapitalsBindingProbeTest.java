package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CompatStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves {@link CapitalsBinding#MANIFEST} against a real MCA Capitals jar.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code NoCapitalsStaticLinkTest} guarantees no class names a Capitals type, which means the
 * compiler cannot tell anyone when Capitals renames or moves something the manifest asks for: a stale
 * member name would surface as a silently dead capability rather than a build error. This is the
 * replacement safety net — it walks the whole manifest against the supplied jar and fails if anything
 * is missing, so a moved data accessor shows up here instead of as a court quest that is never
 * offered.
 *
 * <h2>Why MCA has to be in the loader too</h2>
 *
 * <p>Capitals statically imports MCA throughout, so enumerating a Capitals class's methods resolves
 * MCA parameter types. With MCA absent {@code getMethods()} throws and whole owners read as unbound.
 * That is the correct production behaviour — it is exactly how a mismatched Capitals/MCA pair
 * degrades — but it would make this probe vacuously green, so the loader gets both.
 *
 * <h2>Running it</h2>
 *
 * <pre>./gradlew capitalsProbeTest -PcapitalsJar=/path/mcacapitals-1.3.6.jar</pre>
 *
 * <p>Skipped rather than failed when no jar is supplied, so an ordinary checkout still runs the suite.
 */
class CapitalsBindingProbeTest {

    private static final String CAPITALS_JAR_PROPERTY = "mcaquests.capitals.probe.jar";
    private static final String MCA_JARS_PROPERTY = "mcaquests.probe.jars";

    @Test
    void manifestResolvesAgainstTheRealCapitalsJar() throws Exception {
        List<Path> capitals = jars(CAPITALS_JAR_PROPERTY);
        Assumptions.assumeFalse(capitals.isEmpty(),
                "No Capitals jar supplied (" + CAPITALS_JAR_PROPERTY + "); run "
                        + "`./gradlew capitalsProbeTest -PcapitalsJar=<path>` to exercise this.");

        List<Path> all = new ArrayList<>(capitals);
        all.addAll(jars(MCA_JARS_PROPERTY));

        try (URLClassLoader loader = loaderFor(all)) {
            CapitalsBinding.Resolution resolution = CapitalsBinding.resolveAgainst(loader);

            assertEquals(List.of(), resolution.unresolved(),
                    "Capitals is missing member(s) the manifest asks for. Either Capitals renamed them "
                            + "(update CapitalsBinding's manifest) or removed them (drop the capability "
                            + "and let the bridge answer empty). Jars: " + all);
            assertEquals(CompatStatus.FULL, resolution.status(),
                    "Every declared capability must bind against a supported Capitals.");
            assertEquals(CapitalsBinding.DECLARED_CAPABILITIES, resolution.capabilities());

            // The two enums the bridge names constants of by string. Without the class there is no
            // Enum.valueOf, so a title grant would silently no-op.
            assertNotNull(resolution.type(CapitalsBinding.CLASS_NOBLE_TITLE),
                    "NobleTitle did not load; player title grants would be impossible.");
            assertNotNull(resolution.type(CapitalsBinding.CLASS_DIPLOMATIC_STATE),
                    "CapitalDiplomaticState did not load; the war signal would never be raised.");
            System.out.println("[probe] Capitals bound; capabilities = "
                    + resolution.capabilities().size());
        }
    }

    /**
     * Sanity check on the probe itself: with no Capitals anywhere, resolution must report a clean
     * absence rather than throwing. That is the state the rest of the unit suite runs in, and the
     * state most servers are in — it has to be boring, not fatal.
     */
    @Test
    void resolutionWithoutCapitalsIsAbsentAndDoesNotThrow() throws Exception {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            CapitalsBinding.Resolution resolution = CapitalsBinding.resolveAgainst(empty);

            assertEquals(CompatStatus.ABSENT, resolution.status());
            assertTrue(resolution.capabilities().isEmpty());
            assertTrue(resolution.unresolved().isEmpty(),
                    "An absent Capitals is not a partial binding; nothing should be reported as a miss.");
            assertNotNull(resolution.handle(CapitalsBinding.ALL_CAPITALS));
        }
    }

    @Test
    void missingSaveHookDisablesRecordMutationsButKeepsReadOnlyRegistry() throws Exception {
        List<Path> capitals = jars(CAPITALS_JAR_PROPERTY);
        Assumptions.assumeFalse(capitals.isEmpty(), "No Capitals jar supplied for sabotage probe");
        List<Path> all = new ArrayList<>(capitals);
        all.addAll(jars(MCA_JARS_PROPERTY));
        URL[] urls = all.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }).toArray(URL[]::new);
        try (URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(CapitalsBinding.PACKAGE + "data.CapitalDataAccess")) {
                    throw new ClassNotFoundException("simulated missing save hook");
                }
                return super.loadClass(name, resolve);
            }
        }) {
            CapitalsBinding.Resolution resolution = CapitalsBinding.resolveAgainst(loader);
            assertTrue(resolution.has(CapitalsCapability.REGISTRY));
            assertFalse(resolution.has(CapitalsCapability.CHRONICLE));
            assertFalse(resolution.has(CapitalsCapability.VILLAGER_TITLES));
            assertTrue(resolution.has(CapitalsCapability.TITLE_GRANTS));
            assertEquals(List.of(CapitalsBinding.MARK_DIRTY.toString()), resolution.unresolved());
        }
    }

    private static URLClassLoader loaderFor(List<Path> jars) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (Path jar : jars) {
            urls.add(jar.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new),
                CapitalsBindingProbeTest.class.getClassLoader());
    }

    private static List<Path> jars(String property) {
        List<Path> jars = new ArrayList<>();
        for (String entry : System.getProperty(property, "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                Path path = Paths.get(entry.trim());
                if (Files.isRegularFile(path)) {
                    jars.add(path);
                }
            }
        }
        return jars;
    }
}
