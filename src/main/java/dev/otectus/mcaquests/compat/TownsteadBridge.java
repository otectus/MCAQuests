package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/**
 * The seam to the optional Townstead integration (Townstead spec §3.4), built to the same discipline
 * as {@link FtbqBridge} and {@link ReputationBridge}.
 *
 * <p><b>Only {@code java.*} and {@code net.minecraft.*} types, plus MCA: Quests' own
 * {@code Townstead*View} records, may appear in this interface.</b> The real implementation lives
 * under {@code compat.townstead} and is reached by name from {@link TownsteadCompat} only after
 * {@code ModList} confirms Townstead is present. Nothing here — nor anything reachable from here
 * without that check — may name a {@code com.aetherianartificer.townstead} type;
 * {@code NoTownsteadStaticLinkTest} enforces it.
 *
 * <p><b>Every method is total.</b> Reads return empty and mutations return a failure
 * {@link TownsteadMutationResult} rather than throwing, because these are called from eligibility
 * passes, polling objectives and reward grants where an exception would take a quest — or a tick —
 * with it. A capability that fails to bind disables its own features and nothing else.
 */
public interface TownsteadBridge {

    /** How much of Townstead bound. {@link TownsteadStatus#ABSENT} when the mod is not installed. */
    TownsteadStatus status();

    /** The capabilities that bound. Empty when Townstead is absent. */
    Set<TownsteadCapability> capabilities();

    /** Townstead's declared mod version, or an empty string when it is not installed. */
    String detectedVersion();

    /**
     * Which MCA package root the installed Townstead was compiled against, when that could be
     * determined — the "modern"/"legacy" distinction, reported for diagnostics only. Empty when
     * Townstead is absent or the root could not be identified; never used to pick a code path.
     */
    Optional<String> variant();

    /** Discards data-derived views on reload/world changes; reflection bindings remain valid. */
    default void invalidateDataCaches() {
    }

    /**
     * Called once by {@link TownsteadCompat#init()} after this bridge has been chosen, for work that
     * must not run in a constructor -- the typed bridge subscribes to Townstead's events here. A
     * throw is caught by the caller, which then calls {@link #onUnbound()} and installs a disabled
     * bridge in this one's place, so an implementation must leave nothing registered on failure.
     */
    default void onBound() {
    }

    /** Releases whatever {@link #onBound()} registered. Idempotent; safe to call after a partial failure. */
    default void onUnbound() {
    }

    // ---------------------------------------------------------------- reads

    Optional<TownsteadVillagerView> villager(Entity entity);

    Optional<TownsteadCalendarView> calendar(MinecraftServer server);

    Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos);

    Optional<TownsteadRootView> root(ResourceLocation id);

    Optional<TownsteadGeneView> gene(ResourceLocation id);

    /**
     * Spirit for one MCA village. Keyed by village id rather than by a resident entity (spec §3.4
     * says {@code spiritForHomeVillage(Entity)}) because MCA: Quests already resolves villages by id
     * through {@code McaCompat}, and situation scans need the spirit of a village whose residents may
     * all be unloaded.
     */
    Optional<TownsteadSpiritView> spiritForVillage(ServerLevel level, int villageId);

    /** The skills this villager has learned. Empty when unavailable — never {@code null}. */
    Set<ResourceLocation> learnedSkills(Entity villager);

    boolean hasSkill(Entity villager, ResourceLocation skillId);

    /**
     * True when Townstead recognises this spirit id. Used to validate bundled and datapack content
     * against the running Townstead rather than against a hardcoded list that could drift.
     */
    boolean isKnownSpirit(String spiritId);

    /**
     * What this profession's progression can actually reach (spec §5.1). Never null: a profession
     * Townstead has no track for answers with a
     * {@link TownsteadProfessionTrackView#progressive() non-progressive} view, which is the whole
     * point — Townstead's registry returns a zero/default spec rather than nothing, and 1.4.0 could
     * not tell that apart from a real track.
     *
     * <p>Callers must check {@link TownsteadCapability#READ_PROFESSION_SPEC} before treating a
     * non-progressive answer as proof: with the capability unbound this returns the same shape, and
     * "we cannot tell" must hide content rather than condemn a track that is really there.
     */
    TownsteadProfessionTrackView professionTrack(String professionId);

    /** True when Townstead's skill registry knows this id. False when the registry is unreadable. */
    boolean isKnownSkill(ResourceLocation skillId);

    /** Every registered skill id, for diagnostics and validation. Empty when unreadable. */
    Set<ResourceLocation> knownSkillIds();

    /**
     * The last state Townstead recorded for a villager, loaded or not, from its resident register.
     * Empty for a Townstead that keeps no register (0.7.x), for a villager it has never ticked, and
     * whenever the capability is unbound. The reading carries its own age and membership, and the
     * caller decides whether it is evidence enough -- see {@code TownsteadResidentEvidence}.
     */
    default Optional<TownsteadResidentRecordView> lastKnownResident(MinecraftServer server, java.util.UUID villager) {
        return Optional.empty();
    }

    // ------------------------------------------------------------ mutations

    TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation);

    TownsteadMutationResult awardProfessionXp(Entity villager, String professionId, int requestedXp,
                                              boolean respectDailyCap);

    TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force);

    TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId);

    /**
     * Play Townstead's reaction for a lifecycle transition. Called <em>after</em> the MCA: Quests
     * transaction has committed, so a failure here is cosmetic and never rolls anything back.
     */
    TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                               ResourceLocation taskId, String phase);

    // ------------------------------------------------------------ convenience

    default boolean has(TownsteadCapability capability) {
        return capabilities().contains(capability);
    }

    /** True when Townstead is installed and at least its baseline facade bound. */
    default boolean isAvailable() {
        return status() == TownsteadStatus.FULL || status() == TownsteadStatus.PARTIAL;
    }

    /**
     * How this bridge reaches Townstead: {@code "reflective"} for the by-name binding used on
     * Townstead 0.7.x, {@code "api-v1"} for the typed adapter over Townstead's public API, or a
     * short reason when neither could be used. Diagnostics only; never used to pick a code path.
     */
    default String bindingPath() {
        return "reflective";
    }

    /**
     * Members that were expected but did not bind, named for a bug report. Empty when everything
     * bound and when Townstead is absent -- an absent mod is not a partial binding.
     */
    default java.util.List<String> unresolvedMembers() {
        return java.util.List.of();
    }

    final class Holder {
        private static volatile TownsteadBridge instance = NoopTownsteadBridge.INSTANCE;

        private Holder() {
        }

        public static TownsteadBridge get() {
            return instance;
        }

        /**
         * Set once from {@link TownsteadCompat#init()}. Public rather than package-private because
         * the caller could legitimately live in another package; last writer wins.
         */
        public static void set(TownsteadBridge bridge) {
            instance = bridge == null ? NoopTownsteadBridge.INSTANCE : bridge;
        }
    }
}
