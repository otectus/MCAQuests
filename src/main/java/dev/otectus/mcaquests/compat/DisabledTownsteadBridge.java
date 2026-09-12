package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The bridge installed when Townstead is present but must not be bound: a Townstead that ships the
 * public API but for which this build has no working typed adapter. Reflection is deliberately
 * <em>not</em> tried in its place -- the by-name binding was written against 0.7.x internals and
 * writes through it would bypass the write policy Townstead's API enforces -- so the integration
 * reports {@link TownsteadStatus#DISABLED} with the reason, and every read is empty and every
 * mutation refuses with {@link TownsteadMutationResult.Reason#CAPABILITY_MISSING}.
 */
public final class DisabledTownsteadBridge implements TownsteadBridge {

    private final String version;
    private final String reason;

    public DisabledTownsteadBridge(String version, String reason) {
        this.version = version == null ? "" : version;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public TownsteadStatus status() {
        return TownsteadStatus.DISABLED;
    }

    @Override
    public Set<TownsteadCapability> capabilities() {
        return Set.of();
    }

    @Override
    public String detectedVersion() {
        return version;
    }

    @Override
    public Optional<String> variant() {
        return Optional.empty();
    }

    @Override
    public String bindingPath() {
        return "disabled: " + reason;
    }

    /**
     * Empty on purpose: nothing failed to bind, the binding was refused, and the reason is already in
     * {@link #bindingPath()}. Listing it here would make the status command ask for a bug report
     * about a deliberate choice.
     */
    @Override
    public List<String> unresolvedMembers() {
        return List.of();
    }

    @Override
    public Optional<TownsteadVillagerView> villager(Entity entity) {
        return Optional.empty();
    }

    @Override
    public Optional<TownsteadCalendarView> calendar(MinecraftServer server) {
        return Optional.empty();
    }

    @Override
    public Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos) {
        return Optional.empty();
    }

    @Override
    public Optional<TownsteadRootView> root(ResourceLocation id) {
        return Optional.empty();
    }

    @Override
    public Optional<TownsteadGeneView> gene(ResourceLocation id) {
        return Optional.empty();
    }

    @Override
    public Optional<TownsteadSpiritView> spiritForVillage(ServerLevel level, int villageId) {
        return Optional.empty();
    }

    @Override
    public Set<ResourceLocation> learnedSkills(Entity villager) {
        return Set.of();
    }

    @Override
    public boolean hasSkill(Entity villager, ResourceLocation skillId) {
        return false;
    }

    @Override
    public boolean isKnownSpirit(String spiritId) {
        return false;
    }

    @Override
    public TownsteadProfessionTrackView professionTrack(String professionId) {
        return TownsteadProfessionTrackView.none(professionId == null ? "" : professionId);
    }

    @Override
    public boolean isKnownSkill(ResourceLocation skillId) {
        return false;
    }

    @Override
    public Set<ResourceLocation> knownSkillIds() {
        return Set.of();
    }

    @Override
    public TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }

    @Override
    public TownsteadMutationResult awardProfessionXp(Entity villager, String professionId, int requestedXp,
                                                     boolean respectDailyCap) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }

    @Override
    public TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }

    @Override
    public TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }

    @Override
    public TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                      ResourceLocation taskId, String phase) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }
}
