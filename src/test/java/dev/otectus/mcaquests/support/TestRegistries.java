package dev.otectus.mcaquests.support;

import dev.otectus.mcaquests.state.ServerRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.buffer.Unpooled;

/**
 * Supplies the {@link RegistryAccess} that a {@link RegistryFriendlyByteBuf} needs, and the buffers
 * themselves.
 *
 * <p>1.21 moved every packet payload from {@code RegistryFriendlyByteBuf} to {@code RegistryFriendlyByteBuf},
 * because stream codecs for registry-backed values ({@code ItemStack}, {@code Component}) resolve ids
 * through the connection's registry access. A unit test has no connection, but the built-in registries
 * are exactly the ones a vanilla connection starts from, so wrapping them is a faithful stand-in for
 * everything this suite encodes.
 *
 * <p>{@link TestBootstrap#ensureBootstrapped()} must have run first — {@code BuiltInRegistries.REGISTRY}
 * is only populated after it, and an unbootstrapped access would hand the codecs empty registries.
 */
public final class TestRegistries {

    private static HolderLookup.Provider datapackLookup;

    private TestRegistries() {
    }

    /**
     * The vanilla <em>datapack</em> registries — enchantments above all, which 1.21 moved out of
     * {@code BuiltInRegistries} — and binds them as the lookup {@link ServerRegistries} hands to code
     * that normally asks the running server for one. A unit test has registries but no server.
     */
    public static synchronized HolderLookup.Provider datapackLookup() {
        if (datapackLookup == null) {
            TestBootstrap.ensureBootstrapped();
            datapackLookup = VanillaRegistries.createLookup();
            ServerRegistries.bind(datapackLookup);
        }
        return datapackLookup;
    }

    /** The built-in registries as a frozen {@link RegistryAccess}. */
    public static RegistryAccess access() {
        TestBootstrap.ensureBootstrapped();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** An empty buffer wired to {@link #access()}; the 1.21 replacement for {@code TestRegistries.buffer()}. */
    public static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), access());
    }
}
