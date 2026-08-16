package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.quest.reputation.ReputationOutcome;

/**
 * The reputation a community project moves as it progresses (spec §29.4).
 *
 * <h2>What changed in 1.1.0, and what did not</h2>
 *
 * <p>Each field still accepts the bare integer it always did, so
 * {@code "reputation": { "on_project_complete": 10 }} parses exactly as before and needs no edit.
 * What that number <em>does</em> is what changed: it used to be added once to a world-shared village
 * score with no player attached, which on a server moved everybody's reputation for one group's work.
 * It is now awarded to each eligible contributor as their own standing (§29.4) — the only reading of
 * "reputation for helping" that survives contact with multiplayer.
 *
 * <p>The object form adds what the shorthand could not say: which incident type to record, how public
 * it is, and — the important one — exactly who counts as a recipient.
 */
public record ReputationSpec(ReputationOutcome onPhaseComplete, ReputationOutcome onProjectComplete,
                             ReputationOutcome onFail) {

    public static final ReputationSpec NONE =
            new ReputationSpec(ReputationOutcome.NONE, ReputationOutcome.NONE, ReputationOutcome.NONE);

    public static final Codec<ReputationSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StrictCodecs.strictOptional(ReputationOutcome.CODEC, "on_phase_complete", ReputationOutcome.NONE)
                    .forGetter(ReputationSpec::onPhaseComplete),
            StrictCodecs.strictOptional(ReputationOutcome.CODEC, "on_project_complete", ReputationOutcome.NONE)
                    .forGetter(ReputationSpec::onProjectComplete),
            StrictCodecs.strictOptional(ReputationOutcome.CODEC, "on_fail", ReputationOutcome.NONE)
                    .forGetter(ReputationSpec::onFail)
    ).apply(instance, ReputationSpec::new));

    public ReputationSpec {
        onPhaseComplete = onPhaseComplete == null ? ReputationOutcome.NONE : onPhaseComplete;
        onProjectComplete = onProjectComplete == null ? ReputationOutcome.NONE : onProjectComplete;
        onFail = onFail == null ? ReputationOutcome.NONE : onFail;
    }

    /**
     * The phase outcome with the shorthand defaults filled in: everyone who worked on the phase that
     * just finished, recorded as a project-phase deed.
     */
    public ReputationOutcome phaseOutcome() {
        return onPhaseComplete
                .withDefaultRecipients(ReputationOutcome.Recipients.PHASE_CONTRIBUTORS)
                .withDefaultIncident(QuestReputationBlock.Incidents.PROJECT_PHASE_COMPLETED);
    }

    /** Completion: everyone who contributed at least once, recorded as a project completion. */
    public ReputationOutcome completeOutcome() {
        return onProjectComplete
                .withDefaultRecipients(ReputationOutcome.Recipients.ALL_PARTICIPANTS)
                .withDefaultIncident(QuestReputationBlock.Incidents.PROJECT_COMPLETED);
    }

    /** Failure: the same participants, recorded as a project failure. Only ever authored explicitly. */
    public ReputationOutcome failOutcome() {
        return onFail
                .withDefaultRecipients(ReputationOutcome.Recipients.ALL_PARTICIPANTS)
                .withDefaultIncident(QuestReputationBlock.Incidents.PROJECT_FAILED);
    }
}
