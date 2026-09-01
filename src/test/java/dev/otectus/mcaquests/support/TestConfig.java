package dev.otectus.mcaquests.support;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;

/**
 * Backs the common config spec with its own declared defaults, so tests can call code that reads a
 * config value.
 *
 * <p>Forge refuses to answer {@code ConfigValue#get()} before a config file has been attached, and in
 * the development environment it throws rather than returning the default. That is right for a running
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
        McaQuestsConfig.COMMON_SPEC.setConfig(config);
        loaded = true;
    }
}
