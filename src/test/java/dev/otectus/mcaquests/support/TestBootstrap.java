package dev.otectus.mcaquests.support;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;

/**
 * Test-only stand-in for vanilla's {@link Bootstrap#bootStrap()}. Some unit tests construct real
 * {@code QuestDefinition}s, whose {@code <clinit>} builds DFU codecs reaching
 * {@code BuiltInRegistries} / {@code EntityType} / {@code DataFixers} — all of which assert the game
 * is "bootstrapped" and that {@code SharedConstants} has a detected version. The real
 * {@code Bootstrap.bootStrap()} cannot run here: in this Forge dev environment it reaches
 * {@code net.minecraftforge.network.NetworkHooks.init()}, which needs an actual running Forge
 * instance and NPEs. Since codec <em>construction</em> (no en/decoding) only needs the flag itself,
 * this helper detects the version and flips {@code Bootstrap.isBootstrapped} via reflection, skipping
 * the heavy method body.
 *
 * <p><b>Warning:</b> the flip is JVM-global and one-way for the whole test worker — after any test
 * calls {@link #ensureBootstrapped()}, vanilla code in the same worker will believe the game is
 * bootstrapped even though registries were never actually populated. Any future test that needs the
 * REAL {@code Bootstrap.bootStrap()} (fully frozen/populated registries) must not share a test worker
 * with tests using this helper.
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
            Field isBootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
            isBootstrapped.setAccessible(true);
            isBootstrapped.set(null, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not flip Bootstrap.isBootstrapped for tests", e);
        }
        done = true;
    }
}
