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
 * The bridge in use when Townstead is not installed, or is installed but could not be bound at all
 * (Townstead spec §3.4). Every read is empty and every mutation reports
 * {@link TownsteadMutationResult.Reason#MOD_ABSENT}.
 *
 * <p>This is the <em>normal</em> state for most installs, so it is silent: no warning is ever emitted
 * merely because Townstead is absent, and Townstead content simply never becomes eligible.
 */
final class NoopTownsteadBridge implements TownsteadBridge {

    static final NoopTownsteadBridge INSTANCE = new NoopTownsteadBridge();

    private NoopTownsteadBridge() {
    }

    @Override
    public TownsteadStatus status() {
        return TownsteadStatus.ABSENT;
    }

    @Override
    public Set<TownsteadCapability> capabilities() {
        return Set.of();
    }

    @Override
    public String detectedVersion() {
        return "";
    }

    @Override
    public Optional<String> variant() {
        return Optional.empty();
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
    public TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.MOD_ABSENT);
    }

    @Override
    public TownsteadMutationResult awardProfessionXp(Entity villager, String professionId,
                                                     int requestedXp, boolean respectDailyCap) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.MOD_ABSENT);
    }

    @Override
    public TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.MOD_ABSENT);
    }

    @Override
    public TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.MOD_ABSENT);
    }

    @Override
    public TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                      ResourceLocation taskId, String phase) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.MOD_ABSENT);
    }
}
