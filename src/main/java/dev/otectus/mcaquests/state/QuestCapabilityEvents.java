package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Forge-bus capability lifecycle: attach {@link PlayerQuestData} to players, and copy it across
 * death/dimension respawns (spec section 16).
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestCapabilityEvents {

    private QuestCapabilityEvents() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerQuestDataProvider provider = new PlayerQuestDataProvider();
            event.addCapability(QuestCapabilities.ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // The original player's caps are invalidated on death; revive to read, then re-invalidate.
        event.getOriginal().reviveCaps();
        Optional<PlayerQuestData> old = QuestCapabilities.get(event.getOriginal());
        if (old.isEmpty()) {
            // Never expected: a silent miss here is what quietly wiped every player's quest data
            // from 1.0.0 to 1.4.3, so say so rather than respawning them with an empty capability.
            McaQuests.LOGGER.warn("[MCA: Quests] No quest data to copy from the old player entity for {}; "
                    + "their quests, history and titles would be lost.", event.getEntity().getGameProfile().getName());
        }
        old.ifPresent(data -> QuestCapabilities.get(event.getEntity()).ifPresent(fresh -> fresh.copyFrom(data)));
        event.getOriginal().invalidateCaps();
    }
}
