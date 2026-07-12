package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.ProjectLogSyncS2CPacket;
import dev.otectus.mcaquests.network.ProjectMenuDataS2CPacket;
import dev.otectus.mcaquests.network.ProjectMenuStatus;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.ProjectPhaseToastS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.objective.ProjectKillObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectPlaceBlockObjective;
import dev.otectus.mcaquests.project.objective.ProjectTalkObjective;
import dev.otectus.mcaquests.project.scope.ScopeIdentity;
import dev.otectus.mcaquests.project.scope.ScopeResolver;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectInstanceKey;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.api.event.ProjectEvent;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.ProgressionStats;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Server-authoritative controller for community projects (spec 0.4.0): surfaces projects at sponsors,
 * processes atomic contributions, advances phases, distributes rewards, credits event-driven objectives
 * within a village, and handles sponsor loss. Holds no state — everything lives in
 * {@link ProjectSavedData}. Mirrors {@code QuestManager}'s never-trust-the-client discipline.
 */
public final class ProjectManager {

    /** Transient per-player anti-spam gate: last accepted contribution game-time. */
    private static final Map<UUID, Long> lastContributeTick = new HashMap<>();

    private ProjectManager() {
    }

    private static boolean enabled() {
        return McaQuestsConfig.COMMON.enableVillageProjects.get();
    }

    private static int fallbackRadius() {
        return McaQuestsConfig.COMMON.defaultScopeFallbackRadius.get();
    }

    @Nullable
    private static Entity resolveVillager(ServerPlayer player, UUID villagerUuid) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(villagerUuid);
        return (entity != null && McaCompat.canPlayerInteract(player, entity)) ? entity : null;
    }

    // ---------------------------------------------------------------- contribution

    public static void contributeFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation projectId) {
        if (!enabled()) {
            return;
        }
        Entity villager = resolveVillager(player, villagerUuid);
        if (villager == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ProjectDefinition def = ProjectRegistry.get(projectId).orElse(null);
        if (def == null || def.phases().isEmpty() || !isEligibleSponsor(def, villager)) {
            return;
        }
        Optional<ScopeIdentity> scopeOpt = ScopeResolver.resolve(level, villager, player, def.scope(), fallbackRadius());
        if (scopeOpt.isEmpty()) {
            return;
        }
        // Anti-spam: rate-limit accepted contributions per player.
        long now = level.getGameTime();
        int interval = McaQuestsConfig.COMMON.projectContributeMinIntervalTicks.get();
        Long last = lastContributeTick.get(player.getUUID());
        if (interval > 0 && last != null && now - last < interval) {
            return;
        }

        ProjectSavedData data = ProjectSavedData.get(server);
        ScopeIdentity scope = scopeOpt.get();
        ProjectInstanceKey key = new ProjectInstanceKey(projectId, def.scopeType(), scope.identity());
        ProjectState state = data.getInstance(key).orElse(null);
        if (state == null) {
            if (!conditionsPass(player, villager, def)) {
                return;
            }
            state = new ProjectState(projectId, def.scopeType(), scope.identity(), scope.dimension(),
                    scope.anchor(), scope.villageId(), now, def.phase(0).objectives().size());
            data.putInstance(state);
        }
        if (state.status().isTerminal()) {
            return;
        }
        if (state.status() == ProjectStatus.PAUSED) {
            state.setStatus(ProjectStatus.ACTIVE); // an eligible sponsor resumed it
        }
        state.addSponsor(villagerUuid);

        int defaultCap = McaQuestsConfig.COMMON.defaultPerPlayerContributionCap.get();
        boolean contributed = false;
        var phase = def.phase(state.currentPhase());
        for (int i = 0; i < phase.objectives().size(); i++) {
            ProjectObjective objective = phase.objectives().get(i);
            if (!objective.isContribution()) {
                continue;
            }
            int cap = objective.perPlayerCap() > 0 ? objective.perPlayerCap() : defaultCap;
            int banked = objective.contribute(player, state.progress(i), cap);
            if (banked > 0) {
                bankContribution(def, state, player, i, banked);
                contributed = true;
            }
        }
        if (contributed) {
            lastContributeTick.put(player.getUUID(), now);
        }
        checkPhaseAdvance(server, level, data, state, def, player, villager);
        data.setDirty();

        sendProjectMenu(player, villager);
        syncProjects(player);
    }

    // ---------------------------------------------------------------- offers / menu

    /** Projects this villager should show now: an in-progress instance it can host, or a new offer. */
    public static List<ProjectDefinition> projectsToShow(ServerPlayer player, Entity villager) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        long worldDay = level.getDayTime() / 24000L;
        int max = McaQuestsConfig.COMMON.projectOffersPerVillager.get();
        if (max <= 0) {
            return List.of();
        }
        List<ProjectDefinition> shown = new ArrayList<>();
        for (ProjectDefinition def : ProjectRegistry.all()) {
            if (!def.enabled() || def.phases().isEmpty() || !isEligibleSponsor(def, villager)) {
                continue;
            }
            Optional<ScopeIdentity> scope = ScopeResolver.resolve(level, villager, player, def.scope(), fallbackRadius());
            if (scope.isEmpty()) {
                continue;
            }
            ProjectInstanceKey key = new ProjectInstanceKey(def.id(), def.scopeType(), scope.get().identity());
            Optional<ProjectState> existing = data.getInstance(key);
            if (existing.isPresent()) {
                if (!existing.get().status().isTerminal()) {
                    shown.add(def); // already running here — any eligible sponsor can take contributions
                }
            } else if (conditionsPass(player, villager, def)
                    && isDailyRepresentative(level, def, villager, scope.get().villageId(), worldDay)) {
                shown.add(def); // a fresh offer to begin the project
            }
            if (shown.size() >= max) {
                break;
            }
        }
        return shown;
    }

    /** Sends the project menu cards for this villager (cached client-side; drives the "View Project" button). */
    public static void sendProjectMenu(ServerPlayer player, Entity villager) {
        if (!(player.level() instanceof ServerLevel level) || player.getServer() == null) {
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(player.getServer());
        List<ProjectCard> cards = new ArrayList<>();
        for (ProjectDefinition def : projectsToShow(player, villager)) {
            ScopeResolver.resolve(level, villager, player, def.scope(), fallbackRadius()).ifPresent(scope -> {
                ProjectInstanceKey key = new ProjectInstanceKey(def.id(), def.scopeType(), scope.identity());
                ProjectState state = data.getInstance(key).orElse(null);
                cards.add(buildCard(player, villager, def, state, scope));
            });
        }
        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ProjectMenuDataS2CPacket(villager.getUUID(), cards));
    }

    private static ProjectCard buildCard(ServerPlayer player, Entity villager, ProjectDefinition def,
                                         @Nullable ProjectState state, ScopeIdentity scope) {
        int phaseIdx = state == null ? 0 : state.currentPhase();
        ProjectMenuStatus status = state == null ? ProjectMenuStatus.OFFER
                : state.status().isTerminal() ? ProjectMenuStatus.COMPLETE : ProjectMenuStatus.IN_PROGRESS;
        ProjectPhase phase = def.phase(phaseIdx);
        Component dialogue = phase.dialogueOr(status == ProjectMenuStatus.OFFER ? "offer" : "in_progress", def.displayTitle());
        return new ProjectCard(def.id(), def.displayTitle(), scopeLabel(def),
                sponsorLabel(def, villager, scope), phaseLabel(def, phaseIdx), dialogue,
                objectiveLines(player, def, state, phaseIdx), rewardLines(phase), status);
    }

    private static List<ProjectObjectiveLine> objectiveLines(ServerPlayer player, ProjectDefinition def,
                                                             @Nullable ProjectState state, int phaseIdx) {
        List<ProjectObjectiveLine> lines = new ArrayList<>();
        ProjectPhase phase = def.phase(phaseIdx);
        for (int i = 0; i < phase.objectives().size(); i++) {
            ProjectObjective objective = phase.objectives().get(i);
            if (state != null && i < state.progressCount()) {
                SharedObjectiveProgress progress = state.progress(i);
                lines.add(new ProjectObjectiveLine(objective.describe(), objective.current(progress),
                        objective.required(), progress.contributionOf(player.getUUID())));
            } else {
                lines.add(new ProjectObjectiveLine(objective.describe(), 0, objective.required(), 0));
            }
        }
        return lines;
    }

    private static List<Component> rewardLines(ProjectPhase phase) {
        List<Component> lines = new ArrayList<>();
        for (SharedReward reward : phase.rewards()) {
            lines.add(Component.empty().append(reward.reward().describe())
                    .append(Component.literal(" "))
                    .append(Component.translatable(reward.target().translationKey())));
        }
        return lines;
    }

    private static Component scopeLabel(ProjectDefinition def) {
        return Component.translatable("mcaquests.project.scope." + def.scopeType().lower());
    }

    private static Component sponsorLabel(ProjectDefinition def, Entity villager, ScopeIdentity scope) {
        if (def.scopeType() == ProjectScope.VILLAGE && scope.villageId().isPresent()
                && villager.level() instanceof ServerLevel level) {
            Optional<String> name = McaCompat.villageName(level, scope.villageId().getAsInt());
            if (name.isPresent()) {
                return Component.translatable("mcaquests.label.project.village", name.get());
            }
        }
        return Component.translatable("mcaquests.label.project.sponsor", McaCompat.getVillagerDisplayName(villager));
    }

    private static Component phaseLabel(ProjectDefinition def, int phaseIdx) {
        return Component.translatable("mcaquests.label.project.phase", phaseIdx + 1, def.phaseCount());
    }

    // ---------------------------------------------------------------- eligibility

    private static boolean isEligibleSponsor(ProjectDefinition def, Entity villager) {
        if (!McaCompat.isMcaVillager(villager)) {
            return false;
        }
        if (def.sponsor().adultOnly() && !McaCompat.isAdult(villager)) {
            return false;
        }
        if (def.sponsor().pinnedSponsors().contains(villager.getUUID())) {
            return true;
        }
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        return def.sponsor().isGeneric()
                || ProfessionMatcher.matchesAny(def.sponsor().professions(), profession,
                        McaQuestsConfig.COMMON.professionMatchingMode.get());
    }

    private static boolean conditionsPass(ServerPlayer player, Entity villager, ProjectDefinition def) {
        if (def.conditions().isEmpty()) {
            return true;
        }
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            return true;
        }
        return def.conditions().get().test(new QuestContext(player, villager, data, def.id()));
    }

    /** Anti-flood: with oneSponsorPerProjectPerDay, only the lowest-hash eligible village resident offers it. */
    private static boolean isDailyRepresentative(ServerLevel level, ProjectDefinition def, Entity villager,
                                                 OptionalInt villageId, long worldDay) {
        if (!McaQuestsConfig.COMMON.oneSponsorPerProjectPerDay.get() || villageId.isEmpty()) {
            return true;
        }
        UUID best = null;
        long bestSeed = Long.MAX_VALUE;
        for (Entity resident : McaCompat.loadedVillageResidents(level, villageId.getAsInt())) {
            if (!isEligibleSponsor(def, resident)) {
                continue;
            }
            long seed = repSeed(def.id(), resident.getUUID(), worldDay);
            if (seed < bestSeed) {
                bestSeed = seed;
                best = resident.getUUID();
            }
        }
        return best == null || best.equals(villager.getUUID());
    }

    private static long repSeed(ResourceLocation projectId, UUID uuid, long worldDay) {
        return ((long) projectId.hashCode() * 31L) ^ (uuid.hashCode() * 17L) ^ (worldDay * 1000003L);
    }

    // ---------------------------------------------------------------- phase advancement

    private static void checkPhaseAdvance(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                          ProjectState state, ProjectDefinition def,
                                          @Nullable ServerPlayer player, @Nullable Entity villager) {
        for (int guard = 0; guard <= def.phaseCount(); guard++) {
            if (state.status().isTerminal()) {
                return;
            }
            int current = state.currentPhase();
            ProjectPhase phase = def.phase(current);
            if (!phaseSatisfied(state, phase)) {
                return;
            }
            if (state.tryMarkPhaseDistributed(current)) {
                ProjectRewardDistributor.distribute(server, level, data, state, def, current);
                addReputation(server, data, state, def.reputation().onPhaseComplete());
                MinecraftForge.EVENT_BUS.post(new ProjectEvent.PhaseAdvanced(def, state, current));
                broadcastToast(server, state, def, current);
            }
            int next = current + 1;
            if (next < def.phaseCount()) {
                ProjectPhase nextPhase = def.phase(next);
                if (!unlockPasses(nextPhase, player, villager, data, state)) {
                    return; // wait — a later trigger (contribution/tick) re-checks the unlock gate
                }
                state.enterPhase(next, nextPhase.objectives().size());
            } else {
                state.setStatus(ProjectStatus.COMPLETED);
                addReputation(server, data, state, def.reputation().onProjectComplete());
                completeProject(server, def, state);
                def.followUp().ifPresent(target -> seedFollowUp(server, level, data, state, target));
                return;
            }
        }
    }

    private static boolean phaseSatisfied(ProjectState state, ProjectPhase phase) {
        for (int i = 0; i < phase.objectives().size(); i++) {
            if (i >= state.progressCount() || !phase.objectives().get(i).isSatisfied(state.progress(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean unlockPasses(ProjectPhase phase, @Nullable ServerPlayer player, @Nullable Entity villager,
                                        ProjectSavedData data, ProjectState state) {
        if (phase.unlock().isEmpty()) {
            return true;
        }
        if (player == null) {
            return true; // best-effort: no player context this tick, allow (re-checked when one is present)
        }
        Entity context = villager != null ? villager : resolveSponsor(player.getServer(), state);
        if (context == null) {
            return true;
        }
        PlayerQuestData pdata = QuestCapabilities.get(player).orElse(null);
        if (pdata == null) {
            return true;
        }
        return phase.unlock().get().test(new QuestContext(player, context, pdata, state.projectId()));
    }

    private static void broadcastToast(MinecraftServer server, ProjectState state, ProjectDefinition def, int phase) {
        Component title = def.displayTitle();
        Component phaseLabel = phaseLabel(def, phase);
        for (UUID uuid : state.participants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                        new ProjectPhaseToastS2CPacket(title, phaseLabel));
                syncProjects(p);
            }
        }
    }

    static void addReputation(MinecraftServer server, ProjectSavedData data, ProjectState state, int delta) {
        dev.otectus.mcaquests.quest.reputation.ReputationService.award(data, server, state.identity(), delta, null);
    }

    /** Seeds a follow-up project in the same scope identity. Public for the reward distributor. */
    public static void seedFollowUp(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                    ProjectState from, ResourceLocation targetId) {
        ProjectDefinition target = ProjectRegistry.get(targetId).orElse(null);
        if (target == null || target.phases().isEmpty()) {
            return;
        }
        ProjectInstanceKey key = new ProjectInstanceKey(targetId, from.scope(), from.identity());
        if (data.getInstance(key).isPresent()) {
            return;
        }
        ProjectState seeded = new ProjectState(targetId, from.scope(), from.identity(), from.anchorDimension(),
                from.anchorPos(), from.villageId(), level.getGameTime(), target.phase(0).objectives().size());
        from.sponsors().forEach(seeded::addSponsor);
        data.putInstance(seeded);
    }

    // ---------------------------------------------------------------- event-driven credit

    public static void onProjectKill(ServerPlayer player, Entity killed) {
        creditEvent(player, killed.blockPosition(), (state, def, phase, i) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (objective instanceof ProjectKillObjective kill && kill.matches(killed)) {
                credit(def, state, i, player, 1);
                return true;
            }
            return false;
        });
    }

    public static void onProjectPlace(ServerPlayer player, BlockState placed, BlockPos pos) {
        creditEvent(player, pos, (state, def, phase, i) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (objective instanceof ProjectPlaceBlockObjective place && place.matches(placed)) {
                credit(def, state, i, player, 1);
                return true;
            }
            return false;
        });
    }

    public static void onProjectTalk(ServerPlayer player, Entity villager) {
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        UUID villagerUuid = villager.getUUID();
        creditEvent(player, villager.blockPosition(), (state, def, phase, i) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (objective instanceof ProjectTalkObjective talk && talk.matches(profession)
                    && state.progress(i).markTalkedTo(villagerUuid)) {
                credit(def, state, i, player, 1);
                return true;
            }
            return false;
        });
    }

    private interface ObjectiveCredit {
        boolean apply(ProjectState state, ProjectDefinition def, int phase, int objectiveIndex);
    }

    private static void creditEvent(ServerPlayer player, BlockPos where, ObjectiveCredit credit) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
        for (ProjectState state : data.allInstances()) {
            if (state.status() != ProjectStatus.ACTIVE || !inScopeAt(level, state, where)) {
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
            if (def == null || state.currentPhase() >= def.phaseCount()) {
                continue;
            }
            ProjectPhase phase = def.phase(state.currentPhase());
            boolean changed = false;
            for (int i = 0; i < phase.objectives().size() && i < state.progressCount(); i++) {
                if (phase.objectives().get(i).isEventDriven() && credit.apply(state, def, state.currentPhase(), i)) {
                    changed = true;
                }
            }
            if (changed) {
                checkPhaseAdvance(server, level, data, state, def, player, null);
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
            syncProjects(player);
        }
    }

    private static void credit(ProjectDefinition def, ProjectState state, int objectiveIndex, ServerPlayer player,
                               int amount) {
        SharedObjectiveProgress progress = state.progress(objectiveIndex);
        progress.add(amount);
        progress.addContribution(player.getUUID(), amount);
        bankContribution(def, state, player, objectiveIndex, amount);
    }

    /**
     * The single funnel through which every banked contribution — the packet-driven donate objectives
     * ({@code contributeFromPacket}) and the event-driven kill/place/talk objectives ({@code credit}) —
     * records the participant and posts {@link ProjectEvent.Contributed} (Risk R1): both paths already
     * mutate {@code SharedObjectiveProgress} themselves (a donate objective consumes items atomically in
     * {@code ProjectObjective.contribute}; event-driven credit adds directly), so this method owns only
     * the bookkeeping and event post common to both, called exactly once per bank.
     */
    private static void bankContribution(ProjectDefinition def, ProjectState state, ServerPlayer player,
                                         int objectiveIndex, int amount) {
        if (amount <= 0) {
            return;
        }
        state.addParticipant(player.getUUID());
        // ProgressionStats (spec section 11.2): +amount for the contributing player, keyed by project id.
        QuestCapabilities.get(player).ifPresent(pdata ->
                ProgressionStats.increment(pdata.stats().projectContributions(), state.projectId(), amount));
        MinecraftForge.EVENT_BUS.post(new ProjectEvent.Contributed(def, state, player, objectiveIndex, amount));
    }

    private static boolean inScopeAt(ServerLevel level, ProjectState state, BlockPos where) {
        return state.anchorDimension().equals(level.dimension().location())
                && ScopeResolver.isWithinScope(level, state.scope(), state.villageId(), state.anchorPos(),
                        fallbackRadius(), where);
    }

    // ---------------------------------------------------------------- sponsor loss

    public static void onSponsorDeath(MinecraftServer server, UUID villagerUuid) {
        if (!enabled()) {
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
        for (ProjectState state : data.allInstances()) {
            if (state.status().isTerminal()) {
                continue;
            }
            if (state.removeSponsor(villagerUuid) && state.sponsors().isEmpty()) {
                ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
                if (def != null) {
                    applySponsorLoss(server, data, state, def);
                }
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private static void applySponsorLoss(MinecraftServer server, ProjectSavedData data, ProjectState state,
                                         ProjectDefinition def) {
        var behavior = def.sponsor().onDeathOr(McaQuestsConfig.COMMON.defaultSponsorDeathBehavior.get());
        switch (behavior) {
            case FAIL -> {
                state.setStatus(ProjectStatus.FAILED);
                addReputation(server, data, state, def.reputation().onFail());
                MinecraftForge.EVENT_BUS.post(new ProjectEvent.Failed(def, state));
            }
            case PAUSE -> state.setStatus(ProjectStatus.PAUSED);
            case TRANSFER -> {
                if (!tryTransfer(server, state, def)) {
                    state.setStatus(ProjectStatus.PAUSED);
                }
            }
            case TURN_IN_TO_VILLAGE -> {
                addReputation(server, data, state, def.reputation().onProjectComplete());
                state.setStatus(ProjectStatus.COMPLETED);
                completeProject(server, def, state);
            }
        }
    }

    /**
     * The single funnel through which every project completion — the normal phase-advance path in
     * {@link #checkPhaseAdvance} and the sponsor-death turn-in path in {@link #applySponsorLoss} — records
     * ProgressionStats (spec section 11.2: +1 per online participant, keyed by project definition id) and
     * posts {@link ProjectEvent.Completed}, so no completion path can double-post or skip the counter.
     */
    private static void completeProject(MinecraftServer server, ProjectDefinition def, ProjectState state) {
        for (UUID uuid : state.participants()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(uuid);
            if (participant != null) {
                QuestCapabilities.get(participant).ifPresent(pdata ->
                        ProgressionStats.increment(pdata.stats().projectCompletions(), state.projectId(), 1));
            }
        }
        MinecraftForge.EVENT_BUS.post(new ProjectEvent.Completed(def, state));
    }

    private static boolean tryTransfer(MinecraftServer server, ProjectState state, ProjectDefinition def) {
        if (state.villageId().isEmpty()) {
            return false;
        }
        ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
        if (level == null) {
            return false;
        }
        for (Entity resident : McaCompat.loadedVillageResidents(level, state.villageId().getAsInt())) {
            if (resident.isAlive() && isEligibleSponsor(def, resident)) {
                state.addSponsor(resident.getUUID());
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- sync / login

    /** Pushes the player's participating-project snapshot for the quest log + HUD. */
    public static void syncProjects(ServerPlayer player) {
        if (!enabled() || player.getServer() == null) {
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(player.getServer());
        UUID uuid = player.getUUID();
        List<ProjectLogEntry> entries = new ArrayList<>();
        for (ProjectState state : data.allInstances()) {
            if (state.status() != ProjectStatus.ACTIVE || !state.participants().contains(uuid)) {
                continue;
            }
            ProjectRegistry.get(state.projectId()).ifPresent(def -> entries.add(new ProjectLogEntry(
                    state.projectId(), def.displayTitle(), sponsorLogLabel(player, state),
                    scopeLabel(def), phaseLabel(def, state.currentPhase()),
                    objectiveLines(player, def, state, state.currentPhase()))));
        }
        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ProjectLogSyncS2CPacket(entries));
    }

    private static Component sponsorLogLabel(ServerPlayer player, ProjectState state) {
        if (state.villageId().isPresent() && player.level() instanceof ServerLevel level) {
            Optional<String> name = McaCompat.villageName(level, state.villageId().getAsInt());
            if (name.isPresent()) {
                return Component.translatable("mcaquests.label.project.village", name.get());
            }
        }
        return Component.empty();
    }

    /** Delivers any phase rewards owed to a returning player (login). */
    public static void deliverPending(ServerPlayer player) {
        if (!enabled() || player.getServer() == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(player.getServer());
        List<PendingReward> owed = data.drainPending(player.getUUID());
        for (PendingReward reward : owed) {
            ProjectDefinition def = ProjectRegistry.get(reward.projectId()).orElse(null);
            if (def == null) {
                continue;
            }
            data.allInstances().stream()
                    .filter(s -> s.projectId().equals(reward.projectId()))
                    .findFirst()
                    .ifPresent(state -> ProjectRewardDistributor.grantPending(level, state, player, def,
                            reward.phase(), reward.rewardIndex()));
        }
        syncProjects(player);
    }

    // ---------------------------------------------------------------- helpers shared with distributor/commands

    @Nullable
    public static Entity resolveSponsor(@Nullable MinecraftServer server, ProjectState state) {
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
        if (level == null) {
            return null;
        }
        for (UUID uuid : state.sponsors()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private static ResourceKey<net.minecraft.world.level.Level> dimensionKey(ResourceLocation dimension) {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }

    // ---------------------------------------------------------------- admin

    /** Removes every active instance of {@code projectId} (it will recreate fresh on next interaction). */
    public static int adminReset(MinecraftServer server, ResourceLocation projectId) {
        ProjectSavedData data = ProjectSavedData.get(server);
        List<String> keys = new ArrayList<>();
        for (ProjectState state : data.allInstances()) {
            if (state.projectId().equals(projectId)) {
                keys.add(state.key().asString());
            }
        }
        keys.forEach(data::removeInstance);
        return keys.size();
    }

    /** Force-advances every active instance of {@code projectId} by one phase (testing). */
    public static int adminAdvance(MinecraftServer server, ResourceLocation projectId) {
        ProjectDefinition def = ProjectRegistry.get(projectId).orElse(null);
        if (def == null) {
            return 0;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        int advanced = 0;
        for (ProjectState state : data.allInstances()) {
            if (!state.projectId().equals(projectId) || state.status().isTerminal()) {
                continue;
            }
            int next = state.currentPhase() + 1;
            if (next < def.phaseCount()) {
                state.enterPhase(next, def.phase(next).objectives().size());
            } else {
                state.setStatus(ProjectStatus.COMPLETED);
            }
            advanced++;
        }
        if (advanced > 0) {
            data.setDirty();
        }
        return advanced;
    }

    public static List<ProjectState> activeInstances(MinecraftServer server) {
        return new ArrayList<>(ProjectSavedData.get(server).allInstances());
    }

    public static int reputationOf(MinecraftServer server, String identity) {
        return ProjectSavedData.get(server).reputation(identity);
    }

    // ---------------------------------------------------------------- debug

    /** Human-readable explanation of why {@code projectId} is or is not available from {@code villager}. */
    public static List<Component> explainAvailability(ServerPlayer player, Entity villager, ResourceLocation projectId) {
        List<Component> out = new ArrayList<>();
        ProjectDefinition def = ProjectRegistry.get(projectId).orElse(null);
        if (def == null) {
            out.add(Component.literal("Unknown project '" + projectId + "'."));
            return out;
        }
        out.add(Component.literal("Project " + projectId + " [" + def.scopeType().lower() + "]"));
        boolean eligible = isEligibleSponsor(def, villager);
        out.add(line("sponsor match", eligible));
        if (!(player.level() instanceof ServerLevel level)) {
            out.add(Component.literal("  not on a server level."));
            return out;
        }
        Optional<ScopeIdentity> scope = ScopeResolver.resolve(level, villager, player, def.scope(), fallbackRadius());
        out.add(line("scope resolved", scope.isPresent()));
        scope.ifPresent(s -> out.add(Component.literal("  identity: " + s.identity())));
        boolean conditions = conditionsPass(player, villager, def);
        out.add(line("conditions pass", conditions));
        scope.ifPresent(s -> {
            ProjectInstanceKey key = new ProjectInstanceKey(def.id(), def.scopeType(), s.identity());
            Optional<ProjectState> existing = ProjectSavedData.get(player.getServer()).getInstance(key);
            existing.ifPresent(st -> out.add(Component.literal(
                    "  instance: phase " + (st.currentPhase() + 1) + "/" + def.phaseCount() + " (" + st.status().lower() + ")")));
            long worldDay = level.getDayTime() / 24000L;
            out.add(line("daily representative", isDailyRepresentative(level, def, villager, s.villageId(), worldDay)));
        });
        boolean available = projectsToShow(player, villager).contains(def);
        out.add(Component.literal(available ? "=> AVAILABLE" : "=> NOT AVAILABLE"));
        return out;
    }

    private static Component line(String label, boolean ok) {
        return Component.literal("  " + (ok ? "[ok] " : "[no] ") + label);
    }
}
