package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
        QuestCapabilities.get(event.getOriginal()).ifPresent(old ->
                QuestCapabilities.get(event.getEntity()).ifPresent(fresh -> fresh.copyFrom(old)));
        event.getOriginal().invalidateCaps();
    }
}
