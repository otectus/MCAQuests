package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCA Capitals, as the rest of MCA: Quests is allowed to see it.
 *
 * <p><b>Only {@code java.*}, {@code net.minecraft.*} and this package's own small records appear in
 * these signatures.</b> Conditions, the {@code capital_role} villager target, the three rewards and
 * the situation poller all ask the bridge and never see a Capitals type, so "the mod is absent", "the
 * integration is switched off" and "this Capitals build moved a method" collapse to the same
 * observable answer — empty, or false — in one place.
 *
 * <p>Every method is total and cheap. None throws; a Capitals call that fails is logged once at debug
 * and answers as if the capability were missing, because a quest that quietly does not advance is a
 * far smaller failure than a tick handler that throws once a second.
 */
public interface CapitalsBridge {

    /** How much of Capitals is reachable right now. */
    CompatStatus status();

    /** Every capability this bridge can answer for, present or not. Never empty. */
    List<CompatCapability> capabilities();

    /** Whether one capability is available right now. */
    boolean has(CapitalsCapability capability);

    /**
     * The manifest members that did not bind, for {@code /mcaquests compat capitals status}. Empty on
     * a bridge that never tried, which is the same answer as "everything bound" and is why the status
     * line prints the capability table beside it.
     */
    default List<String> unresolvedMembers() {
        return List.of();
    }

    // --- the registry ----------------------------------------------------------------------------

    /**
     * The capital seated in {@code villageId}, if any.
     *
     * <p>{@code level} must be the level the village is in — Capitals filters by dimension, so passing
     * the overworld for a village elsewhere silently answers empty.
     */
    Optional<CapitalRef> capitalForVillage(ServerLevel level, int villageId);

    /** Every capital Capitals currently holds, in no defined order. Empty until its data loads. */
    List<CapitalRef> allCapitals();

    /** The level a capital is seated in, or empty when that dimension is gone. */
    Optional<ServerLevel> capitalLevel(MinecraftServer server, CapitalRef capital);

    /** The capital that counts {@code residentId} as one of its own, if one does. */
    Optional<CapitalRef> capitalOfResident(UUID residentId);

    /**
     * Whether a capital is live — Capitals' {@code ACTIVE} state, not merely founded. A pending or
     * founded capital has no court to speak of, so content is gated on this rather than on existence.
     */
    boolean isActive(CapitalRef capital);

    /** A capital's display name, when Capitals can resolve one. */
    Optional<String> displayName(ServerLevel level, CapitalRef capital, UUID entityId);

    // --- who holds what --------------------------------------------------------------------------

    /**
     * The villagers holding {@code role} in this capital. A single-seat office returns zero or one
     * entry; {@link CapitalRole#MEMBER} returns the union of every villager role, the ambassador
     * included. A throne held by a <em>player</em> contributes nothing here — ask
     * {@link #playerHasRole} for that.
     */
    List<UUID> villagerRoleHolders(ServerLevel level, CapitalRef capital, CapitalRole role);

    /** Whether {@code villager} holds {@code role} in this capital. */
    boolean villagerHasRole(ServerLevel level, CapitalRef capital, UUID villager, CapitalRole role);

    /**
     * Whether {@code player} holds {@code role} in this capital.
     *
     * <p>A player throne is a separate pair of fields from the villager sovereign, and a player's
     * knighthood is a granted {@code NobleTitle} rather than a seat, so this asks entirely different
     * questions from {@link #villagerHasRole} and the two are never interchangeable. The gendered
     * pairs count as one role: a dame holds {@link CapitalRole#KNIGHT}, a duchess
     * {@link CapitalRole#DUKE}.
     */
    boolean playerHasRole(ServerLevel level, CapitalRef capital, UUID player, CapitalRole role);

    /** The capital a player has declared allegiance to, if any. */
    Optional<UUID> declaredAllegiance(ServerLevel level, UUID player);

    // --- diplomacy and succession -----------------------------------------------------------------

    /**
     * The relation between two capitals as an upper-case name — {@code PEACE},
     * {@code NON_AGGRESSION_PACT}, {@code ALLIANCE}, {@code TRUCE}, {@code WAR}. A string rather than
     * an enum of our own so a Capitals release that adds a state widens the vocabulary instead of
     * breaking the read.
     */
    Optional<String> diplomaticState(ServerLevel level, UUID first, UUID second);

    /** Every capital with a vacant throne right now, keyed by capital id. */
    Map<UUID, InterregnumView> interregnums(ServerLevel level);

    /** This capital's vacant throne, if its throne is vacant. */
    Optional<InterregnumView> interregnum(ServerLevel level, CapitalRef capital);

    // --- the three mutations ----------------------------------------------------------------------

    /**
     * Grants a player a noble title, named by Capitals' own constant ({@code "KNIGHT"},
     * {@code "DAME"}, {@code "LORD"} …). Returns whether the grant happened. Capitals marks its own
     * saved data dirty, so nothing follows this.
     */
    boolean grantPlayerTitle(ServerLevel level, CapitalRef capital, UUID player, String nobleTitleName);

    /**
     * Raises a villager to {@code "KNIGHT"}, {@code "LORD"} or {@code "DUKE"} of this capital, in the
     * gendered form Capitals stores. Returns whether it happened; the capital's saved data is marked
     * dirty afterwards.
     */
    boolean setVillagerTitle(ServerLevel level, CapitalRef capital, UUID villager, String nobleTitleName);

    /**
     * Writes one line into a capital's chronicle. {@code herald} announces it in the world as Capitals'
     * own entries are announced; the entry is flat, server-locale text de-duplicated on the exact
     * string, so the caller renders it before getting here. Marks the saved data dirty afterwards.
     */
    boolean addChronicleEntry(ServerLevel level, CapitalRef capital, String entry, boolean herald);

    // --- gender, for the gendered titles ----------------------------------------------------------

    /** Whether MCA considers this player female, or empty when Capitals cannot say. */
    Optional<Boolean> isPlayerFemale(ServerLevel level, ServerPlayer player);

    /** Whether MCA considers this villager female, or empty when Capitals cannot say. */
    Optional<Boolean> isVillagerFemale(ServerLevel level, UUID villager);
}
