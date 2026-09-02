package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.network.QuestTrackC2SPacket;
import dev.otectus.mcaquests.quest.QuestLogEntry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Opens the Quest Log when the keybind is pressed (spec section 21). */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestClientInput {

    private QuestClientInput() {
    }

    /** Drops the outlines and the marker so neither can survive into the next world we join. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHighlightData.clear();
        ClientGuidanceData.clear();
    }

    /**
     * Moves the marker to the next quest in the log, wrapping round to nothing at the end.
     *
     * <p>The empty step is deliberate rather than an off-by-one: following nothing is a state a player
     * wants — it is how you turn the beacon off without opening a screen or editing a config — so the
     * cycle is "first quest, second quest, ..., none, first quest".
     *
     * <p>The client only proposes; {@code QuestManager.track} looks the quest up in the player's own
     * state and ignores anything they do not actually hold.
     */
    private static void cycleTracked() {
        List<QuestLogEntry> entries = ClientQuestData.active();
        if (entries.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).tracked()) {
                current = i;
                break;
            }
        }
        int next = current + 1;
        QuestNetwork.CHANNEL.sendToServer(next >= entries.size()
                ? QuestTrackC2SPacket.none()
                : QuestTrackC2SPacket.of(entries.get(next).villagerUuid(), entries.get(next).questId()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (QuestClientSetup.OPEN_LOG.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new QuestLogScreen());
            }
        }
        while (QuestClientSetup.TOGGLE_HUD.consumeClick()) {
            ClientQuestData.toggleHud();
        }
        while (QuestClientSetup.CYCLE_TRACKED.consumeClick()) {
            cycleTracked();
        }
        while (QuestClientSetup.OPEN_JOURNAL.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new JournalScreen());
            }
        }
    }
}
