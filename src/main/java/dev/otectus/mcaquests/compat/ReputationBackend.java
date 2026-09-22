package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything MCA: Quests needs to know about a player's public standing, expressed <b>only in
 * Minecraft and Java types</b>.
 *
 * <p>That restriction is the whole point. This interface is always loaded — it is on the class path
 * of every Quests installation, with or without MCA: Reputation — so it must not name a single
 * {@code mcareputation} type. The implementation that <em>does</em> name them lives under
 * {@code compat.reputation} and is only ever constructed after {@code ModList.get().isLoaded(...)}
 * says so (spec §29.1).
 *
 * <p>Two implementations exist:
 *
 * <ul>
 *   <li>{@link LegacyReputationBackend} — Quests' own store, used when Reputation is absent. Since
 *       1.1.0 it is per player and dimension-aware, so a Quests-only server behaves correctly in
 *       multiplayer instead of sharing one number between everybody (§29.2).</li>
 *   <li>{@code CanonicalReputationBackend} — delegates to MCA: Reputation, which owns the canonical
 *       state whenever it is installed (§10).</li>
 * </ul>
 *
 * <h2>Communities are dimension-aware</h2>
 *
 * <p>Every method takes a {@code dimension} alongside a {@code villageId}. MCA allocates village ids
 * per level, so a bare integer names two different places in a world with a Nether village. Quests'
 * historic {@code "v:<id>"} scope strings have exactly that defect; §32.2 migrates them by assuming
 * the overworld, which is the only thing they could have meant in practice.
 */
public interface ReputationBackend {

    /** True when MCA: Reputation is installed and this backend delegates to it. */
    boolean isCanonical();

    /** A short name for logs and {@code /mcaquests debug}. */
    String backendName();

    /**
     * Whether the live backend advertises one of the {@link ReputationFeatures} capability strings
     * <em>right now</em>.
     *
     * <p>The replacement for reflecting over API methods (1.7.0). MCA: Reputation publishes its
     * capability set through {@code capabilities(server)}, and the 0.6.0 profile rows appear only
     * while profiles can actually answer — so this is a runtime readiness question rather than a "does
     * the binary have the method" question, and it is asked again after a world change rather than
     * cached for the life of the JVM.
     *
     * <p>Quests' own store advertises nothing: it has no incident ledger, no receipts and no profiles,
     * and claiming otherwise would make content that depends on them look available.
     */
    default boolean supportsFeature(MinecraftServer server, String feature) {
        return false;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /** This player's standing with one village. {@code 0} when there is no record. */
    int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId);

    /** The current tier id on {@code ladder}, or the floor tier's id. Never empty. */
    String tierId(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                  ResourceLocation ladder);

    /** The current tier's ladder index, or {@code -1} when the ladder is unknown. */
    int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                  ResourceLocation ladder);

    /**
     * Every village in this dimension this player has standing with, as {@code villageId → score}.
     * Backs the FTB tasks, which ask "is the player at N reputation with any village".
     */
    Map<Integer, Integer> villageScores(MinecraftServer server, UUID player, ResourceLocation dimension);

    /** Highest tier ever reached here, or empty. Used by the Journal and the fallback mirror. */
    Optional<String> tierHighWater(MinecraftServer server, UUID player, ResourceLocation dimension,
                                   int villageId, ResourceLocation ladder);

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /**
     * Records one reputation outcome and returns the player's resulting score.
     *
     * <p>Idempotent on {@link ReputationAward#dedupeKey()}: applying the same key twice for the same
     * player and village changes nothing and returns the existing score. That is what makes a
     * duplicated quest turn-in packet, a doubled event, or a relog mid-claim harmless (§14.2).
     */
    int award(ReputationAward award);

    /**
     * Records one reputation outcome and returns the ledger's <b>typed</b> answer (1.7.0).
     *
     * <p>{@link #award} and {@link #recordIncident} are the two shorthands over this, kept because
     * most call sites only want the resulting score or a boolean. A caller that has to tell an
     * already-settled operation from a refusal, or a refusal it may retry from one it may not, asks
     * here — see {@link ReputationDeliveryResult} for why that distinction is not cosmetic.
     */
    default ReputationDeliveryResult deliver(ReputationAward award) {
        return ReputationDeliveryResult.applied(award(award));
    }

    /** @return true when newly granted. */
    boolean grantTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                       int villageId, ResourceLocation title, boolean global);

    boolean hasTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                     int villageId, ResourceLocation title, boolean global);

    Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player);

    Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player, ResourceLocation dimension,
                                        int villageId);

    // ------------------------------------------------------------------
    // Incidents — only meaningful on the canonical backend
    // ------------------------------------------------------------------

    /**
     * Whether the player has an incident matching this selector.
     *
     * <p>The legacy backend has no incident ledger and answers {@code false}, which is the correct
     * degradation: a quest gated on "you assaulted somebody here" simply never offers itself on a
     * Quests-only install rather than offering itself unconditionally (§35.1).
     */
    boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                        IncidentSelector selector);

    /**
     * The same question asked on behalf of a named giver (1.7.0).
     *
     * <p>A selector may ask for deeds {@linkplain IncidentSelector#knownToGiver() the giver actually
     * knows about}, and that cannot be answered without knowing who the giver is. Until 1.7.0 Quests
     * set the flag and supplied nobody, which MCA: Reputation 0.4.1 onward correctly answers with
     * nothing — so a restitution quest gated on "they know what you did" never offered itself. Passing
     * the entity lets Reputation resolve the villager's residency and knowledge itself.
     *
     * <p>A {@code null} giver with {@code known_to_giver} set stays unanswerable, deliberately: the
     * alternative is the village-wide answer, which is a different and much more permissive question.
     */
    default boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                int villageId, IncidentSelector selector, @Nullable Entity giver) {
        return hasIncident(server, player, dimension, villageId, selector);
    }

    /** Resolves the newest incident matching the selector. No-op without Reputation. */
    boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                            IncidentSelector selector, String resolution, @Nullable String dedupeKey);

    /**
     * The same resolution, with the giver whose knowledge the selector may depend on (1.7.0).
     *
     * <p>Also the overload that honours {@code dedupeKey}: the canonical backend binds the discovered
     * incident id and settles it under that key, so a retry after a crash cannot resolve a
     * <em>different</em> deed than the one the reward was granted for.
     */
    default boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                    int villageId, IncidentSelector selector, String resolution,
                                    @Nullable String dedupeKey, @Nullable Entity giver) {
        return resolveIncident(server, player, dimension, villageId, selector, resolution, dedupeKey);
    }

    /** Records a standalone incident with no score of its own. No-op without Reputation. */
    boolean recordIncident(ReputationAward award);

    // ------------------------------------------------------------------
    // Public profiles — only meaningful on the canonical backend (0.6.0)
    // ------------------------------------------------------------------

    /**
     * Whether the player's public profile satisfies an authored predicate.
     *
     * <p>Answers {@link ReputationProfileMatch.Availability#UNSUPPORTED} on the legacy backend, which
     * is not the same as answering "no": a pack may author what should happen when nobody can tell
     * (see {@code mcareputation:profile}'s {@code on_unavailable}), and that choice belongs to the
     * author rather than to this method.
     *
     * @param giver the villager whose own knowledge answers a {@code giver}-scoped query; ignored for
     *              a {@code community}-scoped one, and an unresolvable giver makes a
     *              {@code giver}-scoped query unanswerable rather than community-wide
     */
    default ReputationProfileMatch matchesProfile(MinecraftServer server, UUID player,
                                                  ResourceLocation dimension, int villageId,
                                                  @Nullable Entity giver,
                                                  ReputationProfileQuery query) {
        return ReputationProfileMatch.unsupported();
    }

    // ------------------------------------------------------------------
    // Per-villager opinion — only meaningful on the canonical backend
    // ------------------------------------------------------------------

    /**
     * What this one villager personally makes of the player, as opposed to what the village records.
     *
     * <p>Empty on the legacy backend, and empty on a canonical backend talking to a MCA: Reputation
     * that predates the opinion API — Quests only ever asks, it never assumes. A condition built on
     * this degrades the same way the incident conditions do: unmet rather than unconditionally met, so
     * a quest gated on "this villager saw it happen" never offers itself where nobody can tell.
     */
    default Optional<VillagerOpinionView> villagerOpinion(MinecraftServer server, UUID player, UUID villager,
                                                          ResourceLocation dimension, int villageId) {
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    /**
     * Opens the standing screen for this player and village. Without Reputation there is no such
     * screen, and the Journal simply does not offer the button (§29.7).
     */
    default boolean openStandingScreen(ServerPlayer player, ResourceLocation dimension, int villageId) {
        return false;
    }
}
