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
