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
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.objective.ProjectKillObjective;
import dev.otectus.mcaquests.project.objective.PollingProjectObjective;
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

    /**
     * Transient anti-spam gate: last accepted contribution game-time, per player <em>and project
     * instance</em>. Keyed by player alone, one contribution to the mill locked the player out of the
     * bridge, the granary and every other project for {@code projectContributeMinIntervalTicks}.
     */
    private static final Map<ContributionGate, Long> lastContributeTick = new HashMap<>();

    /** The scope one contribution rate-limit covers: this player, this project instance. */
    private record ContributionGate(UUID player, ProjectInstanceKey project) {
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
        if (def == null || def.phases().isEmpty() || !isEligibleSponsor(def, villager)) {
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
        if (interval > 0 && last != null && now - last < interval) {
            return;
        }
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
            lastContributeTick.put(gate, now);
        }
        checkPhaseAdvance(server, level, data, state, def, player, villager);
        data.setDirty();

        sendProjectMenu(player, villager);
        syncProjects(player);
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
            if (existing.isPresent()) {
                // Deliberately NOT re-tested against conditions. A project already under way must stay
                // reachable even once its gate has drifted from true — a winter project running into
                // spring — or the contributions players have already made are stranded with no way to
                // finish them. Conditions decide who may START a project, not who may finish one.
                if (!existing.get().status().isTerminal()) {
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
                sponsorLabel(def, villager, scope, state), phaseLabel(def, phaseIdx), dialogue,
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
        if (!TownsteadContentGate.allowsProject(def.id(), readsTownstead(def))) {
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
     * True when any phase of this project reads Townstead state, so the content switch knows whether it
     * applies. Derived from the objective types rather than from the id, so a project that stops using
     * Townstead stops being gated by it without anyone having to remember to rename the file.
     */
    private static boolean readsTownstead(ProjectDefinition def) {
        return def.phases().stream().flatMap(phase -> phase.objectives().stream())
                .anyMatch(objective -> objective.type().id().getPath().startsWith("townstead_"));
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
                ProjectReputation.apply(server, level, state, def, def.reputation().phaseOutcome(),
                        "phase", current);
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

    /**
     * Credits one conversation with {@code villager} to every active project's {@code project_talk_to_profession}
     * objectives. Counts distinct villagers only — the villager UUID is recorded on the shared progress, so
     * re-talking to the same villager never advances the objective again, and the credit is idempotent if
     * both the interaction hook and MCA: Conversations report the same conversation.
     */
    public static void onProjectTalk(ServerPlayer player, Entity villager) {
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        UUID villagerUuid = villager.getUUID();
        creditEvent(player, villager.blockPosition(), (state, def, phase, i) -> {
            ProjectObjective objective = def.phase(phase).objectives().get(i);
            if (!(objective instanceof ProjectTalkObjective talk)) {
                return false;
            }
            if (!talk.matches(profession)) {
                debugReject(def, "profession mismatch (wanted " + talk.profession() + ", villager is "
                        + profession + ")");
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

    private static void debugReject(ProjectDefinition def, String reason) {
        debugLog("project '{}' did not credit a conversation: {}", def.id(), reason);
    }

    /** Verbose per-contribution tracing, gated behind {@code debugLogging} so it costs nothing when off. */
    private static void debugLog(String message, Object... args) {
        if (McaQuestsConfig.COMMON.debugLogging.get()) {
            McaQuests.LOGGER.debug("[MCA: Quests] " + message, args);
        }
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
            debugLog("no server for player {} — contribution dropped", player.getGameProfile().getName());
            return;
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
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
            if (!inScopeAt(level, state, where)) {
                debugLog("project '{}' is out of scope at {} (scope={}, village={}, anchor={})",
                        state.projectId(), where, state.scope(), state.villageId(), state.anchorPos());
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
            if (def == null) {
                debugLog("project '{}' has no loaded definition", state.projectId());
                continue;
            }
            if (state.currentPhase() >= def.phaseCount()) {
                debugLog("project '{}' phase {} is past its last phase ({})", state.projectId(),
                        state.currentPhase(), def.phaseCount());
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
                addReputation(server, state, def, def.reputation().failOutcome(), "fail", -1);
                MinecraftForge.EVENT_BUS.post(new ProjectEvent.Failed(def, state));
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
            if (state.status() != ProjectStatus.ACTIVE || !state.participants().contains(uuid)
                    || isScopeStale(state)) {
                continue; // a quarantined instance is no longer reachable, so don't list it as active
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
        if (!enabled() || player.getServer() == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = player.getServer();
        ProjectSavedData data = ProjectSavedData.get(server);
        List<PendingReward> owed = data.drainPending(player.getUUID());
        for (PendingReward reward : owed) {
            if (reward.kind() == PendingReward.Kind.BANKED) {
                if (ProjectRewardDistributor.attemptBankedDelivery(server, level, player, reward.banked())) {
                    player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.banked_delivered"));
                } else {
                    data.addPending(player.getUUID(), reward); // still no target - stays banked
                }
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(reward.projectId()).orElse(null);
            if (def == null) {
                continue;
            }
            data.allInstances().stream()
                    .filter(s -> s.projectId().equals(reward.projectId()) && !isScopeStale(s))
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
        if (!enabled()) {
            return;
        }
        TownsteadCounters.projectPoll();
        ProjectSavedData data = ProjectSavedData.get(server);
        boolean dirty = false;
        for (ProjectState state : data.allInstances()) {
            if (state.status() != ProjectStatus.ACTIVE || isScopeStale(state)) {
                continue;
            }
            ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
            if (def == null || state.currentPhase() >= def.phaseCount()) {
                continue;
            }
            ServerLevel level = server.getLevel(dimensionKey(state.anchorDimension()));
            if (level == null) {
                continue; // the dimension is gone or not loaded; nothing to read
            }
            ProjectPhase phase = def.phase(state.currentPhase());
            boolean changed = false;
            for (int i = 0; i < phase.objectives().size() && i < state.progressCount(); i++) {
                if (phase.objectives().get(i) instanceof PollingProjectObjective polling
                        && polling.poll(server, level, def, state, state.progress(i))) {
                    changed = true;
                }
            }
            if (changed) {
                checkPhaseAdvance(server, level, data, state, def, null, null);
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                syncProjects(online);
            }
        }
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
