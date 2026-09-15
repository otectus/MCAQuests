package dev.otectus.mcaquests.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The running server's {@link HolderLookup.Provider}, for the handful of places 1.21 needs one where
 * 1.20.1 needed nothing (spec section 15).
 *
 * <p>PORT: two things this mod does became registry-bound in 1.21 — saving an {@link
 * net.minecraft.world.item.ItemStack} to NBT ({@code ItemStack.save(Provider)}) and naming an
 * enchantment ({@code Registries.ENCHANTMENT} is a datapack registry now, not {@code
 * BuiltInRegistries.ENCHANTMENT}). Both sit behind call chains that carry no provider — a quest card's
 * {@code describe()}, a player-data {@code save()} — and threading one through every one of them would
 * touch far more than the feature. Both only ever run with a server up, so the server is asked for it.
 *
 * <p>{@link #bind} exists for the unit suite, which has registries but no server.
 */
public final class ServerRegistries {

    @Nullable
    private static volatile HolderLookup.Provider bound;

    private ServerRegistries() {
    }

    /** Overrides the lookup for a context with no running server; the unit suite is the only caller. */
    public static void bind(@Nullable HolderLookup.Provider provider) {
        bound = provider;
    }

    /** The bound lookup, else the running server's, else empty when neither exists yet. */
    public static Optional<HolderLookup.Provider> provider() {
        HolderLookup.Provider override = bound;
        if (override != null) {
            return Optional.of(override);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : Optional.of(server.registryAccess());
    }
}
