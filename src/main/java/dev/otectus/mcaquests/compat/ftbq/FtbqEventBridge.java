package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.events.ClearFileCacheEvent;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.api.event.ProjectEvent;
import dev.otectus.mcaquests.api.event.QuestCompletedEvent;
import dev.otectus.mcaquests.api.event.ReputationTierReachedEvent;
import dev.otectus.mcaquests.api.event.SituationResolvedEvent;
import dev.otectus.mcaquests.api.event.TitleGrantedEvent;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge-bus + Architectury listener table that pushes MCA: Quests domain events into the FTB
 * Quests book (spec §15.0, §12.3, §12.4). Registered <b>manually</b> from {@link FtbqBootstrap#init()}
 * via {@link MinecraftForge#EVENT_BUS}{@code .addListener(...)} / {@link ClearFileCacheEvent#EVENT}
 * {@code .register(...)} calls — deliberately <em>not</em> {@code @Mod.EventBusSubscriber}, so that
 * when FTB Quests is absent (and {@link FtbqBootstrap#init()} never runs) not a single listener is
 * ever attached to the bus (spec §10.4 / {@code NoFtbqClassloadTest}: this whole package only
 * classloads when {@code init()} runs, and {@code @Mod.EventBusSubscriber} would instead attach at
 * class-scan time regardless of whether FTB Quests is installed).
 *
 * <h2>Task-list caching (§12.3)</h2>
 * One lazily-built {@code List<Task>} per Mca task class, cached in {@link #TASK_CACHE}, mirroring
 * {@code FTBQuestsEventHandler.autoSubmitTasks} (FTB-Quests {@code v2001.4.22},
 * {@code FTBQuestsEventHandler.java:48,149-151}: {@code private List<Task> autoSubmitTasks = null;}
 * lazily populated via {@code file.collect(predicate)}). Built from
 * {@code ServerQuestFile.INSTANCE.collect(Class)} ({@code quest/BaseQuestFile.java:1274-1276}, sugar
 * over the predicate overload at {@code 1256-1272}). Dropped on {@link ClearFileCacheEvent} (fired
 * from {@code BaseQuestFile.clearCachedData()}, {@code quest/BaseQuestFile.java:1084-1098} — any file
 * load/edit) exactly as {@code FTBQuestsEventHandler.fileCacheClear} does
 * ({@code FTBQuestsEventHandler.java:100-108}), and additionally on {@link ServerStoppingEvent} so a
 * later server start in the same JVM (e.g. an integrated-server world switch) never sweeps against
 * {@link Task} instances that belonged to a book that no longer exists — {@code ServerStoppingEvent}
 * fires before FTBQ nulls {@code ServerQuestFile.INSTANCE} in its own {@code SERVER_STOPPING}-staged
 * handler, so our cache is torn down at the same lifecycle stage FTBQ tears down its own.
 *
 * <h2>Sweep guardrails (§12.4), verbatim</h2>
 * {@link #sweep} is exactly the {@code StageTask.checkStages(ServerPlayer)} idiom
 * ({@code quest/task/StageTask.java:80-97}, explicitly commented there as "hook for FTB XMod Compat
 * to call into" — the supported external-push entry point we mirror): resolve
 * {@code TeamData} via {@code ServerQuestFile.INSTANCE.getOrCreateTeamData(player)}
 * ({@code BaseQuestFile.java:1018-1024}, nullable); bail if null or {@code data.isLocked()}
 * ({@code TeamData.java:734-736}); wrap the whole per-task-class loop in
 * {@code ServerQuestFile.INSTANCE.withPlayerContext(player, Runnable)} — declared on
 * {@code ServerQuestFile}, not {@code BaseQuestFile} ({@code quest/ServerQuestFile.java:158-165});
 * per task, {@code !data.isCompleted(task) && data.canStartTasks(task.getQuest())}
 * ({@code TeamData.java:559-561,593-597}) before {@code task.submitTask(data, player)} — the
 * {@code final} 2-arg convenience overload ({@code quest/task/Task.java:288-290}, delegates to the
 * 3-arg form with {@code ItemStack.EMPTY}).
 *
 * <h2>Listener table (§15.0, with §15.3's reputation push note)</h2>
 * <ul>
 *   <li>{@link QuestCompletedEvent} → sweep({@code McaQuestCompletedTask}, {@code McaChainCompletedTask},
 *       {@code McaReputationTask}) for the event's player.</li>
 *   <li>{@link SituationResolvedEvent} → sweep({@code McaSituationResolvedTask}, {@code McaReputationTask})
 *       for each <em>online</em> participant.</li>
 *   <li>{@link ProjectEvent.Completed} → sweep({@code McaProjectCompletedTask}, {@code McaReputationTask})
 *       for each online participant (from {@code state().participants()}).</li>
 *   <li>{@link ProjectEvent.Contributed} → sweep({@code McaProjectContributionTask}) for the contributor.</li>
 *   <li>{@link ReputationTierReachedEvent} → sweep({@code McaReputationTask}, {@code McaReputationTierTask})
 *       when the player is non-null.</li>
 *   <li>{@link TitleGrantedEvent} → sweep({@code McaTitleTask}) for the event's player.</li>
 *   <li>Forge {@code PlayerEvent.PlayerLoggedInEvent} → {@link #recheckAll}.</li>
 * </ul>
 * Every one of the seven listeners above early-returns when {@code !FtbqBridge.Holder.get().isAvailable()}.
 * The two maintenance listeners ({@link ClearFileCacheEvent}, {@link ServerStoppingEvent}) do
 * <em>not</em> gate on {@code isAvailable()} — they must always run so the cache (and the
 * {@code McaReputationTierTask} WARN set) never survives a book reload or server restart even if the
 * config master switch happens to be off at that instant; every actual state <em>write</em> still goes
 * through {@code Mca*TaskBase}'s own {@code isAvailable()} check (spec §13), so this is cache hygiene
 * only, never a behavior leak.
 */
public final class FtbqEventBridge {

    private static final Map<Class<? extends Task>, List<Task>> TASK_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static final Class<? extends Task>[] ALL_TASK_CLASSES = new Class[]{
            McaQuestCompletedTask.class,
            McaChainCompletedTask.class,
            McaReputationTask.class,
            McaReputationTierTask.class,
            McaTitleTask.class,
            McaProjectCompletedTask.class,
            McaProjectContributionTask.class,
            McaSituationResolvedTask.class,
            McaHeartsTask.class,
            McaMarriedTask.class,
    };

    private FtbqEventBridge() {
    }

    /** Called exactly once, from {@link FtbqBootstrap#init()}. */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onQuestCompleted);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onSituationResolved);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onProjectCompleted);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onProjectContributed);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onReputationTierReached);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onTitleGranted);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(FtbqEventBridge::onServerStopping);
        ClearFileCacheEvent.EVENT.register(FtbqEventBridge::onClearFileCache);
    }

    // ---------------------------------------------------------------------------------------------
    // Listener table (§15.0)

    private static void onQuestCompleted(QuestCompletedEvent event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            sweep(event.getPlayer(), McaQuestCompletedTask.class, McaChainCompletedTask.class, McaReputationTask.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onQuestCompleted", t);
        }
    }

    private static void onSituationResolved(SituationResolvedEvent event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            for (UUID uuid : event.getParticipants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    sweep(player, McaSituationResolvedTask.class, McaReputationTask.class);
                }
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onSituationResolved", t);
        }
    }

    private static void onProjectCompleted(ProjectEvent.Completed event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            for (UUID uuid : event.state().participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    sweep(player, McaProjectCompletedTask.class, McaReputationTask.class);
                }
            }
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onProjectCompleted", t);
        }
    }

    private static void onProjectContributed(ProjectEvent.Contributed event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            sweep(event.player(), McaProjectContributionTask.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onProjectContributed", t);
        }
    }

    private static void onReputationTierReached(ReputationTierReachedEvent event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        try {
            sweep(player, McaReputationTask.class, McaReputationTierTask.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onReputationTierReached", t);
        }
    }

    private static void onTitleGranted(TitleGrantedEvent event) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            sweep(event.getPlayer(), McaTitleTask.class);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "onTitleGranted", t);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recheckAll(player);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Cache lifecycle (§12.3)

    private static void onClearFileCache(BaseQuestFile file) {
        if (!file.isServerSide()) {
            return;
        }
        TASK_CACHE.clear();
        McaReputationTierTask.clearWarned();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TASK_CACHE.clear();
        McaReputationTierTask.clearWarned();
    }

    // ---------------------------------------------------------------------------------------------
    // recheckAll (used by PlayerLoggedInEvent, FtbqBridgeImpl.recheckAll, and the /mcaquests reload
    // funnel via the FtbqBridge interface)

    /** Re-evaluates all ten {@code mcaquests:} FTB task classes for {@code player}. */
    static void recheckAll(ServerPlayer player) {
        if (!FtbqBridge.Holder.get().isAvailable()) {
            return;
        }
        try {
            sweep(player, ALL_TASK_CLASSES);
        } catch (Throwable t) {
            McaQuests.LOGGER.debug("[MCA: Quests] FTBQ event bridge {} failed", "recheckAll", t);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The §12.4 guard chain

    @SafeVarargs
    private static void sweep(ServerPlayer player, Class<? extends Task>... classes) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) {
            return;
        }
        TeamData data = file.getOrCreateTeamData(player);
        if (data == null || data.isLocked()) {
            return;
        }
        file.withPlayerContext(player, () -> {
            for (Class<? extends Task> clazz : classes) {
                for (Task task : cachedTasks(file, clazz)) {
                    if (!data.isCompleted(task) && data.canStartTasks(task.getQuest())) {
                        task.submitTask(data, player);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Task> cachedTasks(ServerQuestFile file, Class<? extends Task> clazz) {
        return TASK_CACHE.computeIfAbsent(clazz, c -> (List<Task>) (List<?>) file.collect(c));
    }
}
