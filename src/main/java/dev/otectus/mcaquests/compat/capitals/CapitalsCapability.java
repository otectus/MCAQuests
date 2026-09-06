package dev.otectus.mcaquests.compat.capitals;

/**
 * One independently-answerable slice of MCA Capitals.
 *
 * <p>The ids are the ones datapacks gate on through {@code mcaquests:compat_capability} with provider
 * {@code mcacapitals}, so they are part of the pack contract and must not be renamed without a
 * migration note. Each is bound or not bound on its own: Capitals can move one data accessor in a
 * point release and disable exactly the feature that read it, rather than the whole integration.
 */
public enum CapitalsCapability {

    /** Capitals themselves: the record registry, the village-to-capital lookup, the display name. */
    REGISTRY("capitals.registry"),

    /** Who holds which villager office, from the sovereign down to a knight. */
    ROLES("capitals.roles"),

    /** What a <em>player</em> holds: a throne, a consort seat, an office, a granted noble title. */
    PLAYER_TITLES("capitals.player_titles"),

    /** Granting a player a noble title. The only player-side mutation this mod performs. */
    TITLE_GRANTS("capitals.title_grants"),

    /** The capital a player has declared allegiance to. */
    ALLEGIANCE("capitals.allegiance"),

    /** The diplomatic state between two capitals. */
    DIPLOMACY("capitals.diplomacy"),

    /** Whether a capital's throne is currently vacant, and who vacated it. */
    INTERREGNUM("capitals.interregnum"),

    /** Writing a line into a capital's chronicle, with or without the herald announcing it. */
    CHRONICLE("capitals.chronicle"),

    /** Raising a villager to knight, lord or duke of a capital. */
    VILLAGER_TITLES("capitals.villager_titles");

    private final String id;

    CapitalsCapability(String id) {
        this.id = id;
    }

    /** The dotted id datapacks and the status command use. */
    public String id() {
        return id;
    }
}
