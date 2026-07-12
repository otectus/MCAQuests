package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.server.level.ServerPlayer;

/**
 * The real {@link FtbqBridge} implementation, backed by FTB Quests' server book
 * ({@code ServerQuestFile.INSTANCE}). Lives entirely under {@code compat.ftbq} so that it (and
 * every FTB import it needs) only ever classloads when FTB Quests is present and
 * {@link FtbqBootstrap#init()} has run — see {@code McaQuests}'s guarded constructor call (spec
 * §10.4) and {@code NoFtbqClassloadTest}.
 *
 * <p>Every public method wraps its body in a {@code Throwable} guard and returns the documented
 * safe default on failure, mirroring {@code McaCompat}'s contract — a future FTB Quests build
 * that breaks binary compatibility degrades a single call, not the server.
 *
 * <p><b>Server thread only.</b> {@code ServerQuestFile.INSTANCE}, team data, and
 * {@code withPlayerContext} are not thread-safe; every caller (Forge event handlers, MCA: Quests'
 * own funnels) already runs on the server thread.
 */
public final class FtbqBridgeImpl implements FtbqBridge {

    @Override
    public boolean isAvailable() {
        try {
            return McaQuestsConfig.COMMON.enableFtbQuestsIntegration.get() && ServerQuestFile.INSTANCE != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "isAvailable", t);
            return false;
        }
    }

    @Override
    public boolean isQuestCompleted(ServerPlayer player, String hexId) {
        try {
            return isCompleted(player, hexId, Quest.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "isQuestCompleted", t);
            return false;
        }
    }

    @Override
    public boolean isChapterCompleted(ServerPlayer player, String hexId) {
        try {
            return isCompleted(player, hexId, Chapter.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "isChapterCompleted", t);
            return false;
        }
    }

    @Override
    public boolean isTaskCompleted(ServerPlayer player, String hexId) {
        try {
            return isCompleted(player, hexId, Task.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "isTaskCompleted", t);
            return false;
        }
    }

    @Override
    public boolean questIdExists(String hexId) {
        try {
            return resolve(hexId, Quest.class) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "questIdExists", t);
            return false;
        }
    }

    @Override
    public boolean chapterIdExists(String hexId) {
        try {
            return resolve(hexId, Chapter.class) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "chapterIdExists", t);
            return false;
        }
    }

    @Override
    public boolean taskIdExists(String hexId) {
        try {
            return resolve(hexId, Task.class) != null;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "taskIdExists", t);
            return false;
        }
    }

    @Override
    public boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId) {
        try {
            if (!isAvailable() || !McaQuestsConfig.COMMON.allowFtbqProgressRewards.get()) {
                return false;
            }
            if (action == ProgressAction.COMPLETE_TASK || action == ProgressAction.RESET_TASK) {
                Task task = resolve(hexId, Task.class);
                if (task == null) {
                    return false;
                }
                return applyTaskProgress(player, task, action);
            } else {
                Quest quest = resolve(hexId, Quest.class);
                if (quest == null) {
                    return false;
                }
                boolean[] anyApplied = {false};
                for (Task task : quest.getTasks()) {
                    anyApplied[0] |= applyTaskProgress(player, task, action);
                }
                return anyApplied[0];
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "grantProgress", t);
            return false;
        }
    }

    /** {@inheritDoc} STUB — full recompute-from-state re-evaluation lands in task M2.4 (§15). */
    @Override
    public void recheckAll(ServerPlayer player) {
        try {
            // Intentionally no-op until M2.4 wires the ten Mca*Task classes' checkStages idiom.
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "recheckAll", t);
        }
    }

    /** {@inheritDoc} STUB — real counts land once FtbqTaskTypes/FtbqRewardTypes exist (M2.4). */
    @Override
    public int[] integrationObjectCounts() {
        try {
            return new int[]{0, 0};
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "integrationObjectCounts", t);
            return new int[]{0, 0};
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Applies one task's progress change under {@code withPlayerContext}. False if locked/no team data. */
    private static boolean applyTaskProgress(ServerPlayer player, Task task, ProgressAction action) {
        TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        if (data == null || data.isLocked()) {
            return false;
        }
        boolean[] applied = {false};
        ServerQuestFile.INSTANCE.withPlayerContext(player, () -> {
            switch (action) {
                case COMPLETE_TASK, COMPLETE_QUEST -> data.setProgress(task, task.getMaxProgress());
                case RESET_TASK -> data.resetProgress(task);
            }
            applied[0] = true;
        });
        return applied[0];
    }

    /** True if {@code hexId} parses, resolves, is of {@code type}, and is completed for the player's team. */
    private static <T extends QuestObject> boolean isCompleted(ServerPlayer player, String hexId, Class<T> type) {
        T object = resolve(hexId, type);
        if (object == null) {
            return false;
        }
        TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        return data != null && data.isCompleted(object);
    }

    /** Parses {@code hexId} and resolves it in the server book, iff it is an instance of {@code type}. */
    private static <T extends QuestObject> T resolve(String hexId, Class<T> type) {
        if (hexId == null || ServerQuestFile.INSTANCE == null) {
            return null;
        }
        long id = QuestObjectBase.parseCodeString(hexId);
        if (id == 0L) {
            return null;
        }
        QuestObject object = ServerQuestFile.INSTANCE.get(id);
        return type.isInstance(object) ? type.cast(object) : null;
    }
}
