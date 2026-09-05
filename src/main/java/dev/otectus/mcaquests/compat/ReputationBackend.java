package dev.otectus.mcaquests.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    /** Resolves the newest incident matching the selector. No-op without Reputation. */
    boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                            IncidentSelector selector, String resolution, @Nullable String dedupeKey);

    /** Records a standalone incident with no score of its own. No-op without Reputation. */
    boolean recordIncident(ReputationAward award);

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
