package dev.otectus.mcaquests.compat.capitals;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The bridge used when MCA Capitals is not installed, or when {@code compat.capitals.enabled} is
 * false.
 *
 * <p>A whole class rather than a flag on the reflective one, because the most common installation by
 * far is the one without Capitals and that path should hold nothing at all: no handles, no cached
 * resolution, nothing that could throw. Every capability answers "no", which is what makes a gated
 * quest simply never offered and a quest already accepted pause instead of break.
 *
 * <p>{@link #status()} distinguishes the two reasons, because an owner who switched the integration
 * off and an owner who forgot to install the mod need different answers from
 * {@code /mcaquests compat capitals status}.
 */
public final class NoopCapitalsBridge implements CapitalsBridge {

    private final CompatStatus status;

    /**
     * @param disabled true when Capitals is installed but the integration is switched off, which
     *                 reports {@link CompatStatus#DISABLED} rather than {@link CompatStatus#ABSENT}
     */
    public NoopCapitalsBridge(boolean disabled) {
        this.status = disabled ? CompatStatus.DISABLED : CompatStatus.ABSENT;
    }

    @Override
    public CompatStatus status() {
        return status;
    }

    /**
     * Every capability, declared and absent.
     *
     * <p>Declaring them rather than returning nothing is deliberate: {@code compat status} then lists
     * the same rows on every installation, so "the chronicle capability is missing" and "this build
     * has no chronicle capability at all" cannot be confused for one another.
     */
    @Override
    public List<CompatCapability> capabilities() {
        List<CompatCapability> capabilities = new ArrayList<>();
        for (CapitalsCapability capability : CapitalsCapability.values()) {
            capabilities.add(new CompatCapability(capability.id(), false,
                    CapabilityEvidence.ADAPTER_CONFIRMED));
        }
        return List.copyOf(capabilities);
    }

    @Override
    public boolean has(CapitalsCapability capability) {
        return false;
    }

    @Override
    public Optional<CapitalRef> capitalForVillage(ServerLevel level, int villageId) {
        return Optional.empty();
    }

    @Override
    public List<CapitalRef> allCapitals() {
        return List.of();
    }

    @Override
    public Optional<ServerLevel> capitalLevel(MinecraftServer server, CapitalRef capital) {
        return Optional.empty();
    }

    @Override
    public Optional<CapitalRef> capitalOfResident(UUID residentId) {
        return Optional.empty();
    }

    @Override
    public boolean isActive(CapitalRef capital) {
        return false;
    }

    @Override
    public Optional<String> displayName(ServerLevel level, CapitalRef capital, UUID entityId) {
        return Optional.empty();
    }

    @Override
    public List<UUID> villagerRoleHolders(ServerLevel level, CapitalRef capital, CapitalRole role) {
        return List.of();
    }

    @Override
    public boolean villagerHasRole(ServerLevel level, CapitalRef capital, UUID villager, CapitalRole role) {
        return false;
    }

    @Override
    public boolean playerHasRole(ServerLevel level, CapitalRef capital, UUID player, CapitalRole role) {
        return false;
    }

    @Override
    public Optional<UUID> declaredAllegiance(ServerLevel level, UUID player) {
        return Optional.empty();
    }

    @Override
    public Optional<String> diplomaticState(ServerLevel level, UUID first, UUID second) {
        return Optional.empty();
    }

    @Override
    public Map<UUID, InterregnumView> interregnums(ServerLevel level) {
        return Map.of();
    }

    @Override
    public Optional<InterregnumView> interregnum(ServerLevel level, CapitalRef capital) {
        return Optional.empty();
    }

    @Override
    public boolean grantPlayerTitle(ServerLevel level, CapitalRef capital, UUID player,
                                    String nobleTitleName) {
        return false;
    }

    @Override
    public boolean setVillagerTitle(ServerLevel level, CapitalRef capital, UUID villager,
                                    String nobleTitleName) {
        return false;
    }

    @Override
    public boolean addChronicleEntry(ServerLevel level, CapitalRef capital, String entry, boolean herald) {
        return false;
    }

    @Override
    public Optional<Boolean> isPlayerFemale(ServerLevel level, ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> isVillagerFemale(ServerLevel level, UUID villager) {
        return Optional.empty();
    }
}
