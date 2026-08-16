package dev.otectus.mcaquests.quest.reputation;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Builds the stable dedupe keys every Quests-created reputation outcome carries (spec §14.2).
 *
 * <h2>Why one class</h2>
 *
 * <p>A dedupe key only works if the two things that must collide spell it identically and the two
 * things that must not spell it differently. That is very easy to get wrong when the strings are
 * assembled inline at eight call sites, and the failure mode is silent: either a player is paid twice
 * for one quest, or a legitimately repeatable quest never pays at all. Centralising the shapes makes
 * both mistakes visible in one file, and lets the test suite assert the collision behaviour directly.
 *
 * <p>The shapes follow §14.2's recommendations. The one rule behind all of them: include everything
 * that makes this a <em>distinct</em> outcome, and nothing that varies between two attempts at the
 * <em>same</em> outcome.
 */
public final class ReputationDedupe {

    private ReputationDedupe() {
    }

    /**
     * A quest outcome. The giver and the start time are both included because the same quest, from a
     * different villager or accepted again later, is a genuinely different piece of work — that is
     * what makes a repeatable quest repeatable while a duplicated turn-in packet is not.
     */
    public static String quest(ResourceLocation questId, @Nullable UUID giver, long startGameTime,
                               String outcome) {
        return "quest:" + questId + ":" + (giver == null ? "none" : giver) + ":" + startGameTime
                + ":" + outcome;
    }

    /** A situation resolution, keyed by the situation instance so each occurrence pays once. */
    public static String situation(UUID instanceId, UUID player, String resolution) {
        return "situation:" + instanceId + ":" + player + ":" + resolution;
    }

    /** One phase of one project instance, per recipient. */
    public static String projectPhase(ResourceLocation projectId, String instanceKey, int phaseIndex,
                                      UUID player) {
        return "project:" + projectId + ":" + instanceKey + ":phase:" + phaseIndex + ":" + player;
    }

    /** A project's terminal outcome, per recipient. */
    public static String projectOutcome(ResourceLocation projectId, String instanceKey, String outcome,
                                        UUID player) {
        return "project:" + projectId + ":" + instanceKey + ":" + outcome + ":" + player;
    }

    /**
     * An FTB Quests reward claim. FTB's own quest ids are stable longs, and a claim is already
     * one-shot on its side, so the id alone identifies the outcome.
     */
    public static String ftbReward(long ftbQuestId, UUID player) {
        return "ftb:" + Long.toHexString(ftbQuestId) + ":" + player;
    }

    /**
     * A banked reward finally delivered after the village it needed could not be resolved at claim
     * time. Keyed by the original claim so a retry loop cannot pay twice.
     */
    public static String bankedDelivery(UUID player, int amount, long bankedAtGameTime) {
        return "banked:" + player + ":" + amount + ":" + bankedAtGameTime;
    }
}
