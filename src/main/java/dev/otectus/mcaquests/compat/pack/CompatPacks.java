package dev.otectus.mcaquests.compat.pack;

import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.bountiful.BountifulBridge;
import dev.otectus.mcaquests.compat.bountiful.BountifulCompat;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireCapabilities;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireCompat;
import dev.otectus.mcaquests.compat.iceandfire.IceAndFireRegistryManifest;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Every conditional datapack this mod carries, and what has to be true for each one.
 *
 * <p>Kept as data rather than as branches inside the pack finder so the requirements can be asserted
 * in a unit test without a pack repository, a mod file or a server — which matters, because "is this
 * pack mounted?" is only observable in game long after the decision was made.
 */
public final class CompatPacks {

    /**
     * Test seam for {@code compat.iceandfire.enableBuiltinContent}.
     *
     * <p>A unit test can attach the common config spec and read its defaults, but it cannot set a
     * value, so the "installed but switched off" case is otherwise unreachable. Production never
     * replaces this.
     */
    private static volatile BooleanSupplier iceAndFireBuiltinContent =
            CompatPacks::configuredIceAndFireBuiltinContent;

    /** The same seam for {@code compat.bountiful.enableBuiltinContent}. */
    private static volatile BooleanSupplier bountifulBuiltinContent =
            BountifulCompat::builtinContentEnabled;

    /** The same seam for {@code compat.bountiful.enableIceAndFirePools}. */
    private static volatile BooleanSupplier bountifulIceAndFirePools =
            BountifulCompat::iceAndFirePoolsEnabled;

    /**
     * Ice &amp; Fire quest content: mounted when the mod is installed with at least one dragon
     * registered, and the owner has not turned our content off.
     *
     * <p>Both halves are needed. The capability answers "is there anything for these quests to be
     * about?", and {@code compat.iceandfire.enableBuiltinContent} answers "does this server want our
     * take on it?" — a server writing its own dragon quests wants the second answer to be no while
     * the first stays yes.
     */
    public static final ConditionalCompatPack ICEANDFIRE_QUESTS = new ConditionalCompatPack(
            "iafce_quests", "iafce_quests",
            registry -> registry.has(IceAndFireRegistryManifest.MOD_ID, IceAndFireCapabilities.CORE)
                    && iceAndFireBuiltinContent.getAsBoolean());

    /**
     * Bountiful quest content: mounted when Bountiful is installed with its board registered, and the
     * owner has not turned our content off.
     *
     * <p>The board is the right thing to require rather than merely "Bountiful is loaded". Every quest
     * in this pack is ultimately about a board — finding one, or cashing a bounty in at one — so an
     * installation where the block did not register has nothing for them to be about, and offering
     * them would send players looking for something that is not there.
     */
    public static final ConditionalCompatPack BOUNTIFUL_CORE = new ConditionalCompatPack(
            "bountiful_core", "bountiful_core",
            registry -> registry.has(BountifulBridge.MOD_ID, BountifulBridge.Capability.BOARD_REGISTRY.id())
                    && bountifulBuiltinContent.getAsBoolean());

    /**
     * Bounty pools and a decree describing Ice &amp; Fire hunts, for Bountiful's own generator.
     *
     * <p>Four conditions, because this is the one pack whose content is read by <em>another mod's</em>
     * loader: it names Ice &amp; Fire entities and items in files Bountiful will happily merge into its
     * economy. Mounting it without Ice &amp; Fire would put ids into somebody else's generator that
     * resolve to nothing, and this mod does not get to assume how gracefully that fails.
     */
    public static final ConditionalCompatPack BOUNTIFUL_ICEANDFIRE = new ConditionalCompatPack(
            "bountiful_iafce", "bountiful_iafce",
            registry -> registry.has(BountifulBridge.MOD_ID, BountifulBridge.Capability.BOARD_REGISTRY.id())
                    && bountifulBuiltinContent.getAsBoolean()
                    && registry.has(IceAndFireRegistryManifest.MOD_ID, IceAndFireCapabilities.CORE)
                    && bountifulIceAndFirePools.getAsBoolean());

    /** In declaration order, which is the order they are offered to the repository. */
    private static final List<ConditionalCompatPack> ALL = List.of(
            ICEANDFIRE_QUESTS,
            BOUNTIFUL_CORE,
            BOUNTIFUL_ICEANDFIRE);

    private CompatPacks() {
    }

    public static List<ConditionalCompatPack> all() {
        return ALL;
    }

    /** Overrides the built-in-content switch for one test; pass {@code null} to restore the config. */
    public static void setIceAndFireBuiltinContentForTest(BooleanSupplier override) {
        iceAndFireBuiltinContent = override == null
                ? CompatPacks::configuredIceAndFireBuiltinContent
                : override;
    }

    /** Overrides {@code compat.bountiful.enableBuiltinContent} for one test; {@code null} restores it. */
    public static void setBountifulBuiltinContentForTest(BooleanSupplier override) {
        bountifulBuiltinContent = override == null ? BountifulCompat::builtinContentEnabled : override;
    }

    /** Overrides {@code compat.bountiful.enableIceAndFirePools} for one test; {@code null} restores it. */
    public static void setBountifulIceAndFirePoolsForTest(BooleanSupplier override) {
        bountifulIceAndFirePools = override == null ? BountifulCompat::iceAndFirePoolsEnabled : override;
    }

    /**
     * Asks the registered Ice &amp; Fire provider rather than the config directly: the provider is
     * what owns the meaning of the switch, and a registry with no such provider has nothing to mount
     * content for anyway.
     */
    private static boolean configuredIceAndFireBuiltinContent() {
        CompatProvider provider = CompatRegistry.get()
                .provider(IceAndFireRegistryManifest.MOD_ID)
                .orElse(null);
        return provider instanceof IceAndFireCompat iceAndFire && iceAndFire.builtinContentEnabled();
    }
}
