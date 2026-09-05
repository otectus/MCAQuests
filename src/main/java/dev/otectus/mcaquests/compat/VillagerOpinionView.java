package dev.otectus.mcaquests.compat;

/**
 * What one villager personally makes of a player, expressed <b>only in Java types</b>.
 *
 * <p>The Quests-side mirror of MCA: Reputation's {@code VillagerOpinion}, and it exists for the same
 * reason {@link ReputationBackend} does: the condition that reads an opinion is loaded on every
 * installation, so it must not name a {@code mcareputation} type. {@code CanonicalReputationBackend}
 * — the one class allowed to — translates the real record into this one.
 *
 * <p>Nothing here is stored by Quests. It is a read of Reputation's ledger through what this villager
 * saw, was part of, or has had time to hear about.
 *
 * @param opinion the villager's weighted view of the player, on the same scale as a village score
 * @param tierId  the tier that opinion falls in on the default ladder, e.g. {@code "friend"}
 * @param basis   how the villager came to know anything at all: {@code involved}, {@code witnessed},
 *                {@code hearsay}, or {@code none}
 */
public record VillagerOpinionView(int opinion, String tierId, String basis) {

    public VillagerOpinionView {
        tierId = tierId == null ? "" : tierId;
        basis = basis == null ? "none" : basis;
    }
}
