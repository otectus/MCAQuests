package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.compat.townstead.TownsteadBinding;
import dev.otectus.mcaquests.support.ClassFileConstants;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves {@link TownsteadBinding#MANIFEST} against a real Townstead jar.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code NoTownsteadStaticLinkTest} guarantees no class names a Townstead type, which means the
 * compiler cannot tell anyone when Townstead renames or removes something the manifest asks for: a
 * stale member name would surface as a silently dead capability rather than a build error. This test
 * is the replacement safety net. It walks the whole manifest against the supplied jar and fails if
 * anything is missing, so a moved method shows up here instead of as a quest that never completes.
 *
 * <h2>Why MCA has to be in the loader too</h2>
 *
 * <p>Townstead is compiled against MCA, so {@code TownsteadAPI} declares
 * {@code villager(VillagerEntityMCA)} beside the vanilla-descriptor {@code entity(Entity)} we
 * actually bind. Enumerating a class's methods resolves every parameter type, so with MCA absent
 * {@code getMethods()} throws and the whole class reads as unbound. That is the correct production
 * behaviour — it is exactly how a mismatched Townstead/MCA pair degrades — but it would make this
 * probe vacuously green, so the loader gets both jars and the assertions below would catch it.
 *
 * <h2>Running it</h2>
 *
 * <pre>./gradlew townsteadProbeTest -PtownsteadLegacyJar=/path/townstead-0.7.6+1.20.1.jar</pre>
 *
 * <p>Both {@code -PtownsteadModernJar} and {@code -PtownsteadLegacyJar} are accepted and either may
 * be given alone. Skipped rather than failed when neither is supplied, so an ordinary checkout still
 * runs the suite.
 */
class TownsteadBindingProbeTest {

    private static final String TOWNSTEAD_JARS_PROPERTY = "mcaquests.townstead.probe.jars";
    private static final String MCA_JARS_PROPERTY = "mcaquests.probe.jars";

    private static final String API_CLASS = "com.aetherianartificer.townstead.api.TownsteadAPI";
    private static final String FATIGUE_DATA_CLASS = "com.aetherianartificer.townstead.fatigue.FatigueData";
    private static final String HUNGER_DATA_CLASS = "com.aetherianartificer.townstead.hunger.HungerData";
    private static final String THIRST_DATA_CLASS = "com.aetherianartificer.townstead.thirst.ThirstData";

    @Test
    void manifestResolvesAgainstTheRealTownsteadJar() throws Exception {
        List<Path> townstead = jars(TOWNSTEAD_JARS_PROPERTY);
        Assumptions.assumeFalse(townstead.isEmpty(),
                "No Townstead jar supplied (" + TOWNSTEAD_JARS_PROPERTY + "); run "
                        + "`./gradlew townsteadProbeTest -PtownsteadLegacyJar=<path>` to exercise this.");

        List<Path> all = new ArrayList<>(townstead);
        all.addAll(jars(MCA_JARS_PROPERTY));

        try (URLClassLoader loader = loaderFor(all)) {
            TownsteadBinding.Resolution resolution = TownsteadBinding.resolveAgainst(loader);

            assertEquals(List.of(), resolution.unresolved(),
                    "Townstead is missing member(s) the manifest asks for. Either Townstead renamed "
                            + "them (update TownsteadBinding's manifest) or removed them (drop the "
                            + "capability and give TownsteadHandles a fallback). Jars: " + all);
            assertEquals(TownsteadStatus.FULL, resolution.status(),
                    "Every declared capability must bind against a supported Townstead.");
            assertEquals(TownsteadBinding.DECLARED_CAPABILITIES, resolution.capabilities());

            assertNotNull(resolution.variant(),
                    "The MCA package root Townstead was built against could not be read. Diagnostics "
                            + "only, but if TownsteadAPI#villager has gone the variant probe needs a new "
                            + "method to read.");
            System.out.println("[probe] Townstead bound; MCA root = " + resolution.variant()
                    + ", capabilities = " + resolution.capabilities().size());
        }
    }

    /**
     * {@code TownsteadAPI#entity} is the one entry point whose parameter descriptor is vanilla-only,
     * and the entire read facade rests on that. If a future Townstead changed it to take an MCA type,
     * binding would still "work" and then fail at every call — so assert the shape, not just presence.
     */
    @Test
    void theEntryPointTakesAVanillaEntity() throws Exception {
        List<Path> townstead = jars(TOWNSTEAD_JARS_PROPERTY);
        Assumptions.assumeFalse(townstead.isEmpty(), "No Townstead jar supplied.");

        List<Path> all = new ArrayList<>(townstead);
        all.addAll(jars(MCA_JARS_PROPERTY));

        try (URLClassLoader loader = loaderFor(all)) {
            Class<?> api = Class.forName(API_CLASS, false, loader);
            Method entry = null;
            for (Method candidate : api.getMethods()) {
                if (candidate.getName().equals("entity") && candidate.getParameterCount() == 1) {
                    entry = candidate;
                    break;
                }
            }
            assertNotNull(entry, "TownsteadAPI#entity(Entity) is gone; the read facade has no safe entry.");
            assertEquals("net.minecraft.world.entity.Entity", entry.getParameterTypes()[0].getName(),
                    "TownsteadAPI#entity no longer takes a vanilla Entity. Binding it would drag a "
                            + "relocated MCA type into every villager read.");
        }
    }

    /**
     * Townstead owns the real ranges, so they are pinned here rather than trusted to a comment. They are
     * not all the same -- hunger runs to 100 while thirst and fatigue run to 20 -- so a widened range
     * would not fail anything loudly, it would quietly clamp rewards short and drift every threshold in
     * the bundled content.
     *
     * <p>Read out of the class file rather than through reflection, because reflection cannot get at
     * it. {@code Field#getInt} forces the declaring class to initialise, and Townstead's published jar
     * references Minecraft members by their SRG names ({@code f_256913_}) while a dev-mapped test JVM
     * has the official ones — so initialising any Townstead class that touches Minecraft state throws
     * {@code NoSuchFieldError}. The manifest probe above is untouched by that mismatch because it only
     * ever resolves <em>class</em> names, which are identical under both mappings.
     */
    @Test
    void theNeedRangesMatchTownstead() throws Exception {
        List<Path> townstead = jars(TOWNSTEAD_JARS_PROPERTY);
        Assumptions.assumeFalse(townstead.isEmpty(), "No Townstead jar supplied.");

        assertRange(townstead, FATIGUE_DATA_CLASS, "MAX_FATIGUE", TownsteadNeedsView.FATIGUE_MAX);
        assertRange(townstead, HUNGER_DATA_CLASS, "MAX_HUNGER", TownsteadNeedsView.HUNGER_MAX);
        assertRange(townstead, THIRST_DATA_CLASS, "MAX_THIRST", TownsteadNeedsView.THIRST_MAX);
        assertRange(townstead, THIRST_DATA_CLASS, "MAX_QUENCHED", TownsteadNeedsView.QUENCHED_MAX);
    }

    private static void assertRange(List<Path> jars, String owner, String field, int expected)
            throws Exception {
        Integer declared = null;
        for (Path jar : jars) {
            declared = ClassFileConstants.staticFinalInt(jar, owner, field);
            if (declared != null) {
                break;
            }
        }
        assertNotNull(declared, "Townstead no longer declares " + owner + "." + field
                + " as a constant, so the range behind the needs rewards can no longer be verified.");
        assertEquals(expected, declared.intValue(), owner + "." + field + " has moved. Update "
                + "TownsteadNeedsView, then re-check every bundled definition and reward that uses a "
                + "threshold on that need -- they are all written against the old range.");
    }

    /**
     * Sanity check on the probe itself: with no Townstead anywhere, resolution must report a clean
     * absence rather than throwing. That is the state the rest of the unit suite runs in, and the
     * state most servers are in — it has to be boring, not fatal.
     */
    @Test
    void resolutionWithoutTownsteadIsAbsentAndDoesNotThrow() throws Exception {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            TownsteadBinding.Resolution resolution = TownsteadBinding.resolveAgainst(empty);

            assertEquals(TownsteadStatus.ABSENT, resolution.status());
            assertTrue(resolution.capabilities().isEmpty());
            assertTrue(resolution.unresolved().isEmpty(),
                    "An absent Townstead is not a partial binding; nothing should be reported as a miss.");
            // Every handle must still be a usable stub: TownsteadHandles invokes them with no null check.
            assertNotNull(resolution.handle(TownsteadBinding.API_ENTITY));
        }
    }

    private static URLClassLoader loaderFor(List<Path> jars) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (Path jar : jars) {
            urls.add(jar.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new),
                TownsteadBindingProbeTest.class.getClassLoader());
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
