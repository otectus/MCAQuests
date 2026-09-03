package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import dev.otectus.mcaquests.quest.title.Titles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /**
     * {@inheritDoc} Delegates to {@link FtbqEventBridge}'s cached §12.4 guard-chain sweep over all
     * ten {@code mcaquests:} task classes — the same mechanics the event listeners use, just run for
     * every class at once rather than the subset a given domain event implies.
     */
    @Override
    public void recheckAll(ServerPlayer player) {
        try {
            FtbqEventBridge.recheckAll(player);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "recheckAll", t);
        }
    }

    /**
     * {@inheritDoc} Counts {@code mcaquests:}-namespaced tasks/rewards currently in the server book
     * by the registered {@link dev.ftb.mods.ftbquests.quest.task.TaskType}/{@code RewardType}'s own
     * {@code typeId} namespace (not by Java class), so reward counts become correct automatically
     * once M3.2 registers {@code FtbqRewardTypes} without touching this method again.
     */
    @Override
    public int[] integrationObjectCounts() {
        try {
            if (ServerQuestFile.INSTANCE == null) {
                return new int[]{0, 0};
            }
            int taskCount = ServerQuestFile.INSTANCE.collect(Task.class, task ->
                    McaQuests.MOD_ID.equals(task.getType().getTypeId().getNamespace())).size();
            int rewardCount = ServerQuestFile.INSTANCE.collect(Reward.class, reward ->
                    McaQuests.MOD_ID.equals(reward.getType().getTypeId().getNamespace())).size();
            return new int[]{taskCount, rewardCount};
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "integrationObjectCounts", t);
            return new int[]{0, 0};
        }
    }

    @Override
    public boolean isReal() {
        return true;
    }

    /**
     * {@inheritDoc} The Book → MCA half of {@code /mcaquests ftbq validate} (spec §21): walks every
     * {@code mcaquests:}-namespaced task and reward currently in the server book and cross-checks each
     * one's referenced MCA registry id(s) against the live registries (the same lookups
     * {@code FtbqEditorIdsSync} performs for the M5.1 known-ids sync, run in parallel here rather than
     * shared — that class additionally wants display names per id, which would make a shared helper
     * more awkward than two small independent scans). An empty/wildcard field (e.g. an unset
     * {@code quest_id}, or the {@code "ns:*"} namespace wildcard on {@code McaQuestCompletedTask}) is a
     * deliberate "any" and is never a finding.
     */
    @Override
    public List<FtbqBridge.BookReference> validateBookReferences() {
        try {
            if (ServerQuestFile.INSTANCE == null) {
                return List.of();
            }
            List<FtbqBridge.BookReference> findings = new ArrayList<>();
            for (Task task : ServerQuestFile.INSTANCE.collect(Task.class, t ->
                    McaQuests.MOD_ID.equals(t.getType().getTypeId().getNamespace()))) {
                checkTask(task, findings);
            }
            for (Reward reward : ServerQuestFile.INSTANCE.collect(Reward.class, r ->
                    McaQuests.MOD_ID.equals(r.getType().getTypeId().getNamespace()))) {
                checkReward(reward, findings);
            }
            return findings;
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "validateBookReferences", t);
            return List.of();
        }
    }

    private static void checkTask(Task task, List<FtbqBridge.BookReference> findings) {
        String chapter = task.getQuestChapter().getTitle().getString();
        String questCode = task.getQuest().getCodeString();
        String taskName = task.getType().getTypeId() + " [" + task.getCodeString() + "]";

        if (task instanceof McaQuestCompletedTask t) {
            checkQuestId(chapter, questCode, taskName, "quest_id", t.questId(), findings);
            checkChainId(chapter, questCode, taskName, "chain_id", t.chainId(), findings);
        } else if (task instanceof McaChainCompletedTask t) {
            checkChainId(chapter, questCode, taskName, "chain_id", t.chainId(), findings);
        } else if (task instanceof McaReputationTierTask t) {
            checkLadderTier(chapter, questCode, taskName, t.ladder(), t.tier(), findings);
        } else if (task instanceof McaTitleTask t) {
            checkTitleId(chapter, questCode, taskName, "title_id", t.titleId(), findings);
        } else if (task instanceof McaProjectCompletedTask t) {
            checkProjectId(chapter, questCode, taskName, "project_id", t.projectId(), findings);
        } else if (task instanceof McaProjectContributionTask t) {
            checkProjectId(chapter, questCode, taskName, "project_id", t.projectId(), findings);
        } else if (task instanceof McaSituationResolvedTask t) {
            checkSituationId(chapter, questCode, taskName, "situation_id", t.situationId(), findings);
        }
        // McaReputationTask / McaHeartsTask / McaMarriedTask reference no MCA registry id.
    }

    private static void checkReward(Reward reward, List<FtbqBridge.BookReference> findings) {
        if (!(reward instanceof McaGrantTitleReward t)) {
            return; // McaVillageReputationReward / McaHeartsReward reference no MCA registry id.
        }
        String chapter = reward.getQuestChapter().getTitle().getString();
        String questCode = reward.getQuest().getCodeString();
        String taskName = reward.getType().getTypeId() + " [" + reward.getCodeString() + "]";
        checkTitleId(chapter, questCode, taskName, "title_id", t.titleId(), findings);
    }

    // Severity split (M5.3 review): a MALFORMED id (not a valid ResourceLocation; or a chain/tier id
    // containing the reserved '|' separator, which the datapack loaders reject at their own load time)
    // could never resolve against any registry state → error. A WELL-FORMED id that is merely absent
    // from the current registries is the sanctioned forward-reference pattern (spec §20: authors
    // legitimately reference ids from datapacks they haven't written yet) → warning.

    /** {@code quest_id} is empty ("any"), a {@code "ns:*"} namespace wildcard, or a concrete quest id. */
    private static void checkQuestId(String chapter, String questCode, String taskName, String field,
                                      String rawId, List<FtbqBridge.BookReference> findings) {
        if (rawId == null || rawId.isEmpty() || rawId.endsWith(":*")) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, rawId, true));
        } else if (!QuestRegistry.contains(id)) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, rawId, false));
        }
    }

    private static void checkChainId(String chapter, String questCode, String taskName, String field,
                                      String chainId, List<FtbqBridge.BookReference> findings) {
        if (chainId == null || chainId.isEmpty()) {
            return;
        }
        if (chainId.indexOf('|') >= 0) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, chainId, true));
        } else if (!QuestRegistry.chainIds().contains(chainId)) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, chainId, false));
        }
    }

    private static void checkLadderTier(String chapter, String questCode, String taskName,
                                         String ladder, String tier, List<FtbqBridge.BookReference> findings) {
        if (ladder == null || ladder.isEmpty()) {
            return;
        }
        ResourceLocation ladderId = ResourceLocation.tryParse(ladder);
        if (ladderId == null) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, "ladder", ladder, true));
            return;
        }
        Optional<ReputationTierSet> set = ReputationTiers.get(ladderId);
        if (set.isEmpty()) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, "ladder", ladder, false));
            return; // tier membership is meaningless without its ladder; one finding is enough.
        }
        if (tier == null || tier.isEmpty()) {
            return;
        }
        if (tier.indexOf('|') >= 0) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, "tier", tier, true));
        } else if (set.get().indexOf(tier) < 0) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, "tier", tier, false));
        }
    }

    private static void checkTitleId(String chapter, String questCode, String taskName, String field,
                                      String titleId, List<FtbqBridge.BookReference> findings) {
        checkResourceId(chapter, questCode, taskName, field, titleId,
                id -> Titles.isDefined(id), findings);
    }

    private static void checkProjectId(String chapter, String questCode, String taskName, String field,
                                        String projectId, List<FtbqBridge.BookReference> findings) {
        checkResourceId(chapter, questCode, taskName, field, projectId,
                ProjectRegistry::contains, findings);
    }

    private static void checkSituationId(String chapter, String questCode, String taskName, String field,
                                          String situationId, List<FtbqBridge.BookReference> findings) {
        checkResourceId(chapter, questCode, taskName, field, situationId,
                SituationRegistry::contains, findings);
    }

    /** Shared shape for the plain ResourceLocation-keyed registries: unparsable = malformed, absent = unresolved. */
    private static void checkResourceId(String chapter, String questCode, String taskName, String field,
                                         String rawId, java.util.function.Predicate<ResourceLocation> exists,
                                         List<FtbqBridge.BookReference> findings) {
        if (rawId == null || rawId.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, rawId, true));
        } else if (!exists.test(id)) {
            findings.add(new FtbqBridge.BookReference(chapter, questCode, taskName, field, rawId, false));
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
