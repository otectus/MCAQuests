package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.objective.PollingProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjective;
import dev.otectus.mcaquests.project.objective.ProjectObjectiveContext;
import dev.otectus.mcaquests.project.objective.TownsteadSpiritProjectObjective;
import dev.otectus.mcaquests.project.scope.ScopeGeometry;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.IntegrationRequirements;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Operator diagnostics and repair for <b>one</b> project instance (1.7.0).
 *
 * <p>Before this, the only tools were {@code /mcaquests project advance <id>} and {@code reset <id>},
 * which acted on every instance of a project id in every village at once — advance by writing the phase
 * index directly, past settlement and past the next phase's baselines. Answering "my village's wall is
 * stuck" with either changed every other village running the same project.
 *
 * <p>Everything here names one instance. Reading ({@link #describe}) and rechecking
 * ({@link ProjectManager#pollOne}, exactly what the periodic sweep does) change nothing a player did not
 * earn. Every mutation is two steps: a preview that states the exact instance, phase, revision and
 * effects and issues a token, and a confirmation with that token. A token is single-use, belongs to the
 * operator who asked for it, expires after a minute and is refused if the instance has changed since —
 * so a stale or repeated confirmation cannot apply twice or apply to state nobody looked at. Every applied
 * repair is logged with the actor, the instance and its before and after state.
 */
public final class ProjectRecovery {

    /** What a preview proposes. */
    public enum Operation {
        SKIP_NO_REWARDS, SKIP_NORMAL_REWARDS, RESET, REBASELINE,
        /** Detach an instance whose village is gone and bind it to its anchor (1.7.0). */
        REBIND_ANCHOR,
        /** Move an instance whose village is gone to the MCA village now at its anchor (1.7.0). */
        REBIND_VILLAGE,
        /** Every non-terminal instance of one project id, no rewards: the old bare {@code advance}. */
        BULK_SKIP,
        /** Every instance of one project id: the old bare {@code reset}. */
        BULK_RESET
    }

    /** A proposed repair, waiting for its confirmation. */
    record Pending(String actor, String instanceKey, long revision, Operation operation, int objectiveIndex,
                   int value, long expiresAt) {
    }

    private static final long TOKEN_TICKS = 20L * 60L;
    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProjectRecovery() {
    }

    /** Every instance of one project id, in a stable order, so {@code #n} means the same thing twice. */
    public static List<ProjectState> instancesOf(MinecraftServer server, ResourceLocation projectId) {
        return ProjectSavedData.get(server).allInstances().stream()
                .filter(state -> state.projectId().equals(projectId))
                .sorted(Comparator.comparing(state -> state.key().asString()))
                .toList();
    }

    /** The {@code n}th (1-based) instance of a project id. */
    public static Optional<ProjectState> instance(MinecraftServer server, ResourceLocation projectId, int n) {
        List<ProjectState> all = instancesOf(server, projectId);
        return n >= 1 && n <= all.size() ? Optional.of(all.get(n - 1)) : Optional.empty();
    }

    /** One line per instance for a listing. */
    public static Component summary(MinecraftServer server, int n, ProjectState state) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, state.anchorDimension()));
        String village = state.villageId().isPresent() && level != null
                ? McaCompat.villageName(level, state.villageId().getAsInt()).orElse("village " + state.villageId().getAsInt())
                : "no village";
        return Component.literal("#" + n + "  " + state.key().asString() + "  " + state.anchorDimension()
                + "  " + village + "  phase " + (state.currentPhase() + 1) + "  " + state.status().lower()
                + "  rev " + state.revision());
    }

    /**
     * A full read-only diagnosis of one instance: definition and key, geometry, dependency status, and
     * per objective its type, target, evidence, baselines and state. Distinguishes blocked, not yet
     * satisfied, unavailable and unobserved. Mentions no player by name.
     */
    public static List<Component> describe(MinecraftServer server, ProjectState state) {
        List<Component> out = new ArrayList<>();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, state.anchorDimension()));
        out.add(Component.literal(state.projectId() + "  key=" + state.key().asString() + "  status="
                + state.status().lower() + "  phase " + (state.currentPhase() + 1) + "  revision " + state.revision()));
        out.add(Component.literal("  dimension " + state.anchorDimension() + "  anchor " + state.anchorPos().toShortString()
                + "  village " + (state.villageId().isPresent() ? state.villageId().getAsInt() : "none")
                + "  anchor radius " + ProjectManager.anchorRadius(state)
                + (state.anchorRadius().isEmpty() ? " (not yet frozen)" : "")));
        if (ProjectManager.villageGone(server, state)) {
            out.add(Component.literal("  VILLAGE GONE: MCA no longer has village " + state.villageId().getAsInt()
                    + " (deleted or merged). The instance is paused, clock included; rebind it with "
                    + "'... rebind anchor' or '... rebind village'."));
        }
        out.add(Component.literal("  sponsors " + state.sponsors().size() + "  participants "
                + state.participants().size() + "  suspended ticks " + state.suspendedTicks()
                + (state.deferredFollowUps().isEmpty() ? "" : "  deferred follow-ups " + state.deferredFollowUps())));
        ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
        if (def == null) {
            out.add(UnavailableContent.get(UnavailableContent.Kind.PROJECT, state.projectId())
                    .map(missing -> Component.literal("  UNAVAILABLE: " + missing.why().describe()
                            + " — the instance is paused, not failed; its progress is kept."))
                    .orElseGet(() -> Component.literal("  definition not loaded (removed from the datapack?)")));
            return out;
        }
        IntegrationRequirements.dependencies(def).forEach(integration -> out.add(Component.literal(
                "  needs " + integration.displayName() + ": "
                        + (IntegrationRequirements.unavailable(def).isEmpty() ? "available" : "UNAVAILABLE"))));
        if (ProjectPhases.spiritAtStart(state, Optional.empty()).isPresent() || ProjectPhases.spiritAtStartPending(state)) {
            out.add(Component.literal("  spirit at project start: "
                    + (ProjectPhases.spiritAtStartPending(state) ? "pending"
                    : state.extra().getCompound(ProjectPhases.K_SPIRIT_START) + " ("
                    + ProjectPhases.spiritAtStartSource(state).orElse("?") + ")")));
        }
        if (state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()) {
            return out;
        }
        ProjectPhase phase = def.phase(state.currentPhase());
        out.add(Component.literal("  phase '" + phase.keyOr(state.currentPhase()) + "'"));
        if (level != null && ProjectManager.hasPositionalWork(def, state.currentPhase())) {
            ScopeGeometry geometry = ProjectManager.geometry(level, state, ProjectManager.currentPhaseMargin(def, state));
            out.add(Component.literal("  build area: " + geometry.shape().name().toLowerCase(Locale.ROOT)
                    + geometry.effectiveBox().map(b -> " " + b.minX() + "," + b.minY() + "," + b.minZ()
                    + " .. " + b.maxX() + "," + b.maxY() + "," + b.maxZ()).orElse(" radius " + geometry.radius())
                    + "  margin " + geometry.margin() + (geometry.exact() ? "" : "  (approximate outline)")));
        }
        for (int i = 0; i < phase.objectives().size(); i++) {
            ProjectObjective objective = phase.objectives().get(i);
            SharedObjectiveProgress progress = state.progress(i);
            ProjectObjectiveContext context = new ProjectObjectiveContext(server, level, def, state,
                    state.currentPhase(), i, progress, null);
            StringBuilder line = new StringBuilder("  [" + i + "] " + objective.type().id() + "  "
                    + progress.count() + "/" + objective.requiredFor(progress) + "  "
                    + objective.status(context).name().toLowerCase(Locale.ROOT));
            if (objective instanceof PollingProjectObjective polling && polling.isPending(state, progress)) {
                line.append("  (reading pending)");
            }
            if (objective instanceof TownsteadSpiritProjectObjective spirit) {
                line.append("  baseline ").append(spirit.effectiveBaseline(state, progress).stream()
                                .mapToObj(Integer::toString).findFirst().orElse("none"))
                        .append(" [").append(spirit.baselineSource(state, progress)).append(']');
            }
            if (!progress.extra().isEmpty()) {
                line.append("  evidence ").append(progress.extra());
            }
            out.add(Component.literal(line.toString()));
        }
        return out;
    }

    /**
     * Describes a repair without applying it and issues the token that will. Returns the preview lines;
     * the last one names the token.
     */
    public static List<Component> preview(String actor, MinecraftServer server, ProjectState state,
                                          Operation operation, int objectiveIndex, int value) {
        List<Component> out = new ArrayList<>();
        ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
        if (operation != Operation.RESET && def == null) {
            out.add(Component.literal("Refused: the definition is not loaded, so there is no phase to repair. "
                    + "Only reset is available for an unavailable project."));
            return out;
        }
        if (operation != Operation.RESET && state.status().isTerminal()) {
            out.add(Component.literal("Refused: the instance is " + state.status().lower() + "."));
            return out;
        }
        out.add(Component.literal("Preview for " + state.key().asString() + " (revision " + state.revision() + "):"));
        switch (operation) {
            case SKIP_NO_REWARDS, SKIP_NORMAL_REWARDS -> {
                boolean last = state.currentPhase() + 1 >= def.phaseCount();
                boolean pay = operation == Operation.SKIP_NORMAL_REWARDS;
                boolean alreadyPaid = state.isPhaseDistributed(state.currentPhase());
                out.add(Component.literal("  skip phase " + (state.currentPhase() + 1) + " '"
                        + def.phase(state.currentPhase()).keyOr(state.currentPhase()) + "' -> "
                        + (last ? "project COMPLETED" : "phase " + (state.currentPhase() + 2))));
                out.add(Component.literal(pay
                        ? (alreadyPaid ? "  rewards: this phase already paid out; nothing is paid again"
                                : "  rewards: the phase's normal rewards and reputation are paid once"
                                + (last ? ", and the completion reputation and event" : ""))
                        : "  rewards: none — the phase is marked settled without payout; earlier and queued rewards are kept"));
                if (last) {
                    def.followUp().ifPresent(next -> out.add(Component.literal("  follow-up seeded: " + next)));
                } else {
                    out.add(Component.literal("  the next phase takes its baselines as it opens"));
                }
            }
            case BULK_SKIP, BULK_RESET -> {
                return previewBulk(actor, server, state.projectId(), operation);
            }
            case RESET -> out.add(Component.literal("  remove this one instance; every other village's copy is untouched. "
                    + "Its progress, deposits and sponsors are discarded; a fresh copy can be started later."));
            case REBIND_ANCHOR, REBIND_VILLAGE -> {
                Optional<Rebind> target = rebindTarget(server, state, operation);
                if (target.isEmpty()) {
                    out.clear();
                    out.add(Component.literal(operation == Operation.REBIND_VILLAGE
                            ? "Refused: no MCA village lies within 64 blocks of the anchor "
                                    + state.anchorPos().toShortString() + ". Rebind to the anchor instead."
                            : "Refused: the instance is not bound to a village."));
                    return out;
                }
                out.add(Component.literal("  identity " + state.identity() + " -> " + target.get().identity()
                        + "  village " + (state.villageId().isPresent() ? state.villageId().getAsInt() : "none")
                        + " -> " + (target.get().village().isPresent() ? target.get().village().getAsInt()
                        : "none (anchor radius " + ProjectManager.anchorRadius(state) + " around "
                        + target.get().anchor().toShortString() + ")")));
                out.add(Component.literal("  progress, deposits, sponsors, clock and owed rewards move with it; "
                        + "nothing is paid, reset or re-counted"));
            }
            case REBASELINE -> {
                ProjectPhase phase = def.phase(state.currentPhase());
                if (objectiveIndex < 0 || objectiveIndex >= phase.objectives().size()
                        || !(phase.objectives().get(objectiveIndex) instanceof TownsteadSpiritProjectObjective spirit)) {
                    out.clear();
                    out.add(Component.literal("Refused: objective " + objectiveIndex
                            + " of the current phase is not a townstead_spirit_project objective."));
                    return out;
                }
                SharedObjectiveProgress progress = state.progress(objectiveIndex);
                out.add(Component.literal("  objective " + objectiveIndex + " baseline "
                        + spirit.effectiveBaseline(state, progress).stream().mapToObj(Integer::toString)
                        .findFirst().orElse("none") + " [" + spirit.baselineSource(state, progress) + "] -> "
                        + value + " [operator]; credited progress stays at " + progress.count()));
            }
        }
        String token = newToken();
        long now = server.overworld().getGameTime();
        synchronized (PENDING) {
            PENDING.values().removeIf(pending -> now > pending.expiresAt());
            PENDING.put(token, new Pending(actor, state.key().asString(), state.revision(), operation,
                    objectiveIndex, value, now + TOKEN_TICKS));
        }
        out.add(Component.literal("Back up the world first. To apply within 60 seconds: /mcaquests project confirm "
                + token));
        return out;
    }

    /**
     * The deliberate, explicit form of the old bare {@code advance <id>} / {@code reset <id>}: every
     * instance of one project id, listed in the preview. Confirmed like any other repair, and refused if
     * the set of instances or any of their revisions changes in between.
     */
    public static List<Component> previewBulk(String actor, MinecraftServer server, ResourceLocation projectId,
                                              Operation operation) {
        List<Component> out = new ArrayList<>();
        List<ProjectState> all = instancesOf(server, projectId).stream()
                .filter(state -> operation == Operation.BULK_RESET || !state.status().isTerminal()).toList();
        if (all.isEmpty()) {
            out.add(Component.literal("No " + (operation == Operation.BULK_RESET ? "" : "non-terminal ")
                    + "instance of '" + projectId + "'."));
            return out;
        }
        out.add(Component.literal("Bulk preview: " + (operation == Operation.BULK_RESET
                ? "REMOVE" : "skip one phase without rewards for") + " all " + all.size() + " instance(s) of '"
                + projectId + "':"));
        for (int i = 0; i < all.size(); i++) {
            out.add(summary(server, i + 1, all.get(i)));
        }
        String token = newToken();
        long now = server.overworld().getGameTime();
        synchronized (PENDING) {
            PENDING.values().removeIf(pending -> now > pending.expiresAt());
            PENDING.put(token, new Pending(actor, projectId.toString(), fingerprint(all), operation, -1, 0,
                    now + TOKEN_TICKS));
        }
        out.add(Component.literal("This changes every village listed above. Back up the world first. To apply "
                + "within 60 seconds: /mcaquests project confirm " + token));
        return out;
    }

    private static long fingerprint(List<ProjectState> states) {
        long hash = 17L;
        for (ProjectState state : states) {
            hash = hash * 31L + state.key().asString().hashCode();
            hash = hash * 31L + state.revision();
        }
        return hash;
    }

    private static Component confirmBulk(String actor, MinecraftServer server, Pending pending) {
        ResourceLocation projectId = ResourceLocation.tryParse(pending.instanceKey());
        if (projectId == null) {
            return Component.literal("Malformed bulk token. Nothing was changed.");
        }
        List<ProjectState> all = instancesOf(server, projectId).stream()
                .filter(state -> pending.operation() == Operation.BULK_RESET || !state.status().isTerminal()).toList();
        if (fingerprint(all) != pending.revision()) {
            return Component.literal("The instances of '" + projectId + "' changed since the preview. "
                    + "Nothing was changed; preview again.");
        }
        int applied;
        if (pending.operation() == Operation.BULK_RESET) {
            applied = ProjectManager.adminReset(server, projectId);
        } else {
            applied = ProjectManager.adminAdvance(server, projectId);
        }
        McaQuests.LOGGER.info("[MCA: Quests] project bulk repair by {}: {} on {} instance(s) of {}",
                actor, pending.operation(), applied, projectId);
        return Component.literal("Applied " + pending.operation().name().toLowerCase(Locale.ROOT) + " to "
                + applied + " instance(s) of '" + projectId + "'.");
    }

    /** Applies a previewed repair. Returns the outcome line. */
    public static Component confirm(String actor, MinecraftServer server, String token) {
        Pending pending;
        synchronized (PENDING) {
            pending = PENDING.remove(token);
        }
        long now = server.overworld().getGameTime();
        Optional<String> refused = refusal(pending, token, actor, now);
        if (refused.isPresent()) {
            return Component.literal(refused.get());
        }
        if (pending.operation() == Operation.BULK_SKIP || pending.operation() == Operation.BULK_RESET) {
            return confirmBulk(actor, server, pending);
        }
        ProjectSavedData data = ProjectSavedData.get(server);
        ProjectState state = data.getInstance(pending.instanceKey()).orElse(null);
        if (state == null) {
            return Component.literal("The instance no longer exists. Nothing was changed.");
        }
        if (stale(pending, state.revision())) {
            return Component.literal("The instance changed since the preview (revision " + pending.revision() + " -> "
                    + state.revision() + "). Nothing was changed; preview again.");
        }
        String before = "phase " + (state.currentPhase() + 1) + " " + state.status().lower();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, state.anchorDimension()));
        ProjectDefinition def = ProjectRegistry.get(state.projectId()).orElse(null);
        switch (pending.operation()) {
            case SKIP_NO_REWARDS, SKIP_NORMAL_REWARDS -> {
                if (def == null || state.status().isTerminal()) {
                    return Component.literal("The instance can no longer be skipped. Nothing was changed.");
                }
                ProjectManager.adminSkipPhase(server, level != null ? level : server.overworld(), data, state, def,
                        pending.operation() == Operation.SKIP_NORMAL_REWARDS);
            }
            case RESET -> {
                data.removeInstance(pending.instanceKey());
                data.setDirty();
                server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
                ProjectMenuSessions.refreshAll(server);
            }
            case BULK_SKIP, BULK_RESET -> {
                return Component.literal("Unexpected bulk token.");
            }
            case REBIND_ANCHOR, REBIND_VILLAGE -> {
                Optional<Rebind> target = rebindTarget(server, state, pending.operation());
                if (target.isEmpty()) {
                    return Component.literal("The rebind target no longer exists. Nothing was changed.");
                }
                Optional<ProjectState> moved = data.rebind(state, target.get().identity(), target.get().village(),
                        target.get().anchor());
                if (moved.isEmpty()) {
                    return Component.literal("Another instance already holds " + target.get().identity()
                            + ". Nothing was changed; instances are never merged.");
                }
                server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
                ProjectMenuSessions.refreshAll(server);
                McaQuests.LOGGER.info("[MCA: Quests] project repair by {}: {} on {} -> {}", actor,
                        pending.operation(), pending.instanceKey(), moved.get().key().asString());
                return Component.literal("Rebound " + pending.instanceKey() + " as "
                        + moved.get().key().asString() + ".");
            }
            case REBASELINE -> {
                if (def == null || state.currentPhase() < 0 || state.currentPhase() >= def.phaseCount()
                        || pending.objectiveIndex() >= def.phase(state.currentPhase()).objectives().size()) {
                    return Component.literal("The objective no longer exists. Nothing was changed.");
                }
                SharedObjectiveProgress progress = state.progress(pending.objectiveIndex());
                progress.extra().putInt(TownsteadSpiritProjectObjective.K_BASELINE_OVERRIDE, pending.value());
                state.bumpRevision();
                data.setDirty();
                ProjectManager.pollOne(server, data, state, true);
                server.getPlayerList().getPlayers().forEach(ProjectManager::syncProjects);
                ProjectMenuSessions.refreshAll(server);
            }
        }
        String after = pending.operation() == Operation.RESET ? "removed"
                : "phase " + (state.currentPhase() + 1) + " " + state.status().lower();
        McaQuests.LOGGER.info("[MCA: Quests] project repair by {}: {} on {} ({} -> {}); objective {}, value {}",
                actor, pending.operation(), pending.instanceKey(), before, after, pending.objectiveIndex(), pending.value());
        return Component.literal("Applied " + pending.operation().name().toLowerCase(Locale.ROOT) + " to "
                + pending.instanceKey() + ": " + before + " -> " + after + ".");
    }

    /** Where a rebind would put an instance. */
    record Rebind(String identity, java.util.OptionalInt village, net.minecraft.core.BlockPos anchor) {
    }

    /**
     * The identity, village and anchor a rebind would give this instance. To its anchor: the instance
     * becomes anchor-bound where it stands, under an identity that names its old village so it cannot
     * collide with a sponsor's own anchor project. To a village: the MCA village nearest its anchor, within
     * 64 blocks, under that village's identity. Empty when there is nothing to rebind to.
     */
    static Optional<Rebind> rebindTarget(MinecraftServer server, ProjectState state, Operation operation) {
        if (state.villageId().isEmpty()) {
            return Optional.empty();
        }
        if (operation == Operation.REBIND_ANCHOR) {
            return Optional.of(new Rebind("anchor:rebound:" + state.identity(), java.util.OptionalInt.empty(),
                    state.anchorPos()));
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, state.anchorDimension()));
        if (level == null) {
            return Optional.empty();
        }
        java.util.OptionalInt village = McaCompat.findNearestVillageId(level, state.anchorPos(), 64);
        if (village.isEmpty() || village.getAsInt() == state.villageId().getAsInt()) {
            return Optional.empty();
        }
        net.minecraft.core.BlockPos center = McaCompat.villageCenter(level, village.getAsInt()).orElse(state.anchorPos());
        Optional<String> profession = professionOf(state.identity());
        String identity = profession.isPresent()
                ? dev.otectus.mcaquests.project.scope.ScopeResolver.professionIdentity(village.getAsInt(),
                state.anchorDimension(), profession.get())
                : dev.otectus.mcaquests.project.scope.ScopeResolver.villageIdentity(village.getAsInt(), state.anchorDimension());
        return Optional.of(new Rebind(identity, village, center));
    }

    /** {@code p:<village>[@<dimension>]:<profession>}; the dimension and the profession both contain colons. */
    private static final java.util.regex.Pattern PROFESSION_IDENTITY =
            java.util.regex.Pattern.compile("^p:\\d+(?:@[a-z0-9_.-]+:[a-z0-9_./-]+)?:(.+)$");

    /** The profession of a profession-scope identity, or empty for any other scope. */
    static Optional<String> professionOf(String identity) {
        java.util.regex.Matcher matcher = PROFESSION_IDENTITY.matcher(identity);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** Why a confirmation must be refused before anything is looked up, or empty. Pure. */
    static Optional<String> refusal(@javax.annotation.Nullable Pending pending, String token, String actor, long now) {
        if (pending == null) {
            return Optional.of("Unknown or already used token '" + token + "'. Preview again.");
        }
        if (!pending.actor().equals(actor)) {
            return Optional.of("That token belongs to another operator. Preview it yourself.");
        }
        if (now > pending.expiresAt()) {
            return Optional.of("That token expired. Preview again.");
        }
        return Optional.empty();
    }

    /** True when the instance has moved on since the preview. Pure. */
    static boolean stale(Pending pending, long currentRevision) {
        return currentRevision != pending.revision();
    }

    /** Takes a token out of the pending set: a second confirmation of the same token finds nothing. */
    @javax.annotation.Nullable
    static Pending take(String token) {
        synchronized (PENDING) {
            return PENDING.remove(token);
        }
    }

    /** Test seam: registers a pending repair directly. */
    static void putForTest(String token, Pending pending) {
        synchronized (PENDING) {
            PENDING.put(token, pending);
        }
    }

    private static String newToken() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder out = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            out.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return out.toString();
    }

    static void clearSessionState() {
        synchronized (PENDING) {
            PENDING.clear();
        }
    }
}
