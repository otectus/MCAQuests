package dev.otectus.mcaquests.project;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.reputation.ReputationDedupe;
import dev.otectus.mcaquests.quest.reputation.ReputationOutcome;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a project's authored {@link ReputationSpec} into per-recipient standing (spec §29.4).
 *
 * <h2>The change this class embodies</h2>
 *
 * <p>Before 1.1.0 a project's reputation was one anonymous award against a world-shared village score.
 * §29.4 is explicit that this must not happen: "the current shared world score must not receive a
 * single anonymous award. Each recipient gets an idempotent player/community incident." So the delta
 * is applied once <em>per eligible player</em>, each with their own dedupe key, which also means a
 * player who contributed to two phases is credited for both and a player who contributed to none is
 * credited for neither.
 *
 * <p>Offline recipients are handled the same as online ones. Standing is stored per player UUID and
 * needs nobody present, so a contributor who logged off before the barn was finished still gets the
 * credit they earned — which is exactly the sort of thing the old shared score got accidentally right
 * and for the wrong reason.
 */
public final class ProjectReputation {

    private ProjectReputation() {
    }

    /**
     * Applies one project outcome to everyone it is meant for.
     *
     * @param outcome    already defaulted by {@link ReputationSpec}'s accessors
     * @param outcomeKey the dedupe discriminator: {@code "complete"}, {@code "fail"}, or
     *                   {@code "phase:<n>"}
     * @param phaseIndex the phase this outcome belongs to, or {@code -1} for a terminal outcome
     * @return how many players were credited
     */
    public static int apply(MinecraftServer server, ServerLevel level, ProjectState state,
                            ProjectDefinition def, ReputationOutcome outcome, String outcomeKey,
                            int phaseIndex) {
        if (outcome.isNoOp()) {
            return 0;
        }
        Optional<QuestReputation.Community> community = community(server, level, state);
        if (community.isEmpty()) {
            // A project whose scope is not a village has no community to hold an opinion. Silently
            // doing nothing is right: the alternative is attaching civic credit to an arbitrary
            // nearby village that had nothing to do with the work (§12.2).
            McaQuests.LOGGER.debug("[MCA: Quests] project {} has no village community; skipping its "
                    + "reputation outcome '{}'", def.id(), outcomeKey);
            return 0;
        }

        Set<UUID> recipients = recipients(state, outcome.recipients(), phaseIndex);
        if (recipients.isEmpty()) {
            return 0;
        }
        int credited = 0;
        for (UUID recipient : recipients) {
            String dedupeKey = phaseIndex >= 0
                    ? ReputationDedupe.projectPhase(def.id(), state.key().asString(), phaseIndex, recipient)
                    : ReputationDedupe.projectOutcome(def.id(), state.key().asString(), outcomeKey, recipient);
            ReputationAward.Builder award = ReputationAward
                    .builder(server, recipient, community.get().dimension(), community.get().villageId(),
                            QuestReputation.SOURCE)
                    .delta(outcome.delta())
                    .incident(outcome.incident().orElse(null))
                    .dedupeKey(dedupeKey)
                    .visibility(outcome.visibility().orElse(null))
                    .tags(outcome.tags())
                    .context("source_title", def.id().getPath())
                    .context("project", def.id().toString());
            QuestReputation.award(award.build());
            credited++;
        }
        return credited;
    }

    /**
     * Who this outcome is for.
     *
     * <p>{@code PHASE_CONTRIBUTORS} is only meaningful for the phase that just completed, so a
     * terminal outcome asking for it falls back to all participants rather than crediting nobody —
     * an authoring slip should not silently swallow the reward.
     */
    public static Set<UUID> recipients(ProjectState state, ReputationOutcome.Recipients kind,
                                       int phaseIndex) {
        return switch (kind) {
            case NOBODY -> Set.of();
            case PHASE_CONTRIBUTORS -> phaseIndex >= 0
                    ? new LinkedHashSet<>(state.currentPhaseContributors())
                    : new LinkedHashSet<>(state.participants());
            case ALL_PARTICIPANTS, ACCEPTED_PARTICIPANTS -> new LinkedHashSet<>(state.participants());
            // A project has no single "resolving player"; the closest honest answer is everyone who
            // helped, and validation warns about the mismatch.
            case RESOLVING_PLAYER -> new LinkedHashSet<>(state.participants());
        };
    }

    /** The dimension-aware community a project's scope names, when it names one at all. */
    public static Optional<QuestReputation.Community> community(MinecraftServer server, ServerLevel level,
                                                                ProjectState state) {
        java.util.OptionalInt villageId = state.villageId();
        if (villageId.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel resolved = level != null ? level : server.overworld();
        return Optional.of(QuestReputation.inLevel(resolved, villageId.getAsInt()));
    }
}
