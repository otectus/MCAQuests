package dev.otectus.mcaquests.support;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;

/**
 * Brings up as much of the game as the unit suite needs. Some tests construct real
 * {@code QuestDefinition}s, whose {@code <clinit>} builds DFU codecs reaching
 * {@code BuiltInRegistries} / {@code EntityType} / {@code DataFixers} — all of which assert the game
 * is "bootstrapped" and that {@code SharedConstants} has a detected version.
 *
 * <p>PORT: under 1.20.1 Forge the real {@code Bootstrap.bootStrap()} could not run here — it reached
 * {@code net.minecraftforge.network.NetworkHooks.init()}, which needs an actual running Forge instance
 * and NPEs — so this helper detected the version, flipped {@code Bootstrap.isBootstrapped} by
 * reflection and then entered the {@code Registries}/{@code BuiltInRegistries} class-initialisation
 * cycle from the side that survives it.
 *
 * <p>Neither workaround holds on NeoForge. That Forge failure mode is gone, and ModDevGradle's
 * {@code unitTest} runner boots FML and a server before the first test class loads — so the registries
 * are already populated <em>and frozen</em>, and the old {@code BuiltInRegistries.bootStrap()} call
 * threw {@code "Registry is already frozen"} out of every static initializer in the suite. The real
 * bootstrap is idempotent and orders the cycle itself, so it is simply called; the reflective flip
 * survives only as a fallback for an environment that cannot run it (codec <em>construction</em>, with
 * no en/decoding, needs nothing but the flag).
 *
 * <p><b>Warning:</b> either path is JVM-global and one-way for the whole test worker. If the fallback
 * path was taken, vanilla code in the same worker will believe the game is bootstrapped even though
 * registries were never actually populated; any future test that needs fully frozen/populated
 * registries must not share a test worker with tests using the fallback.
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
            // Real bootstrap unavailable in this environment -- flip the flag only (see javadoc).
            // Announced loudly: the fallback is a strictly weaker environment than the real bootstrap,
            // so a suite that silently starts taking this path is a regression worth seeing rather than
            // a quiet degradation.
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
            // Only on the fallback path: with no real bootstrap the Registries/BuiltInRegistries
            // <clinit> cycle is still unordered, and whichever test class touched a registry first
            // would decide whether the worker ran at all. Entering from BuiltInRegistries is the order
            // that survives -- its ROOT_REGISTRY_NAME is assigned before it asks Registries for
            // anything, while entering from Registries finds every BuiltInRegistries field still null.
            BuiltInRegistries.bootStrap();
        }
        done = true;
    }
}
