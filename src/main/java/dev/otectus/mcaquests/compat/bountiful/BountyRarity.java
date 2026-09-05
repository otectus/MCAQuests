package dev.otectus.mcaquests.compat.bountiful;

import java.util.Locale;

/**
 * How rare a Bountiful bounty is, as <b>MCA: Quests</b> understands it.
 *
 * <p>Deliberately our own enum rather than a reference to Bountiful's. Naming theirs would link us to
 * a mod that may not be installed, and — more to the point — would make our datapack contract depend
 * on their enum: a rarity added or renamed upstream would change what {@code "min_rarity": "RARE"}
 * means in a pack that has not changed. The five names mirror Bountiful 6.0.4 and are matched by
 * name at runtime.
 *
 * <p>{@link #UNKNOWN} is the sixth value and the one that carries the design. A bounty whose rarity
 * we could not read is not "common"; it is a bounty we cannot answer questions about. Treating it as
 * the lowest rank would let a "complete a rare contract" quest be satisfied by anything the moment
 * the rarity reader failed to bind, which is exactly the silent wrong answer this enum exists to
 * prevent.
 */
public enum BountyRarity {

    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,

    /** The rarity could not be read. Never satisfies a minimum; see {@link #atLeast}. */
    UNKNOWN;

    /** Bountiful's own name for this rarity, case-insensitively; anything else is {@link #UNKNOWN}. */
    public static BountyRarity fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        for (BountyRarity rarity : values()) {
            if (rarity != UNKNOWN && rarity.name().equals(upper)) {
                return rarity;
            }
        }
        return UNKNOWN;
    }

    /**
     * True when this rank meets {@code min}.
     *
     * <p>{@link #UNKNOWN} never meets a minimum, and no minimum is ever met by comparing against
     * {@code UNKNOWN} either — "at least unknown" is not a question with an answer, so it is false
     * rather than vacuously true.
     */
    public boolean atLeast(BountyRarity min) {
        if (this == UNKNOWN || min == UNKNOWN) {
            return false;
        }
        return ordinal() >= min.ordinal();
    }

    /** The key a player-facing line uses for this rank. */
    public String translationKey() {
        return "mcaquests.bountiful.rarity." + name().toLowerCase(Locale.ROOT);
    }
}
