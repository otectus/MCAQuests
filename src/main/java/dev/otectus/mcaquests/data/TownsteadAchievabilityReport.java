package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Runs the achievability pass once the world is actually up, and again after a reload.
 *
 * <p>It cannot live at the tail of a data loader. The three loaders register themselves on separate
 * {@code AddReloadListenerEvent} handlers with no ordering guarantee between them, and this pass needs
 * quests, projects <em>and</em> situations all present — plus a bound Townstead, which only exists
 * after mod setup. Hanging it off the wrong loader would give an answer that was right most of the
 * time, which for a diagnostic is worse than no answer at all.
 *
 * <p>Silent when everything is reachable, and silent when Townstead is absent. An operator should hear
 * from this exactly when something they installed cannot be finished.
 */
@Mod.EventBusSubscriber(modid = dev.otectus.mcaquests.McaQuests.MOD_ID)
public final class TownsteadAchievabilityReport {

    private TownsteadAchievabilityReport() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        run();
    }

    /** Also called from {@code /mcaquests reload}, which finishes after every loader has swapped. */
    public static void run() {
        List<String> warnings = TownsteadContentValidator.collectWarnings(
                QuestRegistry.all(), ProjectRegistry.all(), SituationRegistry.all());
        TownsteadContentValidator.report(warnings);
    }
}
