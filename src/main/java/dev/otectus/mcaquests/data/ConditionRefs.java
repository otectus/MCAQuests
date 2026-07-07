package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.AnyOfCondition;
import dev.otectus.mcaquests.quest.condition.composite.NotCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestAbandonedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestFailedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestNotCompletedCondition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Static introspection of a quest's condition tree for chain validation. Side-effect-free and free of
 * Minecraft runtime types, so it is directly unit-testable.
 *
 * <p>{@link #required} returns only the quest-state references that are <em>conjunctively required</em> for
 * the gate to pass — reachable from the root through {@code all_of}/{@code not} but never through an
 * {@code any_of} (whose branches are optional alternatives). This makes the downstream "impossible gate"
 * checks <b>sound</b>: they never flag a reference that isn't truly required, so authors don't get false
 * positives on legitimate branching arcs.
 */
public final class ConditionRefs {

    /**
     * A required quest-state reference. {@code polarity} is the truth the gate demands of the predicate:
     * for {@link Kind#COMPLETED}, {@code true} means "must be completed" and {@code false} means "must be
     * not-completed" (a {@code quest_not_completed} leaf, or a negated {@code quest_completed}).
     */
    public record Ref(Kind kind, ResourceLocation quest, HistoryScope scope, boolean polarity) {
        public enum Kind { COMPLETED, FAILED, ABANDONED }
    }

    private ConditionRefs() {
    }

    /** The conjunctively-required quest-state references of {@code condition} (empty when absent). */
    public static List<Ref> required(Optional<QuestCondition> condition) {
        List<Ref> out = new ArrayList<>();
        condition.ifPresent(c -> collectRequired(c, true, out));
        return out;
    }

    private static void collectRequired(QuestCondition c, boolean polarity, List<Ref> out) {
        if (c instanceof AllOfCondition all) {
            all.conditions().forEach(child -> collectRequired(child, polarity, out));
        } else if (c instanceof NotCondition not) {
            collectRequired(not.condition(), !polarity, out);
        } else if (c instanceof AnyOfCondition) {
            // disjunction — nothing inside is individually required, so it contributes nothing.
        } else if (c instanceof QuestCompletedCondition qc) {
            out.add(new Ref(Ref.Kind.COMPLETED, qc.quest(), qc.scope(), polarity));
        } else if (c instanceof QuestNotCompletedCondition qnc) {
            out.add(new Ref(Ref.Kind.COMPLETED, qnc.quest(), qnc.scope(), !polarity));
        } else if (c instanceof QuestFailedCondition qf) {
            out.add(new Ref(Ref.Kind.FAILED, qf.quest(), qf.scope(), polarity));
        } else if (c instanceof QuestAbandonedCondition qa) {
            out.add(new Ref(Ref.Kind.ABANDONED, qa.quest(), qa.scope(), polarity));
        }
    }

    /** Every quest id referenced anywhere in the tree (including {@code any_of} branches) — for existence checks. */
    public static List<ResourceLocation> allReferencedQuests(Optional<QuestCondition> condition) {
        List<ResourceLocation> out = new ArrayList<>();
        condition.ifPresent(c -> collectAll(c, out));
        return out;
    }

    private static void collectAll(QuestCondition c, List<ResourceLocation> out) {
        if (c instanceof AllOfCondition all) {
            all.conditions().forEach(child -> collectAll(child, out));
        } else if (c instanceof AnyOfCondition any) {
            any.conditions().forEach(child -> collectAll(child, out));
        } else if (c instanceof NotCondition not) {
            collectAll(not.condition(), out);
        } else if (c instanceof QuestCompletedCondition qc) {
            out.add(qc.quest());
        } else if (c instanceof QuestNotCompletedCondition qnc) {
            out.add(qnc.quest());
        } else if (c instanceof QuestFailedCondition qf) {
            out.add(qf.quest());
        } else if (c instanceof QuestAbandonedCondition qa) {
            out.add(qa.quest());
        }
    }

    /**
     * Whether the tree contains an outcome-branch leaf ({@code quest_failed} / {@code quest_abandoned} /
     * {@code quest_not_completed}) anywhere — the marker that a same-stage quest is a mutually-exclusive
     * branch rather than a duplicate.
     */
    public static boolean hasOutcomeBranch(Optional<QuestCondition> condition) {
        return condition.map(ConditionRefs::scanOutcome).orElse(false);
    }

    private static boolean scanOutcome(QuestCondition c) {
        if (c instanceof AllOfCondition all) {
            return all.conditions().stream().anyMatch(ConditionRefs::scanOutcome);
        }
        if (c instanceof AnyOfCondition any) {
            return any.conditions().stream().anyMatch(ConditionRefs::scanOutcome);
        }
        if (c instanceof NotCondition not) {
            return scanOutcome(not.condition());
        }
        return c instanceof QuestFailedCondition
                || c instanceof QuestAbandonedCondition
                || c instanceof QuestNotCompletedCondition;
    }
}
