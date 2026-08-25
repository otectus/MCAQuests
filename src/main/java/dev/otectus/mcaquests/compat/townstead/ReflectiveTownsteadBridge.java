package dev.otectus.mcaquests.compat.townstead;

import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadBuildingView;
import dev.otectus.mcaquests.compat.TownsteadCalendarView;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadGeneView;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadRootView;
import dev.otectus.mcaquests.compat.TownsteadSpiritView;
import dev.otectus.mcaquests.compat.TownsteadStatus;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.Set;

/**
 * The real {@link TownsteadBridge}, backed by {@link TownsteadHandles}.
 *
 * <p>Instantiated by name from {@code TownsteadCompat} only after {@code ModList} has confirmed
 * Townstead is present, which is why this class may reference {@link TownsteadHandles} — and why
 * nothing outside this package may reference <em>it</em>.
 *
 * <p>Constructing it forces the binding, so {@link #status()} is meaningful the moment the object
 * exists and the caller can log one accurate line.
 */
public final class ReflectiveTownsteadBridge implements TownsteadBridge {

    private final TownsteadStatus status;
    private final Set<TownsteadCapability> capabilities;
    private final String version;
    private final Optional<String> variant;

    public ReflectiveTownsteadBridge() {
        TownsteadBinding.Resolution resolution = TownsteadHandles.resolution();
        this.status = resolution.status();
        this.capabilities = resolution.capabilities();
        this.variant = Optional.ofNullable(resolution.variant());
        this.version = ModList.get()
                .getModContainerById("townstead")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    @Override
    public TownsteadStatus status() {
        return status;
    }

    @Override
    public Set<TownsteadCapability> capabilities() {
        return capabilities;
    }

    @Override
    public String detectedVersion() {
        return version;
    }

    @Override
    public Optional<String> variant() {
        return variant;
    }

    @Override
    public java.util.List<String> unresolvedMembers() {
        return TownsteadHandles.resolution().unresolved();
    }

    // --- reads -----------------------------------------------------------------------------------

    @Override
    public Optional<TownsteadVillagerView> villager(Entity entity) {
        return TownsteadHandles.villager(entity);
    }

    @Override
    public Optional<TownsteadCalendarView> calendar(MinecraftServer server) {
        return TownsteadHandles.calendar(server);
    }

    @Override
    public Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos) {
        return TownsteadHandles.buildingAt(level, pos);
    }

    @Override
    public Optional<TownsteadRootView> root(ResourceLocation id) {
        return TownsteadHandles.root(id);
    }

    @Override
    public Optional<TownsteadGeneView> gene(ResourceLocation id) {
        return TownsteadHandles.gene(id);
    }

    @Override
    public Optional<TownsteadSpiritView> spiritForVillage(ServerLevel level, int villageId) {
        return TownsteadHandles.spirit(level, villageId);
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
        return TownsteadHandles.isKnownSpirit(spiritId);
    }

    // --- mutations -------------------------------------------------------------------------------
    //
    // Not yet bound. Reporting CAPABILITY_MISSING rather than throwing means a datapack that reaches
    // for one of these gets the same graceful, explainable refusal it would get from a Townstead
    // point release that had moved the method -- and the same diagnostic path.

    @Override
    public TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation) {
        return TownsteadMutationResult.failed(TownsteadMutationResult.Reason.CAPABILITY_MISSING);
    }

    @Override
    public TownsteadMutationResult awardProfessionXp(Entity villager, String professionId,
                                                     int requestedXp, boolean respectDailyCap) {
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
