package dev.otectus.mcaquests.support;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;
import net.neoforged.fml.config.IConfigSpec;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

/**
 * Backs the common config spec with its own declared defaults, so tests can call code that reads a
 * config value.
 *
 * <p>NeoForge refuses to answer {@code ConfigValue#get()} before a config file has been attached, and
 * in the development environment it throws rather than returning the default. That is right for a running
 * game — a mod reading config during registration would get answers that change once the file loads —
 * but it means the reload-time validators, which read {@code strictJsonValidation} and
 * {@code maxHeartsReward}, could not be exercised in a unit test at all. Since a validator's job is to
 * catch a broken bundled definition <em>before</em> it ships, that was the wrong thing to be unable to
 * test.
 *
 * <p>Attaching an in-memory config and letting the spec correct it fills in every declared default, so
 * what the tests see is exactly what a player gets on a fresh install.
 */
public final class TestConfig {

    private static volatile boolean loaded;

    private TestConfig() {
    }

    /** Idempotent; safe to call from any number of tests' static initializers. */
    public static synchronized void ensureCommonLoaded() {
        if (loaded) {
            return;
        }
        CommentedConfig config = CommentedConfig.inMemory();
        McaQuestsConfig.COMMON_SPEC.correct(config);
        McaQuestsConfig.COMMON_SPEC.acceptConfig(loadedConfig(config));
        loaded = true;
    }

    /**
     * Wraps a bare config in the {@code ILoadedConfig} that {@code acceptConfig} takes.
     *
     * <p>PORT: 1.20.1 had {@code ModConfigSpec#setConfig(CommentedConfig)}. Its NeoForge replacement
     * takes {@code IConfigSpec.ILoadedConfig}, which pairs the config with the callback that writes it
     * back to disk — and that interface is {@code sealed}, permitting only the package-private record
     * {@code net.neoforged.fml.config.LoadedConfig}. Neither an anonymous class nor a {@code Proxy} can
     * implement it, so the record is constructed reflectively with no file and no {@code ModConfig}
     * behind it. Nothing reaches either: {@code acceptConfig} only calls {@code save()} when the config
     * fails {@code isCorrect}, and it was just corrected.
     */
    private static IConfigSpec.ILoadedConfig loadedConfig(CommentedConfig config) {
        try {
            Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> ctor = loadedConfigClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (IConfigSpec.ILoadedConfig) ctor.newInstance(config, (Path) null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not wrap a test config as ILoadedConfig", e);
        }
    }
}
