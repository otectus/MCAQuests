package dev.otectus.mcaquests;

import com.mojang.logging.LogUtils;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.project.objective.ProjectObjectiveTypes;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.quest.situation.SituationTriggerTypes;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Main entrypoint for MCA: Quests.
 *
 * <p>A server-authoritative, datapack-driven RPG quest system for Minecraft Comes Alive: Reborn
 * villagers. This add-on isolates every MCA Reborn call behind
 * {@code dev.otectus.mcaquests.compat.McaCompat} so that MCA internal API changes only ever
 * require edits in one place.
 */
@Mod(McaQuests.MOD_ID)
public final class McaQuests {

    public static final String MOD_ID = "mcaquests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public McaQuests() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McaQuestsConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, McaQuestsConfig.CLIENT_SPEC);

        // Optional FTB Quests integration (spec §10.4). ordering="AFTER" in mods.toml sorts our
        // constructor after FTB Quests' own, so its built-in TaskTypes/RewardTypes already exist.
        // This fully-qualified call is the ONLY reference to compat.ftbq outside that package
        // (enforced by NoFtbqClassloadTest); no always-loaded class may import from it.
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                dev.otectus.mcaquests.compat.ftbq.FtbqBootstrap.init();
            } catch (Throwable t) {   // binary drift in a future FTBQ build must not kill the game
                McaQuests.LOGGER.error("[MCA: Quests] FTB Quests detected but integration failed to start; "
                        + "it will be disabled. Report this with your FTB Quests version.", t);
            }
        }

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(QuestCapabilities::onRegisterCapabilities);

        LOGGER.info("MCA: Quests initialising (mod id '{}')", MOD_ID);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Force the built-in objective/reward type registries to populate before any datapack parse.
        ObjectiveTypes.bootstrap();
        RewardTypes.bootstrap();
        ConditionTypes.bootstrap();
        ProjectObjectiveTypes.bootstrap();
        SituationTriggerTypes.bootstrap();
        event.enqueueWork(QuestNetwork::register);
        // Choose the reputation backend once every mod has loaded, so ModList is authoritative. This
        // is the only place the optional MCA: Reputation integration is ever switched on; without the
        // call Quests simply uses its own per-player standing store (spec 29.1).
        event.enqueueWork(dev.otectus.mcaquests.compat.ReputationBridge::init);
        // Report which MCA package layout we bound to, once, now that every mod is constructed and the
        // classloader is authoritative. Resolution itself already happened lazily and cannot fail; this
        // only surfaces the outcome so a bug report can say which root matched (spec §35.1).
        event.enqueueWork(dev.otectus.mcaquests.compat.mca.McaBinding::init);
    }
}
