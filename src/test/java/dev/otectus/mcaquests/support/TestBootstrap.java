package dev.otectus.mcaquests.support;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;

/**
 * Test-only stand-in for vanilla's {@link Bootstrap#bootStrap()}. Some unit tests construct real
 * {@code QuestDefinition}s, whose {@code <clinit>} builds DFU codecs reaching
 * {@code BuiltInRegistries} / {@code EntityType} / {@code DataFixers} — all of which assert the game
 * is "bootstrapped" and that {@code SharedConstants} has a detected version.
 *
 * <p>PORT: under 1.20.1 Forge the real {@code Bootstrap.bootStrap()} could not run here (it reached
 * {@code NetworkHooks.init()}, which NPEs outside a running Forge instance), so this helper flipped
 * {@code Bootstrap.isBootstrapped} reflectively. That Forge failure mode is gone on NeoForge, and
 * ModDevGradle's {@code unitTest} support puts a properly configured game on the test classpath — so
 * we now try the real bootstrap first and keep the reflective flip only as a fallback (codec
 * <em>construction</em>, with no en/decoding, only needs the flag itself).
 *
 * <p><b>Warning:</b> either path is JVM-global and one-way for the whole test worker. If the
 * fallback path was taken, vanilla code in the same worker will believe the game is bootstrapped
 * even though registries were never actually populated; any future test that needs fully
 * frozen/populated registries must not share a test worker with tests using the fallback.
 */
public final class TestBootstrap {

    private static volatile boolean done;

    private TestBootstrap() {
    }

    /** Idempotent; safe to call from any number of tests' static initializers. */
    public static synchronized void ensureBootstrapped() {
        if (done) {
            return;
        }
        SharedConstants.tryDetectVersion(); // needed by DataFixers.<clinit>, reached via EntityType/Items
        try {
            Bootstrap.bootStrap();
        } catch (Throwable t) {
            // Real bootstrap unavailable in this environment — flip the flag only (see javadoc).
            // Announced loudly: the fallback is a strictly weaker environment than the real
            // bootstrap, so a suite that silently starts taking this path is a regression worth
            // seeing rather than a quiet degradation.
            System.err.println("[TestBootstrap] Bootstrap.bootStrap() failed (" + t + "); "
                    + "falling back to flipping Bootstrap.isBootstrapped reflectively. "
                    + "Registries are NOT populated in this worker.");
            try {
                Field isBootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
                isBootstrapped.setAccessible(true);
                isBootstrapped.set(null, true);
            } catch (ReflectiveOperationException e) {
                e.addSuppressed(t);
                throw new IllegalStateException("Could not bootstrap Minecraft for tests", e);
            }
        }
        done = true;
    }
}
