package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadContentGate;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.network.ProjectCard;
import dev.otectus.mcaquests.network.ProjectLogSyncS2CPacket;
import dev.otectus.mcaquests.network.ProjectMenuDataS2CPacket;
import dev.otectus.mcaquests.network.ProjectMenuStatus;
import dev.otectus.mcaquests.network.ProjectObjectiveLine;
import dev.otectus.mcaquests.network.ProjectPhaseToastS2CPacket;
import dev.otectus.mcaquests.network.ProjectScopeS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.objective.ProjectKillObjective;
import dev.otectus.mcaquests.project.objective.PollingProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjectiveContext;
import dev.otectus.mcaquests.project.objective.ProjectObjectiveStatus;
import dev.otectus.mcaquests.project.objective.ProjectPlaceBlockObjective;
import dev.otectus.mcaquests.project.objective.ProjectTalkObjective;
import dev.otectus.mcaquests.project.scope.ScopeIdentity;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import dev.otectus.mcaquests.project.scope.ScopeResolver;
import dev.otectus.mcaquests.project.state.PendingReward;
import dev.otectus.mcaquests.project.state.ProjectInstanceKey;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.ProjectStatus;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.api.event.ProjectEvent;
import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

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

    /**
     * Transient anti-spam gate: last accepted contribution game-time, per player <em>and project
     * instance</em>. Keyed by player alone, one contribution to the mill locked the player out of the
     * bridge, the granary and every other project for {@code projectContributeMinIntervalTicks}.
     */
    private static final Map<ContributionGate, Long> lastContributeTick = new HashMap<>();

    /** The scope one contribution rate-limit covers: this player, this project instance. */
    private record ContributionGate(UUID player, ProjectInstanceKey project) {
    }

    /**
     * Above this many live gates a put prunes first. Below it the map is smaller than the cost of
     * walking it, and a single-player world never reaches it at all.
     */
    private static final int GATE_PRUNE_THRESHOLD = 64;

    /**
     * Records an accepted contribution and keeps the gate map bounded.
     *
     * <p>Entries are dropped once they are older than the throttle window (they can no longer block
     * anything), and also when the stored tick is in the future relative to {@code now} — that means the
     * tick came from a different world with a higher game time, and a stale gate from a previous save
     * would otherwise lock a player out for as long as the tick difference.
     */
    static void recordContribution(UUID player, ProjectInstanceKey project, long now, int interval) {
        if (lastContributeTick.size() >= GATE_PRUNE_THRESHOLD) {
            lastContributeTick.values().removeIf(tick -> tick > now || now - tick >= interval);
        }
        lastContributeTick.put(new ContributionGate(player, project), now);
    }

    /** Test-visible size of the throttle cache. */
    static int throttleGateCount() {
        return lastContributeTick.size();
    }

    /**
     * Drops every piece of per-session state this class holds. Called when a server stops, so a
     * single-player client that loads another world does not inherit the previous world's game-time
     * baselines.
     */
    public static void clearSessionState() {
        lastContributeTick.clear();
        PlacementFeedback.clearSessionState();
        ProjectMenuSessions.clearSessionState();
        dev.otectus.mcaquests.event.ConversationCredit.clearSessionState();
        ProjectRecovery.clearSessionState();
    }

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
        if (def == null || !def.enabled() || def.phases().isEmpty() || !isEligibleSponsor(def, villager)) {
            return;
        }
        Optional<ScopeIdentity> scopeOpt = ScopeResolver.resolve(level, villager, player, def.scope(), fallbackRadius());
        if (scopeOpt.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ProjectSavedData data = ProjectSavedData.get(server);
        ScopeIdentity scope = scopeOpt.get();
        ProjectInstanceKey key = new ProjectInstanceKey(projectId, def.scopeType(), scope.identity());

        // Anti-spam: rate-limit accepted contributions per player and project instance. Checked here
        // rather than before the key is built, because the key is what the limit is about.
        ContributionGate gate = new ContributionGate(player.getUUID(), key);
        int interval = McaQuestsConfig.COMMON.projectContributeMinIntervalTicks.get();
        Long last = lastContributeTick.get(gate);
        if (interval > 0 && last != null && now >= last && now - last < interval) {
            return;
        }
        ProjectState state = data.getInstance(key).orElse(null);
        if (state == null || state.canRetry(now)) {
            if (projectsToShow(player, villager).stream().noneMatch(offered -> offered.id().equals(projectId))) {
                return;
            }
            state = new ProjectState(projectId, def.scopeType(), scope.identity(), scope.dimension(),
                    scope.anchor(), scope.villageId(), now, def.phase(0).objectives().size());
            state.setStartDayTime(level.getDayTime());
            state.sampleClock(now, false);
            state.freezeAnchorRadius(def.scope().fallbackRadiusOr(fallbackRadius()));
            ProjectPhases.begin(server, level, def, state);
            data.putInstance(state);
        }
        if (state.status().isTerminal() || state.currentPhase() < 0
                || state.currentPhase() >= def.phaseCount()) {
            return;
        }
        if (state.status() == ProjectStatus.PAUSED) {
            state.sampleClock(now, true);
            state.setStatus(ProjectStatus.ACTIVE); // an eligible sponsor resumed it
        }
        state.addSponsor(villagerUuid);
        if (checkProjectFailure(server, level, data, state, def)) {
            sendProjectMenu(player, villager);
            syncProjects(player);
            return;
        }

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
            recordContribution(player.getUUID(), key, now, interval);
        }
        checkPhaseAdvance(server, level, data, state, def, player, villager);
        data.setDirty();

        sendProjectMenu(player, villager);
        server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
    }

    // ---------------------------------------------------------------- offers / menu

    /**
     * How many non-terminal project instances already share one scope identity.
     *
     * <p>Enforces {@code maxConcurrentProjectsPerScope}, which had been declared and documented since
     * 0.4.0 with nothing anywhere reading it: a village could accumulate every project in the catalogue at
     * once, which is not a village with a lot going on so much as a menu nobody can read.
     *
     * <p>Only new projects are capped. One already under way is never hidden from a sponsor because the
     * cap was later lowered — that would strand contributions the players have already made.
     */
    private static int openCountInScope(ProjectSavedData data, ProjectScope scope, String identity) {
        int open = 0;
        for (ProjectState state : data.allInstances()) {
            if (state.scope() == scope && state.identity().equals(identity)
                    && !state.status().isTerminal()) {
                open++;
            }
        }
        return open;
    }

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
            if (existing.isPresent() && !existing.get().canRetry(level.getGameTime())) {
                // Deliberately NOT re-tested against conditions. A project already under way must stay
                // reachable even once its gate has drifted from true — a winter project running into
                // spring — or the contributions players have already made are stranded with no way to
                // finish them. Conditions decide who may START a project, not who may finish one.
                if (!existing.get().status().isTerminal() && existing.get().currentPhase() >= 0
                        && existing.get().currentPhase() < def.phaseCount()) {
                    shown.add(def); // already running here — any eligible sponsor can take contributions
                }
            } else if (conditionsPass(player, villager, def)
                    && openCountInScope(data, def.scopeType(), scope.get().identity())
                            < McaQuestsConfig.COMMON.maxConcurrentProjectsPerScope.get()
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
        PacketDistributor.sendToPlayer(player,
                new ProjectMenuDataS2CPacket(villager.getUUID(), cards));
        ProjectMenuSessions.opened(player, villager.getUUID());
    }

    private static ProjectCard buildCard(ServerPlayer player, Entity villager, ProjectDefinition def,
                                         @Nullable ProjectState state, ScopeIdentity scope) {
        int phaseIdx = state == null ? 0 : state.currentPhase();
        if (state != null && state.canRetry(player.level().getGameTime())) {
            state = null;
            phaseIdx = 0;
        }
        ProjectMenuStatus status = state == null ? ProjectMenuStatus.OFFER
                : state.status().isTerminal() ? ProjectMenuStatus.COMPLETE : ProjectMenuStatus.IN_PROGRESS;
        ProjectPhase phase = def.phase(phaseIdx);
        Component dialogue = phase.dialogueOr(status == ProjectMenuStatus.OFFER ? "offer" : "in_progress", def.displayTitle());
        return new ProjectCard(def.id(), def.displayTitle(), scopeLabel(def),
                sponsorLabel(def, villager, scope, state), phaseLabel(def, phaseIdx), dialogue,
                objectiveLines(player, def, state, phaseIdx), rewardLines(phase), status,
                state == null ? "" : state.key().asString(), state == null ? 0L : state.revision(),
                state != null && !state.status().isTerminal() && hasPositionalWork(def, phaseIdx));
    }

    /**
     * One row per objective of {@code phaseIdx}: the shared count, the player's own share, a status in
     * words and a glyph, and the objective's own explanation built from the predicates that grant its
     * credit (1.7.0), so the help can never describe a rule the server does not apply.
     */
    private static List<ProjectObjectiveLine> objectiveLines(ServerPlayer player, ProjectDefinition def,
                                                             @Nullable ProjectState state, int phaseIdx) {
        return objectiveLines(player, def, state, phaseIdx, true);
    }

    /**
     * {@code withHelp} is false for the quest-log sync, which goes to every participant after every
     * credited event and never shows the expanded help; the status still travels with each row.
     */
    private static List<ProjectObjectiveLine> objectiveLines(ServerPlayer player, ProjectDefinition def,
                                                             @Nullable ProjectState state, int phaseIdx,
                                                             boolean withHelp) {
        List<ProjectObjectiveLine> lines = new ArrayList<>();
        ProjectPhase phase = def.phase(phaseIdx);
        MinecraftServer server = player.getServer();
        ServerLevel level = state != null && server != null
                ? server.getLevel(dimensionKey(state.anchorDimension()))
                : player.level() instanceof ServerLevel own ? own : null;
        for (int i = 0; i < phase.objectives().size(); i++) {
            ProjectObjective objective = phase.objectives().get(i);
            boolean live = state != null && i < state.progressCount() && state.currentPhase() == phaseIdx;
            SharedObjectiveProgress progress = live ? state.progress(i) : new SharedObjectiveProgress();
            ProjectObjectiveContext context = new ProjectObjectiveContext(server, level, def, live ? state : null,
                    phaseIdx, i, progress, player);
            ProjectObjectiveStatus status;
            List<Component> details;
            try {
                status = objective.status(context);
                details = withHelp ? objective.explain(context) : List.of();
            } catch (RuntimeException failure) {
                // Help is a courtesy; it must never take the project screen down with it.
                McaQuests.LOGGER.debug("[MCA: Quests] could not explain objective {} of {}", i, def.id(), failure);
                status = objective.isSatisfied(progress) ? ProjectObjectiveStatus.SATISFIED
                        : ProjectObjectiveStatus.IN_PROGRESS;
                details = List.of();
            }
            lines.add(new ProjectObjectiveLine(objective.describe(), objective.current(progress),
                    objective.requiredFor(progress), progress.contributionOf(player.getUUID()), status, details));
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

    /**
     * Who is backing this project, and — when the pack asked for more than one — how many have signed on.
     *
     * <p>{@code sponsor.required_count} was described in its own javadoc as "informational/UX" and then
     * never shown to anyone, which made it informational to nobody. A project that wants three sponsors
     * now says so on its card, and says how many it has.
     */
    private static Component sponsorLabel(ProjectDefinition def, Entity villager, ScopeIdentity scope,
                                          @Nullable ProjectState state) {
        Component who;
        if (def.scopeType() == ProjectScope.VILLAGE && scope.villageId().isPresent()
                && villager.level() instanceof ServerLevel level
                && McaCompat.villageName(level, scope.villageId().getAsInt()).isPresent()) {
            who = Component.translatable("mcaquests.label.project.village",
                    McaCompat.villageName(level, scope.villageId().getAsInt()).orElseThrow());
        } else {
            who = Component.translatable("mcaquests.label.project.sponsor",
                    McaCompat.getVillagerDisplayName(villager));
        }
        int wanted = def.sponsor().requiredCount();
        if (wanted <= 1) {
            return who; // the overwhelmingly common case; saying "1 of 1" would be noise
        }
        int have = state == null ? 0 : state.sponsors().size();
        return Component.translatable("mcaquests.label.project.sponsors_of", who, have, wanted);
    }

    private static Component phaseLabel(ProjectDefinition def, int phaseIdx) {
        return Component.translatable("mcaquests.label.project.phase", phaseIdx + 1, def.phaseCount());
    }

    // ---------------------------------------------------------------- eligibility

    private static boolean isEligibleSponsor(ProjectDefinition def, Entity villager) {
        if (!McaCompat.isMcaVillager(villager)) {
            return false;
        }
        if (IntegrationRequirements.unavailable(def).isPresent()
                || !TownsteadContentGate.allowsProject(def.id(), readsTownstead(def))) {
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

    /**
     * True when any phase of this project needs Townstead, so the content switch knows whether it
     * applies. Derived from the typed content (IntegrationRequirements) rather than from the id, so a
     * project that stops using Townstead stops being gated by it without anyone renaming the file.
     */
    private static boolean readsTownstead(ProjectDefinition def) {
        return IntegrationRequirements.dependsOn(def, IntegrationRequirements.Integration.TOWNSTEAD);
    }

    private static boolean conditionsPass(ServerPlayer player, Entity villager, ProjectDefinition def) {
        if (def.conditions().isEmpty()) {
            return true;
        }
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            return false;
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
            if (current < 0 || current >= def.phaseCount()) {
                return;
            }
            ProjectPhase phase = def.phase(current);
            if (!phaseSatisfied(state, phase)) {
                return;
            }
            if (state.tryMarkPhaseDistributed(current)) {
                ProjectRewardDistributor.distribute(server, level, data, state, def, current);
                ProjectReputation.apply(server, level, state, def, def.reputation().phaseOutcome(),
                        "phase", current);
                NeoForge.EVENT_BUS.post(new ProjectEvent.PhaseAdvanced(def, state, current));
                broadcastToast(server, state, def, current);
            }
            int next = current + 1;
            if (next < def.phaseCount()) {
                ProjectPhase nextPhase = def.phase(next);
                if (!unlockPasses(nextPhase, player, villager, data, state)) {
                    return; // wait — a later trigger (contribution/tick) re-checks the unlock gate
                }
                ProjectPhases.enter(server, level, def, state, next);
            } else {
                state.setStatus(ProjectStatus.COMPLETED);
                ProjectReputation.apply(server, level, state, def, def.reputation().completeOutcome(),
                        "complete", -1);
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
            // Polling has no initiating player. Only an online participant with a real sponsor
            // context can satisfy a declared gate; missing context never grants an unlock.
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            return server != null && state.participants().stream()
                    .map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull)
                    .anyMatch(candidate -> unlockPasses(phase, candidate, villager, data, state));
        }
        Entity context = villager != null ? villager : resolveSponsor(player.getServer(), state);
        if (context == null) {
            return false;
        }
        PlayerQuestData pdata = QuestCapabilities.get(player).orElse(null);
        if (pdata == null) {
            return false;
        }
        return phase.unlock().get().test(new QuestContext(player, context, pdata, state.projectId()));
    }

    private static void broadcastToast(MinecraftServer server, ProjectState state, ProjectDefinition def, int phase) {
        Component title = def.displayTitle();
        Component phaseLabel = phaseLabel(def, phase);
        for (UUID uuid : state.participants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                PacketDistributor.sendToPlayer(p,
                        new ProjectPhaseToastS2CPacket(title, phaseLabel));
                syncProjects(p);
            }
        }
    }

    /**
     * Applies a project reputation outcome to its recipients.
     *
     * <p>Kept as a thin call-through so the several completion paths in this class read the same, but
     * the work — and in particular the per-recipient split that replaced the old anonymous award — is
     * {@link ProjectReputation}'s (§29.4).
     */
    static void addReputation(MinecraftServer server, ProjectState state, ProjectDefinition def,
                              dev.otectus.mcaquests.quest.reputation.ReputationOutcome outcome,
                              String outcomeKey, int phaseIndex) {
        ProjectReputation.apply(server, server.overworld(), state, def, outcome, outcomeKey, phaseIndex);
    }

    /** Seeds a follow-up project in the same scope identity. Public for the reward distributor. */
    public static void seedFollowUp(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                    ProjectState from, ResourceLocation targetId) {
        ProjectDefinition target = ProjectRegistry.get(targetId).orElse(null);
        if (target == null && UnavailableContent.contains(UnavailableContent.Kind.PROJECT, targetId)) {
            // Its optional mod is missing. Remember the debt and seed it once the project loads, rather
            // than dropping a follow-up the village earned (1.7.0).
            if (from.deferredFollowUps().add(targetId)) {
                data.setDirty();
            }
            return;
        }
        if (target == null || !target.enabled() || target.phases().isEmpty()) {
            return;
        }
        if (target.scopeType() != from.scope()) {
            McaQuests.LOGGER.warn("[MCA: Quests] cannot seed '{}' from '{}': follow-ups must share a scope",
                    targetId, from.projectId());
            return;
        }
        ProjectInstanceKey key = new ProjectInstanceKey(targetId, from.scope(), from.identity());
        if (data.getInstance(key).isPresent()) {
            return;
        }
        ProjectState seeded = new ProjectState(targetId, from.scope(), from.identity(), from.anchorDimension(),
                from.anchorPos(), from.villageId(), level.getGameTime(), target.phase(0).objectives().size());
        seeded.setStartDayTime(level.getDayTime());
        seeded.sampleClock(level.getGameTime(), false);
        seeded.freezeAnchorRadius(target.scope().fallbackRadiusOr(fallbackRadius()));
        from.sponsors().forEach(seeded::addSponsor);
        ServerLevel home = server.getLevel(dimensionKey(from.anchorDimension()));
        ProjectPhases.begin(server, home != null ? home : level, target, seeded);
        data.putInstance(seeded);
        from.deferredFollowUps().remove(targetId);
    }

    // ---------------------------------------------------------------- event-driven credit

    public static void onProjectKill(ServerPlayer player, Entity killed) {
        creditEvent(player, killed.blockPosition(), (state, def, phase, i, where, capped) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (!capped && objective instanceof ProjectKillObjective kill && kill.matches(killed)
                    && where.within(kill.borderMargin())) {
                credit(def, state, i, player, 1);
                return true;
            }
            return false;
        });
    }

    public static void onProjectPlace(ServerPlayer player, BlockState placed, BlockPos pos) {
        PlacementFeedback feedback = new PlacementFeedback();
        boolean credited = creditEvent(player, pos, (state, def, phase, i, where, capped) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (!(objective instanceof ProjectPlaceBlockObjective place) || !place.matches(placed)) {
                return false;
            }
            if (!where.within(place.borderMargin())) {
                feedback.offer(PlacementFeedback.Reason.OUTSIDE, state, def,
                        geometry(where.level(), state, place.borderMargin()).blocksOutside(pos));
                return false;
            }
            if (capped) {
                feedback.offer(PlacementFeedback.Reason.LIMIT, state, def, 0);
                return false;
            }
            if (!state.progress(i).markPlaced(pos)) {
                feedback.offer(PlacementFeedback.Reason.ALREADY_COUNTED, state, def, 0);
                return false;
            }
            credit(def, state, i, player, 1);
            return true;
        });
        if (!credited) {
            collectInactivePlacement(player, placed, pos, feedback);
            feedback.send(player);
        }
    }

    /**
     * Explains a relevant placement that could not count because of <em>when</em> or <em>where</em> it
     * happened rather than what it was: the material belongs to a later phase of a project in whose
     * area the player is building, or to a project the player is helping in another dimension.
     * Checked only after nothing was credited, so an ordinary placement costs one list walk.
     */
    private static void collectInactivePlacement(ServerPlayer player, BlockState placed, BlockPos pos,
                                                 PlacementFeedback feedback) {
        if (!enabled() || !(player.level() instanceof ServerLevel level) || player.getServer() == null
                || feedback.hasReason()) {
            return;
        }
        for (ProjectState state : ProjectSavedData.get(player.getServer()).allInstances()) {
            if (state.status() != ProjectStatus.ACTIVE) {
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
            if (def == null || state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
                continue;
            }
            boolean sameDimension = state.anchorDimension().equals(level.dimension().location());
            for (int p = 0; p < def.phaseCount(); p++) {
                for (ProjectObjective objective : def.phase(p).objectives()) {
                    if (!(objective instanceof ProjectPlaceBlockObjective place) || !place.matches(placed)) {
                        continue;
                    }
                    if (!sameDimension) {
                        if (p == state.currentPhase() && state.participants().contains(player.getUUID())) {
                            feedback.offer(PlacementFeedback.Reason.WRONG_DIMENSION, state, def, 0);
                        }
                    } else if (p != state.currentPhase() && inScopeAt(level, state, pos, place.borderMargin())) {
                        feedback.offerPhase(state, def, p);
                    }
                }
            }
        }
    }

    /** Project talk counts a resident of the bound village anywhere, or anyone within MCA's villager margin. */
    static final int TALK_VILLAGE_MARGIN = 48;

    /**
     * Credits one conversation with {@code villager} to every active project's {@code project_talk_to_profession}
     * objectives. Counts distinct villagers only — the villager UUID is recorded on the shared progress, so
     * re-talking to the same villager never advances the objective again, and the credit is idempotent if
     * several routes report the same conversation.
     *
     * <p>Who counts (1.7.0): a village-bound project counts a <b>resident</b> of its village wherever the
     * conversation happens, and otherwise anyone standing inside the village's area with MCA's own
     * villager margin. Before this, the villager had to be inside the box of registered buildings at the
     * moment of the conversation, so a librarian out in the fields did not count.
     */
    public static void onProjectTalk(ServerPlayer player, Entity villager) {
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        UUID villagerUuid = villager.getUUID();
        OptionalInt home = McaCompat.getHomeVillageId(villager);
        creditEvent(player, villager.blockPosition(), (state, def, phase, i, where, capped) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (!(objective instanceof ProjectTalkObjective talk) || capped) {
                return false;
            }
            if (!talk.matches(profession)) {
                debugReject(def, "profession mismatch (wanted " + talk.profession() + ", villager is "
                        + profession + ")");
                return false;
            }
            if (!talkCounts(state, home, where)) {
                debugReject(def, "villager " + villagerUuid + " is neither a resident nor inside the village");
                return false;
            }
            if (!state.progress(i).markTalkedTo(villagerUuid)) {
                debugReject(def, "duplicate villager " + villagerUuid + " — already counted");
                return false;
            }
            credit(def, state, i, player, 1);
            return true;
        });
    }

    /** Whether a conversation with a villager whose home village is {@code home} counts for {@code state}. */
    static boolean talkCounts(ProjectState state, OptionalInt home, EventSite where) {
        if (state.villageId().isPresent()) {
            return (home.isPresent() && home.getAsInt() == state.villageId().getAsInt())
                    || where.within(TALK_VILLAGE_MARGIN);
        }
        return where.within(0);
    }

    private static void debugReject(ProjectDefinition def, String reason) {
        debugLog("project '{}' did not credit a conversation: {}", def.id(), reason);
    }

    /** Verbose per-contribution tracing, gated behind {@code debugLogging} so it costs nothing when off. */
    private static void debugLog(String message, Object... args) {
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] " + message, args);
        }
    }

    /** Where one event happened, relative to one instance; the scope test is memoised per margin. */
    static final class EventSite {
        private final ServerLevel level;
        private final ProjectState state;
        private final BlockPos pos;
        private final Map<Integer, Boolean> within = new HashMap<>(2);

        EventSite(ServerLevel level, ProjectState state, BlockPos pos) {
            this.level = level;
            this.state = state;
            this.pos = pos;
        }

        ServerLevel level() {
            return level;
        }

        boolean within(int margin) {
            return within.computeIfAbsent(margin, m -> inScopeAt(level, state, pos, m));
        }
    }

    private interface ObjectiveCredit {
        /**
         * @param capped true when the player has already reached this objective's per-player cap; the
         *               credit is refused either way, but a placement can still explain why
         */
        boolean apply(ProjectState state, ProjectDefinition def, int phase, int objectiveIndex, EventSite where,
                      boolean capped);
    }

    /** Returns true when anything was credited. */
    private static boolean creditEvent(ServerPlayer player, BlockPos where, ObjectiveCredit credit) {
        if (!enabled() || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            debugLog("no server for player {} — contribution dropped", player.getGameProfile().getName());
            return false;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
        boolean anyCredited = false;
        for (ProjectState state : data.allInstances()) {
            if (state.status() != ProjectStatus.ACTIVE) {
                debugLog("project '{}' is {}, not ACTIVE", state.projectId(), state.status());
                continue;
            }
            if (isScopeStale(state)) {
                debugLog("project '{}' instance is quarantined: saved scope {} no longer matches the pack",
                        state.projectId(), state.scope());
                continue;
            }
            if (!state.anchorDimension().equals(level.dimension().location())) {
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
            if (def == null) {
                debugLog("project '{}' has no loaded definition", state.projectId());
                continue;
            }
            if (state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
                debugLog("project '{}' phase {} is past its last phase ({})", state.projectId(),
                        state.currentPhase(), def.phaseCount());
                continue;
            }
            ProjectPhase phase = def.phase(state.currentPhase());
            if (phase.objectives().stream().noneMatch(ProjectObjective::isEventDriven)) {
                continue;
            }
            if (checkProjectFailure(server, level, data, state, def)) {
                dirty = true;
                continue;
            }
            EventSite site = new EventSite(level, state, where);
            boolean changed = false;
            for (int i = 0; i < phase.objectives().size(); i++) {
                ProjectObjective objective = phase.objectives().get(i);
                if (!objective.isEventDriven() || objective.isSatisfied(state.progress(i))) {
                    continue;
                }
                int cap = objective.perPlayerCap() > 0 ? objective.perPlayerCap()
                        : McaQuestsConfig.COMMON.defaultPerPlayerContributionCap.get();
                boolean capped = cap > 0 && state.progress(i).contributionOf(player.getUUID()) >= cap;
                if (credit.apply(state, def, state.currentPhase(), i, site, capped)) {
                    changed = true;
                }
            }
            if (changed) {
                anyCredited = true;
                checkPhaseAdvance(server, level, data, state, def, player, null);
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
            server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
            ProjectMenuSessions.refreshAll(server);
        }
        return anyCredited;
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
        NeoForge.EVENT_BUS.post(new ProjectEvent.Contributed(def, state, player, objectiveIndex, amount));
    }

    /**
     * True when a saved instance's scope no longer matches its definition's current {@code scope} — i.e.
     * the pack changed the project's scope after this instance was created.
     *
     * <p>Every by-key lookup (menu, offers, contribution) builds its key from {@code def.scopeType()}, so
     * such an instance can never be found, shown, or resumed again, and a fresh instance is created
     * alongside it. Left unchecked it would still be reached through {@code allInstances()} and keep
     * accruing progress and paying out phase rewards in parallel with its replacement — a double payout.
     *
     * <p>So it is <b>quarantined, not deleted</b>: it stops accruing and stops paying, but its data stays
     * in the save. Reverting the pack's {@code scope} brings it back exactly as it was. Admins can still
     * see it via {@code /mcaquests project} and clear it with {@code adminReset}.
     */
    public static boolean isScopeStale(ProjectState state) {
        return ProjectRegistry.get(state.projectId())
                .map(def -> def.scopeType() != state.scope())
                .orElse(false); // unknown definition — a missing datapack, not a scope change
    }

    private static boolean inScopeAt(ServerLevel level, ProjectState state, BlockPos where) {
        return inScopeAt(level, state, where, 0);
    }

    /**
     * Whether {@code where} counts for this instance, with {@code margin} blocks of allowance beyond a
     * village's registered buildings. The one predicate server credit uses, and exactly what
     * {@link #geometry} draws for the player.
     */
    public static boolean inScopeAt(ServerLevel level, ProjectState state, BlockPos where, int margin) {
        return state.anchorDimension().equals(level.dimension().location())
                && ScopeResolver.isWithinScope(level, state.scope(), state.villageId(), state.anchorPos(),
                        anchorRadius(state), margin, where);
    }

    /**
     * The anchor radius this instance was created with. Instances from before 1.7.0 never stored one and
     * were tested against the global {@code defaultScopeFallbackRadius} — not their definition's own
     * override — so that is the value they keep; {@link #pollProjects} freezes it on first sight.
     */
    public static int anchorRadius(ProjectState state) {
        return state.anchorRadius().orElseGet(ProjectManager::fallbackRadius);
    }

    /** What {@link #inScopeAt} tests, as geometry a player can be shown. */
    public static ScopeGeometry geometry(ServerLevel level, ProjectState state, int margin) {
        ResourceLocation dimension = state.anchorDimension();
        if (state.villageId().isEmpty()) {
            return ScopeGeometry.anchorRadius(dimension, state.anchorPos(), anchorRadius(state));
        }
        int village = state.villageId().getAsInt();
        BlockPos center = McaCompat.villageCenter(level, village).orElse(state.anchorPos());
        return McaCompat.villageBox(level, village)
                .map(box -> ScopeGeometry.villageBox(dimension, center, box, margin))
                .orElseGet(() -> ScopeGeometry.villageApproximate(dimension, center, anchorRadius(state), margin));
    }

    /**
     * The widest positional allowance any objective of the instance's current phase asks for, which is
     * the area a player building for this phase needs to see. Zero when the phase has none.
     */
    public static int currentPhaseMargin(ProjectDefinition def, ProjectState state) {
        if (state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
            return 0;
        }
        int margin = 0;
        for (ProjectObjective objective : def.phase(state.currentPhase()).objectives()) {
            if (objective instanceof ProjectPlaceBlockObjective place) margin = Math.max(margin, place.borderMargin());
            if (objective instanceof ProjectKillObjective kill) margin = Math.max(margin, kill.borderMargin());
        }
        return margin;
    }

    /** True when the current phase has any objective credited by where something happens. */
    public static boolean hasPositionalWork(ProjectDefinition def, int phase) {
        if (phase < 0 || phase >= def.phaseCount()) {
            return false;
        }
        return def.phase(phase).objectives().stream().anyMatch(objective ->
                objective instanceof ProjectPlaceBlockObjective || objective instanceof ProjectKillObjective);
    }

    // ---------------------------------------------------------------- sponsor loss

    /** Fails an expired shared project before accepting another contribution or paying a phase. */
    private static boolean checkProjectFailure(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                               ProjectState state, ProjectDefinition def) {
        if (state.status() != ProjectStatus.ACTIVE || def.failure().isEmpty()
                || state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
            return false;
        }
        ProjectPhase phase = def.phase(state.currentPhase());
        if (phase.objectives().stream().anyMatch(objective -> !objective.isAvailable(level, state))) {
            return false;
        }
        for (int i = 0; i < phase.objectives().size(); i++) {
            if (phase.objectives().get(i) instanceof PollingProjectObjective polling
                    && polling.isPending(state, state.progress(i))) {
                return false; // paused waiting for a reading, not late
            }
        }
        if (state.currentPhase() == def.phaseCount() - 1 && phaseSatisfied(state, phase)) {
            return false; // the final work was completed in time
        }
        var failure = def.failure().get();
        state.initializeFailureClock(level.getGameTime(), level.getDayTime());
        if (ProjectFailure.deadlinePassed(state, failure, level.getGameTime(), level.getDayTime())
                || failure.requireWeather().filter(weather -> !weather.matches(level)).isPresent()) {
            failProject(server, level, data, state, def);
            return true;
        }
        return false;
    }

    private static void failProject(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                     ProjectState state, ProjectDefinition def) {
        if (state.status().isTerminal()) {
            return;
        }
        state.setStatus(ProjectStatus.FAILED);
        def.failure().ifPresent(failure -> {
            state.allowRetryAt(ProjectFailure.retryAt(failure, level.getGameTime()));
            if (failure.failureHearts() != 0) {
                for (UUID sponsor : state.sponsors()) {
                    for (UUID participant : state.participants()) {
                        ServerPlayer online = server.getPlayerList().getPlayer(participant);
                        if (online == null) {
                            dev.otectus.mcaquests.state.PendingHeartsData.get(server)
                                    .queue(sponsor, participant, failure.failureHearts());
                        } else {
                            McaCompat.awardHearts(level, sponsor, online, failure.failureHearts());
                        }
                    }
                }
            }
        });
        addReputation(server, state, def, def.reputation().failOutcome(), "fail", -1);
        data.setDirty();
        NeoForge.EVENT_BUS.post(new ProjectEvent.Failed(def, state));
        for (UUID participant : state.participants()) {
            ServerPlayer online = server.getPlayerList().getPlayer(participant);
            if (online != null) {
                online.sendSystemMessage(Component.translatable("mcaquests.message.project_failed", def.displayTitle()));
                syncProjects(online);
            }
        }
    }

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
            ProjectDefinition definition = ProjectRegistry.get(state.projectId()).orElse(null);
            if (state.hasSponsor(villagerUuid) && definition != null
                    && definition.failure().map(failure -> failure.failOnGiverDeath()
                            || failure.failOnTargetLost()).orElse(false)) {
                ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
                if (level != null) {
                    failProject(server, level, data, state, definition);
                    dirty = true;
                    continue;
                }
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
            server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
        }
    }

    private static void applySponsorLoss(MinecraftServer server, ProjectSavedData data, ProjectState state,
                                         ProjectDefinition def) {
        var behavior = def.sponsor().onDeathOr(McaQuestsConfig.COMMON.defaultSponsorDeathBehavior.get());
        switch (behavior) {
            case FAIL -> {
                ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
                failProject(server, level != null ? level : server.overworld(), data, state, def);
            }
            case PAUSE -> state.setStatus(ProjectStatus.PAUSED);
            case TRANSFER -> {
                if (!tryTransfer(server, state, def)) {
                    state.setStatus(ProjectStatus.PAUSED);
                }
            }
            case TURN_IN_TO_VILLAGE -> {
                addReputation(server, state, def, def.reputation().completeOutcome(), "complete", -1);
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
        NeoForge.EVENT_BUS.post(new ProjectEvent.Completed(def, state));
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
            if (state.status() != ProjectStatus.ACTIVE || !state.participants().contains(uuid)
                    || isScopeStale(state)) {
                continue; // a quarantined instance is no longer reachable, so don't list it as active
            }
            Optional<ProjectDefinition> loaded = ProjectRegistry.get(state.projectId());
            if (loaded.isEmpty()) {
                // Not loaded because an optional mod it needs is missing: still the player's project,
                // paused and named, never silently gone (1.7.0).
                UnavailableContent.get(UnavailableContent.Kind.PROJECT, state.projectId()).ifPresent(missing ->
                        entries.add(new ProjectLogEntry(state.projectId(), missing.title(),
                                sponsorLogLabel(player, state), Component.empty(),
                                Component.translatable("mcaquests.label.project.phase", state.currentPhase() + 1, "?"),
                                List.of(), state.key().asString(), Optional.of(missing.why().reason()))));
                continue;
            }
            loaded.filter(def -> state.currentPhase() >= 0 && state.currentPhase() < def.phaseCount())
                    .ifPresent(def -> entries.add(new ProjectLogEntry(
                    state.projectId(), def.displayTitle(), sponsorLogLabel(player, state),
                    scopeLabel(def), phaseLabel(def, state.currentPhase()),
                    objectiveLines(player, def, state, state.currentPhase(), false), state.key().asString(),
                    Optional.empty())));
        }
        PacketDistributor.sendToPlayer(player, new ProjectLogSyncS2CPacket(entries));
    }

    private static Component sponsorLogLabel(ServerPlayer player, ProjectState state) {
        ServerLevel level = player.getServer() == null ? null
                : player.getServer().getLevel(dimensionKey(state.anchorDimension()));
        if (state.villageId().isPresent() && level != null) {
            Optional<String> name = McaCompat.villageName(level, state.villageId().getAsInt());
            if (name.isPresent()) {
                return Component.translatable("mcaquests.label.project.village", name.get());
            }
        }
        return Component.empty();
    }

    /**
     * Delivers any phase rewards owed to a returning player (login), and retries any banked FTB-claim
     * rewards (village_reputation / hearts / grant_title with no resolvable target at claim time —
     * spec 1.0.0 §16, task M3.1) against the player's current surroundings. Called on login
     * ({@code ProjectLifecycleEvents.onPlayerLogin}) and once per in-game day while online
     * ({@code QuestProgressEvents}'s throttled per-player tick). {@code drainPending} removes the whole
     * owed list from storage up front; a banked entry that still can't resolve is re-queued in this same
     * server-thread pass, so the only loss window matches legacy pending delivery's own — a crash between
     * the drain and the (re-)grant, before the next autosave.
     */
    public static void deliverPending(ServerPlayer player) {
        if (player.getServer() == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = player.getServer();
        // Banked FTB-claim rewards are debts the player already earned outside the project system, so
        // they are paid even when village projects are switched off. Phase rewards are not delivered
        // then, but they are never discarded either.
        boolean projectsEnabled = enabled();
        ProjectSavedData data = ProjectSavedData.get(server);
        UUID playerId = player.getUUID();
        List<PendingReward> owed = data.drainPending(playerId);
        long heldBefore = owed.stream().filter(ProjectManager::isHeld).count();
        List<PendingReward> retained = drainPass(playerId, owed, projectsEnabled,
                reward -> deliverOne(server, level, player, reward));
        retained.forEach(reward -> data.addPending(playerId, reward));
        if (retained.stream().filter(ProjectManager::isHeld).count() > heldBefore) {
            player.sendSystemMessage(Component.translatable("mcaquests.reward.pending.held"));
        }
        if (projectsEnabled) {
            syncProjects(player);
        }
    }

    /**
     * How many failed delivery passes an owed reward gets before it is held for an operator. A reward
     * that fails identically every login is not going to start working on the fourth try, and the log
     * line it produces each time is worse than the missing reward.
     */
    static final int MAX_DELIVERY_ATTEMPTS = 3;

    /** Held: retained in storage, visible to {@code /mcaquests project pending}, never retried on its own. */
    public static boolean isHeld(PendingReward reward) {
        return reward.attempts() >= MAX_DELIVERY_ATTEMPTS;
    }

    /** One attempt at a single owed reward. */
    @FunctionalInterface
    interface PendingDelivery {
        ProjectRewardDistributor.DeliveryOutcome deliver(PendingReward reward);
    }

    /**
     * Runs one delivery pass over an already-drained owed list and returns the entries that must go
     * back into storage. Pure apart from {@code delivery} itself, so the retention rules are testable
     * without a server.
     */
    static List<PendingReward> drainPass(UUID playerId, List<PendingReward> owed, boolean projectsEnabled,
                                         PendingDelivery delivery) {
        List<PendingReward> retained = new ArrayList<>();
        int failures = 0;
        for (PendingReward reward : owed) {
            if (isHeld(reward) || (!projectsEnabled && reward.kind() != PendingReward.Kind.BANKED)) {
                retained.add(reward); // held, or projects are off: keep it, do not pay it, do not drop it
                continue;
            }
            ProjectRewardDistributor.DeliveryOutcome outcome;
            try {
                outcome = delivery.deliver(reward);
            } catch (RuntimeException | LinkageError failure) {
                // Anything escaping the delivery call comes from resolution — a corrupt nested snapshot,
                // one broken add-on — because the grant itself reports FAILED_UNKNOWN rather than throw.
                McaQuests.LOGGER.warn("[MCA: Quests] unreadable pending reward for {}", playerId, failure);
                outcome = ProjectRewardDistributor.DeliveryOutcome.FAILED_UNAPPLIED;
            }
            switch (outcome) {
                case DELIVERED -> {
                    // paid: the entry is settled and does not go back into storage
                }
                case DEFERRED -> retained.add(reward);
                case FAILED_UNAPPLIED -> {
                    failures++;
                    retained.add(reward.withAttempts(reward.attempts() + 1));
                }
                case FAILED_UNKNOWN -> {
                    failures++;
                    retained.add(reward.withAttempts(MAX_DELIVERY_ATTEMPTS)); // may have partly paid
                }
            }
        }
        if (failures > 0) {
            // One line per pass, not per entry: a broken add-on affects every reward it touches.
            McaQuests.LOGGER.warn("[MCA: Quests] kept {} undeliverable pending reward(s) for {}",
                    failures, playerId);
        }
        return retained;
    }

    /**
     * True when an owed phase reward's project is only unloaded because an optional mod it needs is
     * missing. That is not a failed delivery and must not use up the retry allowance: the debt waits for
     * the mod, and is paid once when it returns (1.7.0).
     */
    static boolean waitsForOptionalMod(PendingReward reward) {
        return reward.kind() == PendingReward.Kind.PROJECT_PHASE && reward.projectId() != null
                && UnavailableContent.contains(UnavailableContent.Kind.PROJECT, reward.projectId());
    }

    private static ProjectRewardDistributor.DeliveryOutcome deliverOne(MinecraftServer server, ServerLevel level,
                                                                       ServerPlayer player, PendingReward reward) {
        if (reward.kind() == PendingReward.Kind.BANKED) {
            if (!ProjectRewardDistributor.attemptBankedDelivery(server, level, player, reward.banked())) {
                // Not a failure: no village or villager in range yet. Retried indefinitely, as designed.
                return ProjectRewardDistributor.DeliveryOutcome.DEFERRED;
            }
            player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.banked_delivered"));
            return ProjectRewardDistributor.DeliveryOutcome.DELIVERED;
        }
        ProjectDefinition def = ProjectRegistry.get(reward.projectId()).orElse(null);
        if (def == null && waitsForOptionalMod(reward)) {
            return ProjectRewardDistributor.DeliveryOutcome.DEFERRED;
        }
        if (def == null || reward.phase() < 0 || reward.phase() >= def.phaseCount()
                || reward.rewardIndex() < 0
                || reward.rewardIndex() >= def.phase(reward.phase()).rewards().size()) {
            return ProjectRewardDistributor.DeliveryOutcome.FAILED_UNAPPLIED;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        List<ProjectState> candidates = reward.instanceSnapshot() != null
                ? java.util.stream.Stream.of(ProjectState.load(reward.instanceSnapshot()))
                        .filter(state -> reward.matchesInstance(state, player.getUUID())).toList()
                : data.allInstances().stream()
                .filter(s -> reward.matchesInstance(s, player.getUUID()) && !isScopeStale(s)).toList();
        if (candidates.size() != 1) {
            // Old saves omitted instance identity. Ambiguous or unavailable payouts stay banked;
            // choosing the first village would pay another village's amount and sponsor reward.
            return ProjectRewardDistributor.DeliveryOutcome.FAILED_UNAPPLIED;
        }
        return ProjectRewardDistributor.grantPending(level, candidates.get(0), player, def,
                reward.phase(), reward.rewardIndex());
    }

    /**
     * Operator recovery for {@code /mcaquests project pending <player> retry}: clears the attempt count
     * on everything this player is owed, so the next delivery pass tries them all again. Returns how
     * many entries were reset.
     */
    public static int resetPendingAttempts(MinecraftServer server, UUID playerId) {
        ProjectSavedData data = ProjectSavedData.get(server);
        List<PendingReward> owed = data.drainPending(playerId);
        owed.forEach(reward -> data.addPending(playerId, reward.withAttempts(0)));
        return owed.size();
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

    /**
     * Force-advances every non-terminal instance of {@code projectId} by one phase, paying nothing. Kept
     * for tests and for the explicit {@code advance <id> all} bulk form; the command refuses a bare id that
     * matches more than one instance (1.7.0). Uses {@link #adminSkipPhase}, so the next phase's baselines
     * are taken and a finished project is marked complete without a payout.
     */
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
            ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
            adminSkipPhase(server, level != null ? level : server.overworld(), data, state, def, false);
            advanced++;
        }
        return advanced;
    }

    /**
     * One bounded sweep of every live project, advancing the objectives that have to watch the world
     * rather than wait to be told about it (Townstead spec 5.4).
     *
     * <p>Deliberately server-wide and player-independent. A project is village state, not player state:
     * a dock finished while its sponsor was logged out is still finished, and tying the check to a
     * nearby player would make completion depend on who happened to be standing where.
     *
     * <p>Guarded by the same eligibility ladder {@code creditEvent} uses -- status, stale scope, missing
     * definition, phase bounds -- so a quarantined or finished project costs one comparison.
     */
    public static void pollProjects(MinecraftServer server) {
        boolean projectsEnabled = enabled();
        TownsteadCounters.projectPoll();
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
        for (ProjectState state : data.allInstances()) {
            dirty |= pollOne(server, data, state, projectsEnabled);
        }
        if (dirty) {
            data.setDirty();
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                syncProjects(online);
            }
            ProjectMenuSessions.refreshAll(server);
        }
    }

    /**
     * One instance's share of the sweep: exactly what {@link #pollProjects} does for it, and what an
     * operator's {@code recheck} runs for one instance on demand. Returns true when anything a player can
     * see changed. It reads current state and settles a phase that is genuinely satisfied through the
     * normal path; it never resets a baseline, moves an item or pays anything out of turn.
     */
    public static boolean pollOne(MinecraftServer server, ProjectSavedData data, ProjectState state,
                                  boolean projectsEnabled) {
        boolean dirty = false;
        if (state.status().isTerminal()) {
            if (projectsEnabled && !state.deferredFollowUps().isEmpty()) {
                dirty = retryDeferredFollowUps(server, data, state);
            }
            return dirty;
        }
        if (state.freezeAnchorRadius(fallbackRadius())) {
            // Saved before 1.7.0: freeze the radius it was actually being tested against.
            dirty = true;
        }
        ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
        ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
        boolean unavailable = !projectsEnabled || state.status() != ProjectStatus.ACTIVE || isScopeStale(state)
                || def == null || !def.enabled() || level == null || state.currentPhase() < 0
                || state.currentPhase() >= def.phaseCount();
        if (!unavailable) {
            // Readings a phase could not take at its boundary are retried before anything else, so a
            // pending baseline resolves the moment its source can be read.
            dirty |= ProjectPhases.resolvePending(server, level, def, state);
            var objectives = def.phase(state.currentPhase()).objectives();
            for (int i = 0; i < objectives.size() && !unavailable; i++) {
                ProjectObjective objective = objectives.get(i);
                unavailable = !objective.isAvailable(level, state)
                        || (objective instanceof PollingProjectObjective polling
                                && polling.isPending(state, state.progress(i)));
            }
        }
        state.sampleClock(server.overworld().getGameTime(), unavailable);
        data.setDirty();
        if (unavailable) {
            return dirty;
        }
        if (checkProjectFailure(server, level, data, state, def)) {
            return true;
        }
        ProjectPhase phase = def.phase(state.currentPhase());
        boolean changed = false;
        for (int i = 0; i < phase.objectives().size(); i++) {
            if (phase.objectives().get(i) instanceof PollingProjectObjective polling
                    && polling.poll(server, level, def, state, state.progress(i))) {
                changed = true;
            }
        }
        if (changed || phaseSatisfied(state, phase)) {
            int previousPhase = state.currentPhase();
            ProjectStatus previousStatus = state.status();
            boolean distributed = state.isPhaseDistributed(previousPhase);
            checkPhaseAdvance(server, level, data, state, def, null, null);
            dirty |= changed || previousPhase != state.currentPhase() || previousStatus != state.status()
                    || distributed != state.isPhaseDistributed(previousPhase);
        }
        return dirty;
    }

    /**
     * Operator repair: moves one instance past its current phase (1.7.0). A skip is not proof the work
     * happened, so by default ({@code normalRewards == false}) the phase is marked settled without paying
     * anything, awarding reputation or posting a completion; with {@code normalRewards} the phase settles
     * through exactly the path a finished phase takes, guarded by the same one-shot distribution flag, so
     * repeating a skip can never pay twice. Earlier-earned and queued rewards are untouched either way.
     * The next phase is entered through {@link ProjectPhases}, so its baselines are taken as usual; a
     * follow-up is seeded as it would be on completion, because that is story, not payment.
     */
    public static void adminSkipPhase(MinecraftServer server, ServerLevel level, ProjectSavedData data,
                                      ProjectState state, ProjectDefinition def, boolean normalRewards) {
        int current = state.currentPhase();
        if (state.tryMarkPhaseDistributed(current) && normalRewards) {
            ProjectRewardDistributor.distribute(server, level, data, state, def, current);
            ProjectReputation.apply(server, level, state, def, def.reputation().phaseOutcome(), "phase", current);
            NeoForge.EVENT_BUS.post(new ProjectEvent.PhaseAdvanced(def, state, current));
            broadcastToast(server, state, def, current);
        }
        int next = current + 1;
        if (next < def.phaseCount()) {
            ProjectPhases.enter(server, level, def, state, next);
        } else {
            state.setStatus(ProjectStatus.COMPLETED);
            if (normalRewards) {
                ProjectReputation.apply(server, level, state, def, def.reputation().completeOutcome(), "complete", -1);
                completeProject(server, def, state);
            }
            def.followUp().ifPresent(target -> seedFollowUp(server, level, data, state, target));
        }
        state.bumpRevision();
        data.setDirty();
        server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
        ProjectMenuSessions.refreshAll(server);
    }

    /**
     * Seeds follow-ups this finished project earned while their optional mod was missing, now that they
     * load. Returns true when anything was seeded.
     */
    private static boolean retryDeferredFollowUps(MinecraftServer server, ProjectSavedData data, ProjectState state) {
        boolean seeded = false;
        ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
        for (ResourceLocation target : List.copyOf(state.deferredFollowUps())) {
            if (ProjectRegistry.get(target).isPresent()) {
                seedFollowUp(server, level != null ? level : server.overworld(), data, state, target);
                seeded = true;
            }
        }
        return seeded;
    }

    /** Players may ask where a project's work counts from this far from its anchor, outside its roll. */
    static final int BUILD_AREA_REQUEST_DISTANCE = 256;

    /**
     * Answers "show me the build area" for one instance (1.7.0): the geometry {@link #inScopeAt} tests
     * for the current phase's positional work, the village's name and dimension, and what counts there.
     * Only a participant or a player near the project is answered, so a request cannot be used to find
     * other people's villages.
     */
    public static void sendBuildArea(ServerPlayer player, String instanceKey) {
        MinecraftServer server = player.getServer();
        if (!enabled() || server == null) {
            return;
        }
        ProjectState state = ProjectSavedData.get(server).getInstance(instanceKey).orElse(null);
        if (state == null || state.status().isTerminal()) {
            return;
        }
        ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
        ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
        if (def == null || level == null || state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
            return;
        }
        boolean near = player.level() == level && state.anchorPos().distSqr(player.blockPosition())
                <= (long) BUILD_AREA_REQUEST_DISTANCE * BUILD_AREA_REQUEST_DISTANCE;
        if (!state.participants().contains(player.getUUID()) && !near) {
            return;
        }
        int margin = currentPhaseMargin(def, state);
        ScopeGeometry geometry = geometry(level, state, margin);
        Component village = (state.villageId().isPresent()
                ? McaCompat.villageName(level, state.villageId().getAsInt()) : Optional.<String>empty())
                .<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("mcaquests.project.help.this_village"));
        String key = switch (geometry.shape()) {
            case VILLAGE_BOX -> "mcaquests.project.buildarea.summary";
            case VILLAGE_APPROXIMATE -> "mcaquests.project.buildarea.summary_approximate";
            case ANCHOR_RADIUS -> "mcaquests.project.buildarea.summary_anchor";
        };
        Component summary = Component.translatable(key, village, Component.literal(state.anchorDimension().toString()),
                margin, geometry.anchor().getX(), geometry.anchor().getZ(), geometry.radius());
        List<Component> materials = new ArrayList<>();
        for (ProjectObjective objective : def.phase(state.currentPhase()).objectives()) {
            if (objective instanceof ProjectPlaceBlockObjective place) {
                materials.add(Component.translatable("mcaquests.project.buildarea.place", place.target().describe()));
            } else if (objective instanceof ProjectKillObjective kill) {
                materials.add(Component.translatable("mcaquests.project.buildarea.kill", kill.target().describe()));
            }
        }
        PacketDistributor.sendToPlayer(player,
                new ProjectScopeS2CPacket(def.displayTitle(), summary, geometry, materials));
    }

    public static List<ProjectState> activeInstances(MinecraftServer server) {
        return new ArrayList<>(ProjectSavedData.get(server).allInstances());
    }

    public static int reputationOf(MinecraftServer server, String identity) {
        // Project scope identities are not per-player standing; the shared value is gone, and the
        // honest answer for a scope query is 0 (see QuestReputation for the per-player reads).
        return 0;
    }

    // ---------------------------------------------------------------- debug

    /** Human-readable explanation of why {@code projectId} is or is not available from {@code villager}. */
    public static List<Component> explainAvailability(ServerPlayer player, Entity villager, ResourceLocation projectId) {
        List<Component> out = new ArrayList<>();
        ProjectDefinition def = ProjectRegistry.get(projectId).orElse(null);
        if (def == null) {
            out.add(UnavailableContent.get(UnavailableContent.Kind.PROJECT, projectId)
                    .map(missing -> Component.literal("Project '" + projectId + "' is not loaded: "
                            + missing.why().describe() + ". It is never offered on this installation."))
                    .orElseGet(() -> Component.literal("Unknown project '" + projectId + "'.")));
            return out;
        }
        out.add(Component.literal("Project " + projectId + " [" + def.scopeType().lower() + "]"));
        IntegrationRequirements.dependencies(def).forEach(integration -> out.add(line("needs "
                + integration.displayName(), IntegrationRequirements.unavailable(def).isEmpty())));
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
