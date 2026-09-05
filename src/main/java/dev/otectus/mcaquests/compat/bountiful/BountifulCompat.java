package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bountiful, as a {@link CompatProvider}: a thin shell that owns <em>which</em>
 * {@link BountifulBridge} is in use and delegates everything else to it.
 *
 * <p>The choice is re-made on every {@link #reprobe}, which is what makes the integration follow the
 * installation rather than the moment the game started. A {@code /reload} after switching
 * {@code compat.bountiful.mode} to {@code DATA_ONLY} takes effect immediately, and a world opened
 * with a different mod set gets the bridge that fits it.
 *
 * <p>{@link #select} is deliberately a pure function of four facts, so the whole decision table can
 * be tested without Forge, a mod file or a running game — the mode that actually ships is the one
 * nobody can observe until a player reports that their bounty quests never advance.
 */
public final class BountifulCompat implements CompatProvider {

    /**
     * Everything that wants to hear about a cash-in, held statically because listeners outlive
     * bridges.
     *
     * <p>A bridge is rebuilt on every re-probe, so a list owned by one is discarded with it -- and a
     * listener registered once at start-up would stop being called after the first reload, silently
     * and with nothing in the log. The listeners belong to the mod; which bridge is in use is a fact
     * about the installation, and the two have different lifetimes.
     */
    private static final List<BountifulCompletionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private volatile BountifulBridge bridge = new NoopBountifulBridge(false);
    private volatile boolean hookByteVerified;

    @Override
    public String id() {
        return BountifulBridge.MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.bountiful.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(BountifulBridge.MOD_ID);
    }

    @Override
    public CompatStatus status() {
        return bridge.status();
    }

    @Override
    public List<CompatCapability> capabilities() {
        return bridge.capabilities();
    }

    /** The bridge currently in use. Never null, and never the same object across a re-probe. */
    public BountifulBridge currentBridge() {
        return bridge;
    }

    /**
     * Test seam: use {@code bridge} instead of whatever a probe would choose. Production only ever
     * sets this through {@link #reprobe}.
     */
    public void setBridgeForTest(BountifulBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * The bridge the registered provider is using, or a permanently-absent one when Bountiful has no
     * provider registered at all. The entry point for everything outside this package.
     */
    public static BountifulBridge bridge(CompatRegistry registry) {
        return registry.provider(BountifulBridge.MOD_ID).orElse(null) instanceof BountifulCompat compat
                ? compat.currentBridge()
                : new NoopBountifulBridge(false);
    }

    /** As {@link #bridge(CompatRegistry)}, against the singleton registry. */
    public static BountifulBridge bridge() {
        return bridge(CompatRegistry.get());
    }

    /**
     * Whether MCA: Quests' own Bountiful quest pack may be mounted.
     *
     * <p>Separate from the mode for the same reason Ice &amp; Fire's is: a server that writes its own
     * bounty-board quests wants the capabilities and the {@code compat_capability} condition but not
     * our content. Never affects what {@link #capabilities()} reports.
     */
    public static boolean builtinContentEnabled() {
        return McaQuestsConfig.COMMON.bountifulEnableBuiltinContent.get();
    }

    /**
     * Whether our Ice &amp; Fire bounty pools and decree are offered to Bountiful's loader.
     *
     * <p>Its own switch rather than a second meaning for {@code enableBuiltinContent}, because the two
     * are different kinds of content: the quests are ours, but the pools become part of what a
     * Bountiful board generates, which is somebody else's economy to balance.
     */
    public static boolean iceAndFirePoolsEnabled() {
        return McaQuestsConfig.COMMON.bountifulEnableIceAndFirePools.get();
    }

    /**
     * Re-decides which bridge is in use.
     *
     * <p>{@code access} is unused: nothing here lives in a dynamic registry. The board is an ordinary
     * block, and the bridge re-asks {@code ForgeRegistries} for it during its own construction.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
        boolean loaded = ModList.get().isLoaded(BountifulBridge.MOD_ID);
        McaQuestsConfig.BountifulMode mode = McaQuestsConfig.COMMON.bountifulMode.get();
        hookByteVerified = loaded && BountifulBinding.tryCashInPresent(bountyDataClass());

        BountifulBinding.Resolution resolution = loaded && mode != McaQuestsConfig.BountifulMode.OFF
                ? BountifulBinding.resolveAgainst(BountifulCompat.class.getClassLoader())
                : BountifulBinding.absent();
        BountifulBridge selected = select(loaded, mode, hookByteVerified,
                BountifulHookProbe.state(), resolution);
        // The hook reports itself once, during mixin application, long before the first probe -- and
        // every re-probe builds a fresh bridge that has never heard of it. Carrying the fact across is
        // what stops the status command from downgrading a working hook to "believed" after a reload.
        if (selected instanceof HookedBountifulBridge hooked
                && BountifulHookProbe.state() == BountifulHookProbe.State.APPLIED) {
            hooked.markHookApplied();
        }
        bridge = selected;
    }

    /**
     * Which bridge fits these five facts. Pure, total, and the whole of the decision.
     *
     * <p>The ordering matters. {@code OFF} beats everything, because a switched-off integration must
     * behave exactly like an absent mod rather than like a degraded one. {@code DATA_ONLY} then beats
     * a working hook, because an owner who asked for no hook has asked for no hook — and finally the
     * hook is used only when the bytes prove the method it needs is there with the right shape.
     *
     * @param loaded            whether Forge reports the mod installed
     * @param mode              {@code compat.bountiful.mode}
     * <p>{@code probeState} is the fifth fact and the only one that can contradict the bytes. The
     * class file saying the method is hookable and the hook actually going in are different claims,
     * and when the plugin reports {@link BountifulHookProbe.State#FAILED} the second one is false: the
     * data-only bridge is then the honest answer, because a hooked bridge would offer
     * bounty-completion quests that nothing could ever advance. {@code SKIPPED} needs no case of its
     * own -- it means the bytes did not match either, so {@code tryCashInPresent} has already said no.
     *
     * @param tryCashInPresent  whether {@code BountyData.tryCashIn} was found with the exact descriptor
     * @param probeState        what the mixin config plugin reported about the hook
     * @param resolution        the resolved manifest, which decides the two read capabilities
     */
    public static BountifulBridge select(boolean loaded, McaQuestsConfig.BountifulMode mode,
                                         boolean tryCashInPresent,
                                         BountifulHookProbe.State probeState,
                                         BountifulBinding.Resolution resolution) {
        if (!loaded) {
            return new NoopBountifulBridge(false);
        }
        if (mode == McaQuestsConfig.BountifulMode.OFF) {
            return new NoopBountifulBridge(true);
        }
        if (mode == McaQuestsConfig.BountifulMode.DATA_ONLY || !tryCashInPresent
                || probeState == BountifulHookProbe.State.FAILED) {
            return DataOnlyBountifulBridge.of(resolution);
        }
        return HookedBountifulBridge.of(resolution);
    }

    /**
     * Registers a listener for successful cash-ins, for the lifetime of the game rather than of a
     * bridge. Reached through {@link BountifulBridge#addCompletionListener}, which every bridge
     * delegates here.
     */
    static void addCompletionListener(BountifulCompletionListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    /**
     * Tells every listener about one completion.
     *
     * <p>A listener that throws is logged and the rest still run. This is called from inside
     * Bountiful's own cash-in, where an exception would not merely lose a quest credit but take the
     * player's bounty down with it.
     */
    static void dispatchCompletion(ServerPlayer player, BountyCompletion completion) {
        for (BountifulCompletionListener listener : LISTENERS) {
            try {
                listener.onBountyCompleted(player, completion);
            } catch (Throwable t) {
                McaQuests.LOGGER.warn("[MCA: Quests] A Bountiful completion listener threw; the "
                        + "cash-in itself is unaffected.", t);
            }
        }
    }

    /** Test seam: forget every registered listener. Production never removes one. */
    static void clearListenersForTest() {
        LISTENERS.clear();
    }

    /**
     * The six lines {@code /mcaquests compat bountiful status} prints.
     *
     * <p>Shaped so each answers one question an owner might actually be asking — "is it installed",
     * "is there a board", "will my pools load", "will bounty quests advance", "does {@code min_rarity}
     * work", "what am I actually running" — rather than dumping the capability table twice.
     */
    @Override
    public List<Component> diagnostics() {
        List<Component> lines = new ArrayList<>();
        boolean installed = bridge.status() != CompatStatus.ABSENT;
        lines.add(Component.translatable("mcaquests.command.compat.bountiful.mod",
                Component.translatable(installed
                        ? "mcaquests.command.compat.bountiful.mod.present"
                        : "mcaquests.command.compat.bountiful.mod.absent")));
        lines.add(line("board", bridge.has(BountifulBridge.Capability.BOARD_REGISTRY)));
        lines.add(line("data_pack", bridge.has(BountifulBridge.Capability.DATA_PACK)));
        lines.add(hookLine());
        lines.add(line("rarity", bridge.has(BountifulBridge.Capability.READ_RARITY)));
        lines.add(Component.translatable("mcaquests.command.compat.bountiful.effective_mode",
                effectiveMode()));
        return List.copyOf(lines);
    }

    /** NOOP, DATA_ONLY or HOOKED — the bridge actually in use, not the configured mode. */
    public String effectiveMode() {
        if (bridge instanceof HookedBountifulBridge) {
            return "HOOKED";
        }
        return bridge instanceof DataOnlyBountifulBridge ? "DATA_ONLY" : "NOOP";
    }

    /** Whether the mod file's bytes said the cash-in hook is possible. Diagnostics and tests only. */
    public boolean hookByteVerified() {
        return hookByteVerified;
    }

    /**
     * The cash-in line, which reports the hook itself rather than the capability.
     *
     * <p>"The bytes allow it" and "it is in place" are the two answers this integration is most likely
     * to confuse for one another, and they are the difference between a quest that will advance and
     * one that never will. So the probe's own state is printed: applied, unavailable with the reason
     * the plugin recorded, or not probed at all -- which is what an installation shows before
     * Bountiful's class has ever been loaded.
     */
    private Component hookLine() {
        BountifulHookProbe.State state = BountifulHookProbe.state();
        Component status;
        if (state == BountifulHookProbe.State.APPLIED) {
            status = Component.translatable("mcaquests.command.compat.bountiful.hook.applied");
        } else if (state == BountifulHookProbe.State.FAILED
                || state == BountifulHookProbe.State.SKIPPED) {
            status = Component.translatable("mcaquests.command.compat.bountiful.hook.failed",
                    BountifulHookProbe.reason());
        } else if (bridge.has(BountifulBridge.Capability.CASH_IN_HOOK)) {
            status = Component.translatable("mcaquests.command.compat.bountiful.hook.not_probed");
        } else {
            status = Component.translatable("mcaquests.command.compat.bountiful.unavailable");
        }
        return Component.translatable("mcaquests.command.compat.bountiful.cash_in", status);
    }

    private static Component line(String what, boolean ok) {
        return Component.translatable("mcaquests.command.compat.bountiful." + what,
                Component.translatable(ok
                        ? "mcaquests.command.compat.bountiful.ok"
                        : "mcaquests.command.compat.bountiful.unavailable"));
    }

    /**
     * {@code BountyData.class} inside the installed Bountiful, or null when it cannot be located.
     *
     * <p>Read out of the mod file rather than off the classpath so the answer is about the jar the
     * player has, and so nothing is loaded to get it. The resource path is assembled from a dotted
     * class name by {@link BountifulBinding#bountyDataResource()}; see that method for why it is not
     * written out in slash form here.
     */
    @Nullable
    private static Path bountyDataClass() {
        try {
            IModFileInfo info = ModList.get().getModFileById(BountifulBridge.MOD_ID);
            if (info == null) {
                return null;
            }
            Path resource = info.getFile().findResource(BountifulBinding.bountyDataResource());
            return resource != null && java.nio.file.Files.isRegularFile(resource) ? resource : null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] Could not read Bountiful's BountyData class; the "
                    + "cash-in hook will not be offered.", t);
            return null;
        }
    }
}
