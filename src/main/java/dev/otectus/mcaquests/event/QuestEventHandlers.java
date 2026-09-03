package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Decides what counts as <em>talking to</em> an MCA villager, and credits it to both quest and project
 * {@code talk_to_profession} objectives.
 *
 * <p>This class used to carry a Phase 0 debug shortcut that opened the quest menu on sneak-right-click
 * and then {@code setCanceled(true)} on <em>every</em> such interaction with an MCA villager, whatever
 * the player was holding. That swallowed MCA's own sneak actions (the villager editor book, inventory
 * and trading) and any other mod that owns sneak-right-click, and — because a canceled event is not
 * delivered to later listeners — it could also starve the very progress handlers below. It is gone:
 * the injected <b>Quests</b> button is the only entry point, and MCA: Quests now never cancels an
 * entity interaction.
 *
 * <p>A conversation is counted only for a <b>main-hand, empty-handed, non-canceled</b> interaction with
 * an MCA villager, or for an explicit signal from MCA: Conversations via
 * {@link dev.otectus.mcaquests.api.McaQuestsApi#notifyVillagerConversation}. Holding the MCA editor book
 * or another mod's interaction item is therefore not "talking". Both entry points funnel through
 * {@link #creditConversation}, and both quest- and project-side credit dedupe by villager UUID, so a
 * doubled notification (base hook <em>and</em> MCA: Conversations) can never count twice.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class QuestEventHandlers {

    private QuestEventHandlers() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return; // fire once, not per-hand
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            debugReject("invalid player", event.getEntity());
            return;
        }
        if (!McaCompat.isMcaVillager(event.getTarget())) {
            return; // not our business; stay silent so the log isn't spammed by every mob click
        }
        if (event.isCanceled()) {
            // Defensive: Forge already skips listeners for a canceled event unless they opt in, so this
            // is reached only via same-priority ordering. Either way a canceled interaction never counts.
            debugReject("canceled event", event.getTarget());
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            debugReject("held item (" + player.getMainHandItem().getItem() + ") — not a conversation",
                    event.getTarget());
            return;
        }
        creditConversation(player, event.getTarget());
    }

    /**
     * Credits one conversation with {@code villager} to the player's quest and project talk objectives.
     * Idempotent per villager: both sides record the villager's UUID and ignore repeats, so calling this
     * twice for one interaction (base hook plus an MCA: Conversations signal) advances progress once.
     */
    public static void creditConversation(ServerPlayer player, Entity villager) {
        QuestProgressEvents.creditTalk(player, villager);
        ProjectManager.onProjectTalk(player, villager);
    }

    static void debugReject(String reason, Entity target) {
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Villager interaction not counted as a conversation: {} (target={})",
                    reason, target == null ? "null" : target.getUUID());
        }
    }
}
