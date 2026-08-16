package dev.otectus.mcaquests.quest.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The optional top-level {@code reputation} block on a quest definition (spec §29.3).
 *
 * <pre>{@code
 * "reputation": {
 *   "complete": { "delta": 12, "incident": "mcareputation:quest_completed", "visibility": "village" },
 *   "fail":     { "delta": -4, "incident": "mcareputation:quest_failed" },
 *   "abandon":  { "delta": -2, "visibility": "witnessed" }
 * }
 * }</pre>
 *
 * <p>All three are optional, and <b>failure and abandonment default to nothing</b>. That is a
 * deliberate content decision, not an oversight: abandoning a quest has always been free from the
 * villager menu, and quietly attaching a penalty to it would change the behaviour of every existing
 * pack (§29.3, §33 rule 6).
 *
 * <h2>Relationship to the legacy {@code village_reputation} reward</h2>
 *
 * <p>The old reward still works. When a quest has no {@code reputation.complete}, the sum of its
 * {@code mcaquests:village_reputation} rewards is translated into one generic completion outcome, so
 * nothing about an existing pack changes.
 *
 * <p>When a quest has <em>both</em>, this block is authoritative and the legacy reward is treated as
 * display/compatibility only — never both, which would silently double the intended reward. Validation
 * warns about the ambiguity so the author can remove one.
 */
public record QuestReputationBlock(
        Optional<ReputationOutcome> complete,
        Optional<ReputationOutcome> fail,
        Optional<ReputationOutcome> abandon) {

    public static final QuestReputationBlock NONE =
            new QuestReputationBlock(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<QuestReputationBlock> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(ReputationOutcome.CODEC, "complete")
                            .forGetter(QuestReputationBlock::complete),
                    StrictCodecs.strictOptional(ReputationOutcome.CODEC, "fail")
                            .forGetter(QuestReputationBlock::fail),
                    StrictCodecs.strictOptional(ReputationOutcome.CODEC, "abandon")
                            .forGetter(QuestReputationBlock::abandon)
            ).apply(instance, QuestReputationBlock::new));

    public QuestReputationBlock {
        complete = complete == null ? Optional.empty() : complete;
        fail = fail == null ? Optional.empty() : fail;
        abandon = abandon == null ? Optional.empty() : abandon;
    }

    public boolean isEmpty() {
        return complete.isEmpty() && fail.isEmpty() && abandon.isEmpty();
    }

    /** The completion outcome, with the generic quest-completed incident filled in when unstated. */
    public Optional<ReputationOutcome> completeOutcome() {
        return complete.map(outcome -> outcome.withDefaultIncident(Incidents.QUEST_COMPLETED));
    }

    public Optional<ReputationOutcome> failOutcome() {
        return fail.map(outcome -> outcome.withDefaultIncident(Incidents.QUEST_FAILED));
    }

    public Optional<ReputationOutcome> abandonOutcome() {
        return abandon.map(outcome -> outcome.withDefaultIncident(Incidents.QUEST_ABANDONED));
    }

    /**
     * The incident type ids Quests names when recording an outcome.
     *
     * <p>Plain {@link ResourceLocation} constants rather than references into MCA: Reputation, because
     * this class is always loaded — with or without that mod installed. An id naming a mod that is not
     * present is just a string; a <em>type</em> from a mod that is not present is a crash.
     */
    public static final class Incidents {

        private Incidents() {
        }

        public static final ResourceLocation QUEST_COMPLETED = rep("quest_completed");
        public static final ResourceLocation QUEST_FAILED = rep("quest_failed");
        public static final ResourceLocation QUEST_ABANDONED = rep("quest_abandoned");
        public static final ResourceLocation PROJECT_PHASE_COMPLETED = rep("project_phase_completed");
        public static final ResourceLocation PROJECT_COMPLETED = rep("project_completed");
        public static final ResourceLocation PROJECT_FAILED = rep("project_failed");
        public static final ResourceLocation SITUATION_RESOLVED = rep("situation_resolved");
        public static final ResourceLocation RESTITUTION_COMPLETED = rep("restitution_completed");

        private static ResourceLocation rep(String path) {
            return ResourceLocation.fromNamespaceAndPath("mcareputation", path);
        }
    }
}
