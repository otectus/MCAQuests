package dev.otectus.mcaquests.compat.ftbq;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;

/**
 * Entry point for the optional FTB Quests integration. Called exactly once, from
 * {@code McaQuests}'s constructor, iff {@code ModList.get().isLoaded("ftbquests")} — and wrapped
 * there in a {@code Throwable} guard so a future FTB Quests build that breaks binary
 * compatibility disables the integration instead of crashing the game (spec §10.4).
 *
 * <p>Current scope (task M2.4): the bridge seam, all ten §15 task types, and the full event bridge
 * (§12/§15.0) — {@link FtbqBridge.Holder} is set <em>before</em> {@link FtbqEventBridge#register()}
 * so that if event-bridge registration itself throws partway through (a future FTB Quests binary
 * incompatibility), any listener that already attached to the bus still sees a real bridge instance
 * rather than momentarily reading the {@code Noop} default. Reward-type registration lands in M3.2.
 */
public final class FtbqBootstrap {

    private FtbqBootstrap() {
    }

    public static void init() {                       // called iff ModList.get().isLoaded("ftbquests")
        // FtbqRewardTypes.register() lands in M3.2.
        FtbqTaskTypes.register();
        FtbqBridge.Holder.set(new FtbqBridgeImpl());
        FtbqEventBridge.register();
        McaQuests.LOGGER.info(
                "[MCA: Quests] FTB Quests integration bridge active ({} task type(s) registered, event bridge listening).",
                FtbqTaskTypes.count());
    }
}
