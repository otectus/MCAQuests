package dev.otectus.mcaquests.compat.capitals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * An office or rank inside a capital, as MCA: Quests names it.
 *
 * <p>Capitals holds these as a mixture of single UUID fields on its capital record, sets of UUIDs,
 * and a {@code NobleTitle} enum for players — three shapes with no common vocabulary. This is that
 * vocabulary: one name a datapack can write, that the bridge then answers for on whichever side of
 * Capitals it lives.
 *
 * <p>{@link #MEMBER} is the union — anyone the capital counts as one of its own — and doubles as the
 * {@code "any"} spelling a pack is likely to reach for first.
 */
public enum CapitalRole {

    SOVEREIGN,
    CONSORT,
    DOWAGER,
    HEIR,
    ROYAL_CHILD,
    HAND,
    COMMANDER,
    HERALD,
    GRAND_MAESTER,
    MASTER_OF_LAWS,
    AMBASSADOR,
    DUKE,
    LORD,
    KNIGHT,
    ROYAL_GUARD,
    /** Player-only: Capitals grants it as a noble title and never seats a villager in it. */
    ARCHDUKE,
    /** Anyone the capital counts as one of its own. Spelled {@code "any"} as well as {@code "member"}. */
    MEMBER;

    /** The roles a villager can hold — everything but {@link #ARCHDUKE}. */
    private static final Set<CapitalRole> VILLAGER_ROLES =
            EnumSet.complementOf(EnumSet.of(ARCHDUKE));

    /** The roles a player can hold: two thrones, two offices, the granted titles, and the union. */
    private static final Set<CapitalRole> PLAYER_ROLES =
            EnumSet.of(SOVEREIGN, CONSORT, HAND, COMMANDER, KNIGHT, LORD, DUKE, ARCHDUKE, MEMBER);

    /** The roles Capitals stores as a set, so more than one villager can hold them at once. */
    private static final Set<CapitalRole> SET_VALUED =
            EnumSet.of(ROYAL_CHILD, DUKE, LORD, KNIGHT, ROYAL_GUARD);

    /**
     * Accepts the lower-case name, plus {@code "any"} as an alias of {@link #MEMBER} — the word a
     * pack author writes when they mean "belongs to this capital at all".
     */
    public static final Codec<CapitalRole> CODEC = Codec.STRING.flatXmap(
            s -> {
                String value = s.toLowerCase(Locale.ROOT);
                if (value.equals("any")) {
                    return DataResult.success(MEMBER);
                }
                try {
                    return DataResult.success(CapitalRole.valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown capital role: " + s);
                }
            },
            role -> DataResult.success(role.key()));

    /** Whether a villager can hold this role. */
    public boolean appliesToVillager() {
        return VILLAGER_ROLES.contains(this);
    }

    /** Whether a player can hold this role. */
    public boolean appliesToPlayer() {
        return PLAYER_ROLES.contains(this);
    }

    /** Whether more than one villager can hold this role at once. */
    public boolean setValued() {
        return SET_VALUED.contains(this);
    }

    /** The lower-case name, for JSON and for the {@code mcaquests.target.capital_role.*} keys. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
