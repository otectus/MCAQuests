package dev.otectus.mcaquests.event;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.mca.McaDialogueHookProbe;
import dev.otectus.mcaquests.project.ProjectManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single place a conversation with an MCA villager becomes quest and project credit (1.7.0).
 *
 * <p>Three routes report conversations, and one real conversation can arrive through more than one:
 * <ol>
 *   <li>{@link Source#MCA_DIALOGUE} — MCA opened its dialogue screen for the player, observed on the
 *       server by a hook on MCA's own handler. The authoritative signal whenever it is installed.</li>
 *   <li>{@link Source#API} — an add-on such as MCA: Conversations reporting a conversation it ran,
 *       through {@code McaQuestsApi.notifyVillagerConversation}.</li>
 *   <li>{@link Source#INTERACT_EVENT} — Forge's interaction events, used <em>only</em> when the dialogue
 *       hook could not be applied to the running MCA, and then only for an empty-handed, non-sneaking,
 *       main-hand, non-cancelled click.</li>
 * </ol>
 *
 * <p>Everything is re-validated here, whatever the route: a live MCA villager, in the player's own
 * level, within interaction range. A stale or remote entity reference is refused. The same player and
 * villager reported again within one tick is the same interaction arriving by a second route and is
 * dropped; beyond that, both quest and project credit count <em>distinct</em> villagers by UUID, so
 * talking to one librarian twice is still one librarian.
 */
public final class ConversationCredit {

    public enum Source {
        MCA_DIALOGUE, API, INTERACT_EVENT
    }

    private record Recent(UUID villager, long tick) {
    }

    private static final Map<UUID, Recent> RECENT = new HashMap<>();

    private ConversationCredit() {
    }

    /** Credits one conversation. Returns false when it was refused or already counted this tick. */
    public static boolean credit(ServerPlayer player, Entity villager, Source source) {
        if (!McaCompat.canPlayerInteract(player, villager)) {
            debug(source, "villager not a live MCA villager in reach", villager);
            return false;
        }
        if (!firstReport(player.getUUID(), villager.getUUID(), player.level().getGameTime())) {
            debug(source, "same interaction already reported by another route", villager);
            return false;
        }
        QuestProgressEvents.creditTalk(player, villager);
        ProjectManager.onProjectTalk(player, villager);
        return true;
    }

    /**
     * Records a report and says whether it is the first for this player and villager within a tick —
     * the window in which one click can arrive through several routes. Pure apart from the small map.
     */
    static boolean firstReport(UUID player, UUID villager, long now) {
        Recent last = RECENT.get(player);
        if (last != null && last.villager().equals(villager) && now >= last.tick() && now - last.tick() <= 1) {
            return false;
        }
        if (RECENT.size() > 256) {
            RECENT.clear();
        }
        RECENT.put(player, new Recent(villager, now));
        return true;
    }

    /** True when MCA's own dialogue signal is live, so the interaction-event fallback stands down. */
    public static boolean dialogueHookActive() {
        return McaDialogueHookProbe.applied();
    }

    public static void clearSessionState() {
        RECENT.clear();
    }

    private static void debug(Source source, String reason, Entity villager) {
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] Conversation ({}) not counted: {} (target={})",
                    source, reason, villager.getUUID());
        }
    }
}
