package dev.otectus.mcaquests;

import com.mojang.logging.LogUtils;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
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
        event.enqueueWork(QuestNetwork::register);
    }
}
