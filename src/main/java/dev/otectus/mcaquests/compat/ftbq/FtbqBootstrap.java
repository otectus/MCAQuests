package dev.otectus.mcaquests.compat.ftbq;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;

/**
 * Entry point for the optional FTB Quests integration. Called exactly once, from
 * {@code McaQuests}'s constructor, iff {@code ModList.get().isLoaded("ftbquests")} — and wrapped
 * there in a {@code Throwable} guard so a future FTB Quests build that breaks binary
 * compatibility disables the integration instead of crashing the game (spec §10.4).
 *
 * <p>Current scope (task M3.2): the bridge seam, all ten §15 task types, all three §16 reward types,
 * and the full event bridge (§12/§15.0). The holder is published after registration succeeds, so any
 * partially attached listeners stay inactive if a future FTB Quests binary incompatibility interrupts
 * initialization.
 */
public final class FtbqBootstrap {

    private FtbqBootstrap() {
    }

    public static void init() {                       // called iff ModList.get().isLoaded("ftbquests")
        FtbqTaskTypes.register();
        FtbqRewardTypes.register();
        FtbqEventBridge.register();
        FtbqBridge.Holder.set(new FtbqBridgeImpl());
        McaQuests.LOGGER.info(
                "[MCA: Quests] FTB Quests integration bridge active ({} task type(s), {} reward type(s) "
                        + "registered, event bridge listening).",
                FtbqTaskTypes.count(), FtbqRewardTypes.count());
    }
}
