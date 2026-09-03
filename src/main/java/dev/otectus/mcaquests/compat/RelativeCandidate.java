package dev.otectus.mcaquests.compat;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * One relative of a quest giver, with everything any caller needs to decide whether they are a person a
 * quest may be about.
 *
 * <p>This type exists because the mod used to ask that question in five places and get five answers. The
 * condition gate asked "does a sibling exist with status {@code same_village}?"; the objective's target
 * then asked {@code findGiverRelative} for "the first sibling in list order", with no status filter, no
 * deceased filter and no existence filter at all. One sibling could satisfy the gate while a completely
 * different one was handed to the objective — and because MCA never removes the dead from a village's
 * resident roll, the gate counted the departed. That is how a player was asked to deliver a letter to a
 * brother who had died the week before.
 *
 * <p>Now the gate, the offer-time resolvability check, the accept-time binder, {@code matches} and the
 * display name all filter the <em>same</em> candidate list.
 *
 * <p>They do <b>not</b> all use the same predicate, and the comment that used to claim they did was
 * describing an intention rather than the code. Two of the seven statuses are partly <em>positional</em>
 * — {@code nearby} is "within 12 blocks of the giver right now" — and a positional predicate re-asked
 * at a different instant is a different question, not the same one. Selection asks
 * {@link #matches(String)}; identity, which must survive the walk from the giver to the recipient,
 * asks {@link #matchesIdentity(String)}. See that method for what separates them and why.
 *
 * <p><b>Only JDK types.</b> Candidates are built inside {@link McaCompat} from MCA's family tree, but no
 * MCA type may escape that class ({@code NoMcaStaticLinkTest} enforces it), so everything here is a
 * primitive, a {@link UUID} or a {@link String}.
 *
 * <p><b>The status switch is pure.</b> {@link #matches(String)} takes no level, no entity and no MCA
 * handle, which is what lets it be table-driven in a unit test — MCA is deliberately absent from the test
 * runtime, so a predicate tangled up with the reflective walk could not be tested at all.
 *
 * @param uuid             the relative's MCA family-tree id
 * @param relation         the relation that produced this candidate (spouse/parent/child/sibling/grandparent)
 * @param name             MCA's persistent name for them, or {@code null} when it cannot be read
 * @param nodeKnown        MCA's family tree has a node under this id
 * @param deceased         the node is flagged deceased
 * @param generated        a filler ancestor MCA invented to pad a family tree, never a person who can be found
 * @param player           the node belongs to a player, not a villager
 * @param embodied         an entity with this id exists in the world right now (alive or not)
 * @param loaded           that entity exists <em>and</em> is alive
 * @param nearby           loaded and within interaction range of the giver
 * @param sameVillage      on the giver's own home village resident roll
 * @param residentAnywhere on the resident roll of any village in the level
 * @param materialisable   {@code McaCompat.materializeRelative} would agree to bring them into the world
 * @param infected         loaded and part-way through MCA's zombie infection right now
 */
public record RelativeCandidate(UUID uuid,
                                String relation,
                                @Nullable String name,
                                boolean nodeKnown,
                                boolean deceased,
                                boolean generated,
                                boolean player,
                                boolean embodied,
                                boolean loaded,
                                boolean nearby,
                                boolean sameVillage,
                                boolean residentAnywhere,
                                boolean materialisable,
                                boolean infected) {

    /**
     * The pre-{@code infected} shape, so every caller that describes a relative without reading MCA's
     * infection progress keeps compiling. An absent value means "not infected", which is the answer that
     * makes a cure quest ineligible rather than accidentally offered.
     */
    public RelativeCandidate(UUID uuid, String relation, @Nullable String name, boolean nodeKnown,
                             boolean deceased, boolean generated, boolean player, boolean embodied,
                             boolean loaded, boolean nearby, boolean sameVillage, boolean residentAnywhere,
                             boolean materialisable) {
        this(uuid, relation, name, nodeKnown, deceased, generated, player, embodied, loaded, nearby,
                sameVillage, residentAnywhere, materialisable, false);
    }

    /**
     * Every status a datapack may name, in {@code related_villager_status} or in a villager target's
     * {@code require}. The two vocabularies are the same set on purpose: a quest must be able to gate on
     * exactly the question its objective then asks.
     */
    public static final Set<String> STATUSES =
            Set.of("alive", "reachable", "nearby", "missing", "dead", "same_village", "any_known", "infected");

    /**
     * The statuses that assert the relative can actually be found. A target requiring one of these needs a
     * gate; a target requiring {@code dead}, {@code missing} or {@code any_known} deliberately does not.
     */
    public static final Set<String> EXISTENCE_STATUSES =
            Set.of("alive", "reachable", "nearby", "same_village", "infected");

    /** The default {@code require} for a {@code mode: family} target: the safe answer, not the loose one. */
    public static final String DEFAULT_FAMILY_REQUIRE = "reachable";

    /** A real person who is not dead — the floor every other status but {@code dead} builds on. */
    public boolean isAlive() {
        return nodeKnown && !deceased && !generated && !player;
    }

    /**
     * A real person a quest can send the player to: either they are standing in a loaded chunk, or a
     * village roll says where they live. Deliberately excludes the genuinely missing — nothing but
     * {@code find_missing_relative} materialises anyone, so "go and give this to them" would never finish.
     */
    public boolean isReachable() {
        return isAlive() && (loaded || residentAnywhere);
    }

    /**
     * Genuinely vanished rather than merely out of render distance. {@code ServerLevel#getEntity} only
     * sees loaded entities, so without the residency check a villager standing in an unloaded village
     * reads as missing — and a quest that materialises missing kin would then spawn a second copy of
     * someone alive and well.
     */
    public boolean isMissing() {
        return isAlive() && !embodied && !residentAnywhere;
    }

    /**
     * True when this candidate is <em>the person</em> {@code status} describes, ignoring where they
     * happen to be standing this tick.
     *
     * <p>{@link #matches} answers a selection question — "who may this quest be about?" — and two of
     * its statuses answer it partly with a position: {@code nearby} is
     * {@code loaded && within 12 blocks of the giver}. That is the right question at offer time and
     * the wrong one at every later moment, because the player's next move is to walk away from the
     * giver to find this person. Asking it again when the goods are handed over is asking whether the
     * recipient is standing beside the villager the player just left, which is the one thing the
     * player has guaranteed is false.
     *
     * <p>So this drops the distance test from {@code nearby} and keeps everything else — alive, not a
     * fabricated ancestor, not a player, and findable — which is the half of the status that is about
     * identity rather than geography. Every other status is already position-free (or, for
     * {@code reachable}, deliberately tolerant of being unloaded) and answers identically.
     *
     * <p>Used for two things only: binding a target when the moment the offer described has passed,
     * and crediting an objective that was never bound. Selection itself still uses {@link #matches},
     * so which quests are offered is unchanged.
     */
    public boolean matchesIdentity(String status) {
        return "nearby".equals(status) ? isReachable() : matches(status);
    }

    /** True when this candidate satisfies {@code status}. Unknown statuses fail closed. */
    public boolean matches(String status) {
        return switch (status) {
            case "alive" -> isAlive();
            case "reachable" -> isReachable();
            case "nearby" -> isAlive() && nearby;
            case "missing" -> isMissing();
            // A fabricated ancestor is not a bereavement, so a memorial quest can never be about one.
            case "dead" -> deceased && !generated;
            // The roll alone is not enough: MCA leaves the dead on it forever, which is the whole bug.
            case "same_village" -> isAlive() && sameVillage;
            case "any_known" -> nodeKnown;
            // Infection is only readable off a loaded body, so this is deliberately the narrowest
            // existence status: a cure quest must not be offered about kin nobody can see turning.
            case "infected" -> isAlive() && infected;
            default -> false;
        };
    }
}
