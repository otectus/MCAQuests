package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.project.BundledProjectMigrations;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.ProjectPhases;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.TownsteadNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * A project phase that finishes when the village has grown into something (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_spirit_project", "spirit": "tourism", "points_delta": 2, "baseline": "project" }
 * }</pre>
 *
 * <p>Spirit is Townstead's village-character metric: every <em>completed</em> MCA building adds the
 * points Townstead's {@code extended_buildings} data gives its type (in Townstead 0.7.6 an inn adds
 * Tourism +2 and Commercial +5). {@code points_delta} asks for growth, never for a total, so a village
 * that was already developed does not hand the phase over for free.
 *
 * <h2>Measured from where</h2>
 * <ul>
 *   <li>{@code "baseline": "phase"} (the default, and the only rule before 1.7.0): growth since this
 *       phase became current.</li>
 *   <li>{@code "baseline": "project"} (1.7.0): growth since the project began. For a phase whose
 *       building <em>is</em> the spirit source — an inn is the only Tourism source — this is the rule
 *       that matches what players do: build the inn as soon as they can, often during the donation
 *       phase before it. Measured from the phase, that inn landed in the baseline and the phase asked
 *       for a second one.</li>
 * </ul>
 *
 * <p><b>Readings are taken at boundaries, never on a convenient poll.</b> The phase reading is captured
 * when the phase opens ({@link #onPhaseEntered}) and the project reading when the instance begins
 * ({@code ProjectPhases.begin}). Before 1.7.0 the phase reading was taken on the first poll after the
 * phase opened, so spirit earned in between was folded into the baseline and asked for again. When a
 * reading cannot be taken the objective records that it is pending and the phase pauses until it can;
 * absence of data is never read as zero.
 *
 * <p><b>Four numbers, kept apart:</b> the baseline, the current reading, the required growth and the
 * credited progress. Credited progress is a high-water mark: spirit falls when a building is lost, and a
 * village is never walked backwards.
 *
 * <p>An instance from before 1.7.0 has no project-start reading. Its {@code "project"} objectives
 * measure from their phase, as they did when it was started, with the number they had then
 * ({@link BundledProjectMigrations}); nothing reconstructs a reading nobody took.
 */
public record TownsteadSpiritProjectObjective(Optional<String> spirit, OptionalInt pointsDelta,
                                              OptionalInt targetTier, Baseline baseline)
        implements TownsteadProjectObjective {

    /** Where growth is measured from. */
    public enum Baseline {
        PHASE, PROJECT;

        static final Codec<Baseline> CODEC = Codec.STRING.flatXmap(raw -> switch (raw.toLowerCase(Locale.ROOT)) {
            case "phase" -> DataResult.success(PHASE);
            case "project" -> DataResult.success(PROJECT);
            default -> DataResult.error(() -> "Unknown spirit baseline '" + raw + "'; expected phase or project");
        }, baseline -> DataResult.success(baseline.name().toLowerCase(Locale.ROOT)));
    }

    /** The phase-start reading. Name kept from 1.4.x so existing saves carry straight over. */
    static final String K_BASELINE = "townstead_spirit_baseline";
    /** How {@link #K_BASELINE} was taken: phase_entry, late, legacy_first_poll or operator. */
    static final String K_BASELINE_SOURCE = "townstead_spirit_baseline_source";
    static final String K_PENDING = "townstead_spirit_baseline_pending";
    /** A per-instance required growth frozen by a migration; absent means {@link #required()}. */
    static final String K_REQUIRED = "townstead_spirit_required";
    /** Set for an instance that predates project baselines and so measures from its phase. */
    static final String K_LEGACY = "townstead_spirit_legacy";
    /** An operator's explicit baseline, which outranks every other. */
    public static final String K_BASELINE_OVERRIDE = "townstead_spirit_baseline_operator";

    /** Pre-1.7.0 JSON shape, measured from the phase. */
    public TownsteadSpiritProjectObjective(Optional<String> spirit, OptionalInt pointsDelta, OptionalInt targetTier) {
        this(spirit, pointsDelta, targetTier, Baseline.PHASE);
    }

    public static final Codec<TownsteadSpiritProjectObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritProjectObjective::spirit),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "points_delta")
                            .forGetter((TownsteadSpiritProjectObjective o) -> box(o.pointsDelta())),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "target_tier")
                            .forGetter((TownsteadSpiritProjectObjective o) -> box(o.targetTier())),
                    StrictCodecs.strictOptional(Baseline.CODEC, "baseline", Baseline.PHASE)
                            .forGetter(TownsteadSpiritProjectObjective::baseline)
            ).apply(instance, (s, delta, tier, base) ->
                    new TownsteadSpiritProjectObjective(s, unbox(delta), unbox(tier), base)));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_SPIRIT;
    }

    @Override
    public java.util.Set<TownsteadCapability> requiredCapabilities() {
        return java.util.Set.of(TownsteadCapability.READ_SPIRIT);
    }

    @Override
    public int required() {
        return pointsDelta.orElseGet(() -> targetTier.orElse(1));
    }

    @Override
    public int requiredFor(SharedObjectiveProgress progress) {
        return progress.extra().contains(K_REQUIRED) ? progress.extra().getInt(K_REQUIRED) : required();
    }

    private boolean measuresGrowth() {
        return targetTier.isEmpty();
    }

    // ------------------------------------------------------------------ baselines

    @Override
    public void onPhaseEntered(MinecraftServer server, @Nullable ServerLevel level, ProjectDefinition definition,
                               ProjectState state, int objectiveIndex, SharedObjectiveProgress progress) {
        if (!measuresGrowth()) {
            return;
        }
        CompoundTag extra = progress.extra();
        if (baseline == Baseline.PROJECT && isLegacyInstance(state)) {
            markLegacy(definition, state, objectiveIndex, progress);
        }
        OptionalInt reading = read(level, state);
        if (reading.isPresent()) {
            extra.putInt(K_BASELINE, reading.getAsInt());
            extra.putString(K_BASELINE_SOURCE, "phase_entry");
            extra.remove(K_PENDING);
        } else if (needsPhaseBaseline(progress)) {
            extra.putBoolean(K_PENDING, true);
        }
    }

    @Override
    public boolean isPending(ProjectState state, SharedObjectiveProgress progress) {
        if (!measuresGrowth()) {
            return false;
        }
        if (progress.extra().contains(K_BASELINE_OVERRIDE)) {
            return false;
        }
        if (usesProjectReading(state, progress)) {
            return ProjectPhases.spiritAtStartPending(state);
        }
        return progress.extra().getBoolean(K_PENDING);
    }

    @Override
    public boolean resolvePending(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                                  ProjectState state, SharedObjectiveProgress progress) {
        if (!measuresGrowth() || !progress.extra().getBoolean(K_PENDING)) {
            return false;
        }
        OptionalInt reading = read(level, state);
        if (reading.isEmpty()) {
            return false;
        }
        // Taken later than the boundary it stands for, and recorded as such rather than passed off
        // as the phase-entry value.
        progress.extra().putInt(K_BASELINE, reading.getAsInt());
        progress.extra().putString(K_BASELINE_SOURCE, "late");
        progress.extra().remove(K_PENDING);
        return true;
    }

    /** True when growth is measured from the project-start reading for this instance. */
    private boolean usesProjectReading(ProjectState state, SharedObjectiveProgress progress) {
        return baseline == Baseline.PROJECT && !progress.extra().getBoolean(K_LEGACY)
                && !isLegacyInstance(state);
    }

    /** An instance that predates project-start readings: it has neither the reading nor a pending flag. */
    private static boolean isLegacyInstance(ProjectState state) {
        return ProjectPhases.spiritAtStart(state, Optional.empty()).isEmpty()
                && !ProjectPhases.spiritAtStartPending(state);
    }

    private boolean needsPhaseBaseline(SharedObjectiveProgress progress) {
        return baseline == Baseline.PHASE || progress.extra().getBoolean(K_LEGACY);
    }

    private void markLegacy(ProjectDefinition definition, ProjectState state, int objectiveIndex,
                            SharedObjectiveProgress progress) {
        if (progress.extra().getBoolean(K_LEGACY)) {
            return;
        }
        progress.extra().putBoolean(K_LEGACY, true);
        int phase = state.currentPhase();
        if (phase >= 0 && phase < definition.phaseCount()) {
            BundledProjectMigrations.legacySpiritDelta(definition.id(), definition.phase(phase).keyOr(phase),
                    objectiveIndex).ifPresent(delta -> progress.extra().putInt(K_REQUIRED, delta));
        }
    }

    /**
     * The reading growth is measured from for this instance, or empty while it is pending. An operator
     * baseline outranks everything; a project-start reading is used when this objective asks for one and
     * the instance has it; otherwise the phase reading.
     */
    public OptionalInt effectiveBaseline(ProjectState state, SharedObjectiveProgress progress) {
        CompoundTag extra = progress.extra();
        if (extra.contains(K_BASELINE_OVERRIDE)) {
            return OptionalInt.of(extra.getInt(K_BASELINE_OVERRIDE));
        }
        if (usesProjectReading(state, progress)) {
            return ProjectPhases.spiritAtStart(state, spirit);
        }
        return extra.contains(K_BASELINE) ? OptionalInt.of(extra.getInt(K_BASELINE)) : OptionalInt.empty();
    }

    /** How the effective baseline was taken, for diagnostics. */
    public String baselineSource(ProjectState state, SharedObjectiveProgress progress) {
        if (progress.extra().contains(K_BASELINE_OVERRIDE)) {
            return "operator";
        }
        if (usesProjectReading(state, progress)) {
            return "project:" + ProjectPhases.spiritAtStartSource(state).orElse("pending");
        }
        String phase = progress.extra().contains(K_BASELINE_SOURCE)
                ? progress.extra().getString(K_BASELINE_SOURCE) : "legacy_first_poll";
        return (progress.extra().getBoolean(K_LEGACY) ? "legacy-phase:" : "phase:") + phase;
    }

    private OptionalInt read(@Nullable ServerLevel level, ProjectState state) {
        return ProjectPhases.readSpirit(level, state)
                .map(view -> OptionalInt.of(ProjectPhases.pointsOf(view, spirit)))
                .orElseGet(OptionalInt::empty);
    }

    // ------------------------------------------------------------------ progress

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        if (village.isEmpty() || !TownsteadBridge.Holder.get().has(TownsteadCapability.READ_SPIRIT)) {
            return false;
        }
        TownsteadSpiritView view = ProjectPhases.readSpirit(level, state).orElse(null);
        if (view == null) {
            return false; // unreadable is unknown, not zero: nothing moves
        }
        int reached;
        if (!measuresGrowth()) {
            reached = view.tier();
        } else {
            if (baseline == Baseline.PROJECT && isLegacyInstance(state) && !progress.extra().getBoolean(K_LEGACY)) {
                // A phase already under way when 1.7.0 arrived: keep the rule it was started under.
                int index = definition.phase(state.currentPhase()).objectives().indexOf(this);
                markLegacy(definition, state, Math.max(0, index), progress);
            }
            if (!progress.extra().contains(K_BASELINE) && !progress.extra().getBoolean(K_PENDING)
                    && needsPhaseBaseline(progress) && !progress.extra().contains(K_BASELINE_OVERRIDE)) {
                // A phase entered by an older version, which never took the reading at entry. The first
                // reading now is the only honest one available, and is labelled as such.
                progress.extra().putInt(K_BASELINE, ProjectPhases.pointsOf(view, spirit));
                progress.extra().putString(K_BASELINE_SOURCE, "legacy_first_poll");
                return true;
            }
            OptionalInt base = effectiveBaseline(state, progress);
            if (base.isEmpty()) {
                return false;
            }
            reached = Math.max(0, ProjectPhases.pointsOf(view, spirit) - base.getAsInt());
        }
        int next = Math.min(requiredFor(progress), reached);
        if (next <= progress.count()) {
            return false; // spirit falls when a building is lost; never walk a village backwards
        }
        progress.setCount(next);
        return true;
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public Component describe() {
        boolean tier = targetTier.isPresent();
        if (spirit.isEmpty()) {
            return Component.translatable(tier
                    ? "mcaquests.project.objective.townstead_spirit_tier_any"
                    : "mcaquests.project.objective.townstead_spirit_points_any", required());
        }
        if (!tier) {
            return Component.translatable(baseline == Baseline.PROJECT
                            ? "mcaquests.project.objective.townstead_spirit_points_since_start"
                            : "mcaquests.project.objective.townstead_spirit_points_since_phase",
                    required(), TownsteadNames.spiritMetric(spirit.get()));
        }
        return Component.translatable("mcaquests.project.objective.townstead_spirit_tier",
                required(), TownsteadNames.spirit(spirit.get()));
    }

    @Override
    public ProjectObjectiveStatus status(ProjectObjectiveContext context) {
        if (isSatisfied(context.progress())) {
            return ProjectObjectiveStatus.SATISFIED;
        }
        if (context.state() != null && context.level() != null && !isAvailable(context.level(), context.state())) {
            return ProjectObjectiveStatus.UNAVAILABLE;
        }
        if (context.state() != null && isPending(context.state(), context.progress())) {
            return ProjectObjectiveStatus.BLOCKED;
        }
        return ProjectObjectiveStatus.IN_PROGRESS;
    }

    @Override
    public List<Component> explain(ProjectObjectiveContext context) {
        List<Component> lines = new ArrayList<>();
        if (spirit.isPresent()) {
            lines.add(Component.translatable("mcaquests.project.help.spirit.metric",
                    TownsteadNames.spiritMetric(spirit.get()), TownsteadNames.spirit(spirit.get())));
        } else {
            lines.add(Component.translatable("mcaquests.project.help.spirit.metric_total"));
        }
        if (!measuresGrowth()) {
            lines.add(Component.translatable("mcaquests.project.help.spirit.tier", required()));
            return lines;
        }
        ProjectState state = context.state();
        if (state == null) {
            lines.add(Component.translatable(baseline == Baseline.PROJECT
                    ? "mcaquests.project.help.spirit.rule_project" : "mcaquests.project.help.spirit.rule_phase",
                    required()));
        } else if (isPending(state, context.progress())) {
            lines.add(Component.translatable("mcaquests.project.help.spirit.pending"));
        } else {
            OptionalInt base = effectiveBaseline(state, context.progress());
            OptionalInt now = read(context.level(), state);
            boolean fromProject = usesProjectReading(state, context.progress());
            lines.add(Component.translatable(fromProject
                            ? "mcaquests.project.help.spirit.rule_project" : "mcaquests.project.help.spirit.rule_phase",
                    requiredFor(context.progress())));
            if (base.isPresent() && now.isPresent()) {
                lines.add(Component.translatable("mcaquests.project.help.spirit.values",
                        base.getAsInt(), now.getAsInt(), Math.max(0, now.getAsInt() - base.getAsInt()),
                        requiredFor(context.progress())));
            } else if (now.isEmpty()) {
                lines.add(Component.translatable("mcaquests.project.help.spirit.unreadable"));
            }
            if (context.progress().extra().getBoolean(K_LEGACY)) {
                lines.add(Component.translatable("mcaquests.project.help.spirit.legacy"));
            }
        }
        Map<String, Integer> sources = sourcesOf(spirit);
        if (sources.isEmpty()) {
            lines.add(Component.translatable("mcaquests.project.help.spirit.sources_unknown"));
        } else {
            List<Component> parts = new ArrayList<>();
            sources.forEach((type, points) -> parts.add(Component.translatable(
                    "mcaquests.project.help.spirit.source", TownsteadNames.building(type), points)));
            lines.add(Component.translatable("mcaquests.project.help.spirit.sources",
                    joined(parts)));
        }
        return lines;
    }

    /** Recomputed at most this often: building data only changes on a datapack reload. */
    private static final long SOURCES_TTL_NANOS = 60_000_000_000L;
    private static final Map<String, Map<String, Integer>> SOURCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long sourcesAt;

    /** Building types whose completed buildings raise {@code spirit} (or any spirit), with their points. */
    static Map<String, Integer> sourcesOf(Optional<String> spirit) {
        long now = System.nanoTime();
        if (now - sourcesAt > SOURCES_TTL_NANOS) {
            SOURCES.clear();
            sourcesAt = now;
        }
        return SOURCES.computeIfAbsent(spirit.orElse("*"), key -> computeSources(spirit));
    }

    private static Map<String, Integer> computeSources(Optional<String> spirit) {
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.has(TownsteadCapability.READ_SPIRIT)) {
            return Map.of();
        }
        Map<String, Integer> out = new TreeMap<>();
        for (String type : McaCompat.buildingTypeIds()) {
            Map<String, Integer> contributions = bridge.spiritContributions(type);
            int points = spirit.map(id -> contributions.getOrDefault(id, 0))
                    .orElseGet(() -> contributions.values().stream().mapToInt(Integer::intValue).sum());
            if (points > 0) {
                out.put(type, points);
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static Component joined(List<Component> parts) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(parts.get(i));
        }
        return out;
    }
}
