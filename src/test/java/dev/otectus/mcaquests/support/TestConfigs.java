package dev.otectus.mcaquests.support;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Loads a config spec's declared defaults into it from an in-memory night-config, so
 * {@code SPEC_VALUE.get()} returns the default instead of throwing "config not loaded".
 *
 * <p>PORT: NeoForge's {@code ModConfigSpec.acceptConfig} takes an {@link IConfigSpec.ILoadedConfig}
 * instead of Forge's bare {@code CommentedConfig} — and that interface is sealed, permitting only
 * FML's package-private {@code LoadedConfig} record, so tests must construct it reflectively (with
 * a null path/modConfig; {@code save()} is never called on it). JVM-global like the vanilla
 * bootstrap flip, and equally harmless: tests using it only read defaults (or set-and-restore
 * around each test).
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static void loadDefaults(ModConfigSpec spec) {
        CommentedConfig config = CommentedConfig.inMemory();
        spec.correct(config);
        try {
            Class<?> loadedConfig = Class.forName("net.neoforged.fml.config.LoadedConfig");
            java.lang.reflect.Constructor<?> ctor = loadedConfig.getDeclaredConstructor(
                    CommentedConfig.class, java.nio.file.Path.class, net.neoforged.fml.config.ModConfig.class);
            ctor.setAccessible(true);
            spec.acceptConfig((IConfigSpec.ILoadedConfig) ctor.newInstance(config, null, null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not load config defaults for tests "
                    + "(FML LoadedConfig shape changed?)", e);
        }
    }
}
