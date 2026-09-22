package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.quest.situation.CapitalsSituationDetector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Decides what counts as <em>talking to</em> an MCA villager, and credits it to both quest and project
 * {@code talk_to_profession} objectives through {@link ConversationCredit}.
 *
 * <p><b>Since 1.7.0 the authoritative signal is MCA's own</b>: a hook on MCA's interaction handler
 * reports when MCA opens its dialogue for a player ({@code McaDialogueHookEvents}). Until then this class
 * credited Forge's {@code EntityInteract} event, which an ordinary MCA conversation never produces: MCA
 * 7.6.x opens its dialogue from {@code interactAt} and answers {@code SUCCESS}, so the client never sends
 * the second packet that event is fired for. Talk objectives therefore counted nothing on a plain MCA
 * install — only MCA: Conversations' API report reached them — while sneak-clicks, which open MCA's
 * trade screen, were counted as conversations.
 *
 * <p>The listeners below remain as a <b>fallback</b> for an MCA build whose handler the hook cannot
 * apply to. They then listen to both {@code EntityInteractSpecific} (the {@code interactAt} packet) and
 * {@code EntityInteract}, and count only a main-hand, empty-handed, non-sneaking, non-cancelled click on
 * an MCA villager; {@link ConversationCredit} drops the second of the two for the same click. When the
 * hook is live they stand down entirely, so no click is ever counted twice or counted without MCA
 * having actually opened a conversation.
 *
 * <p>MCA: Quests never cancels an entity interaction; MCA's own handling of every click is unchanged.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestEventHandlers {

    private QuestEventHandlers() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        fallbackConversation(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        fallbackConversation(event, event.getTarget());
    }

    private static void fallbackConversation(PlayerInteractEvent event, Entity target) {
        if (event.getLevel().isClientSide() || ConversationCredit.dialogueHookActive()) {
            return; // MCA's own dialogue signal is authoritative whenever it is installed
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return; // fire once, not per-hand
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            debugReject("invalid player", target);
            return;
        }
        if (!McaCompat.isMcaVillager(target)) {
            return; // not our business; stay silent so the log isn't spammed by every mob click
        }
        if (event.isCanceled()) {
            debugReject("canceled event", target);
            return;
        }
        if (player.isShiftKeyDown()) {
            debugReject("sneaking — MCA opens trading, not a conversation", target);
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            debugReject("held item (" + player.getMainHandItem().getItem() + ") — without MCA's dialogue "
                    + "signal a held-item click cannot be proven to be a conversation", target);
            return;
        }
        ConversationCredit.credit(player, target, ConversationCredit.Source.INTERACT_EVENT);
    }

    /**
     * Credits one conversation with {@code villager}, as reported by an add-on through
     * {@code McaQuestsApi.notifyVillagerConversation}. Validated and de-duplicated like every other
     * route; both quest and project credit count distinct villagers, so reporting a conversation MCA's
     * own signal also saw advances progress once.
     */
    public static void creditConversation(ServerPlayer player, Entity villager) {
        ConversationCredit.credit(player, villager, ConversationCredit.Source.API);
    }

    /**
     * Polls MCA Capitals for court changes worth a situation (1.6.0).
     *
     * <p>Throttled by {@code compat.capitals.pollIntervalTicks} rather than run every tick: a court
     * changes hands a handful of times a world, and the poll walks every capital. {@code Phase.END} so
     * the reading is of a tick that has finished — Capitals settles a succession during the tick that
     * killed the sovereign, and asking at the start of one would see the throne mid-handover.
     */
    @SubscribeEvent
    public static void onServerTickPollCapitals(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int interval = McaQuestsConfig.COMMON.capitalsPollIntervalTicks.get();
        if (server == null || interval <= 0 || server.getTickCount() % interval != 0) {
            return;
        }
        CapitalsSituationDetector.poll(server);
    }

    static void debugReject(String reason, Entity target) {
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Villager interaction not counted as a conversation: {} (target={})",
                    reason, target == null ? "null" : target.getUUID());
        }
    }
}
