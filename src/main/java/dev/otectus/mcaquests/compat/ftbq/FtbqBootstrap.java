package dev.otectus.mcaquests.compat.ftbq;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.FtbqBridge;

/**
 * Entry point for the optional FTB Quests integration. Called exactly once, from
 * {@code McaQuests}'s constructor, iff {@code ModList.get().isLoaded("ftbquests")} — and wrapped
 * there in a {@code Throwable} guard so a future FTB Quests build that breaks binary
 * compatibility disables the integration instead of crashing the game (spec §10.4).
 *
 * <p>Current scope (task M2.3): the bridge seam plus task-type registration — all ten task types
 * in the §15 table are now registered. Reward-type registration and the event bridge (§10.3, §12)
 * land in later M2.x tasks, at which point the log line below grows again.
 */
public final class FtbqBootstrap {

    private FtbqBootstrap() {
    }

    public static void init() {                       // called iff ModList.get().isLoaded("ftbquests")
        // FtbqRewardTypes.register() / FtbqEventBridge.register() land in later M2.x tasks.
        FtbqTaskTypes.register();
        FtbqBridge.Holder.set(new FtbqBridgeImpl());
        McaQuests.LOGGER.info("[MCA: Quests] FTB Quests integration bridge active ({} task type(s) registered).",
                FtbqTaskTypes.count());
    }
}
