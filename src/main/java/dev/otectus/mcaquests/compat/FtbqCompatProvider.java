package dev.otectus.mcaquests.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * FTB Quests, expressed as a {@link CompatProvider}.
 *
 * <p>Read-only over {@link FtbqBridge}, which {@code FtbqBootstrap} selects once during mod
 * construction. Two capabilities, because the bridge distinguishes two failures that look identical
 * from outside: {@code book} is "the real FTB-backed implementation is in place", and
 * {@code progress} is "and the master config switch lets it do anything".
 */
public final class FtbqCompatProvider implements CompatProvider {

    private static final String MOD_ID = "ftbquests";

    /** The FTB book is reachable — an {@code ftbq_*} condition can be answered at all. */
    private static final String BOOK = "book";
    /** Reads and writes against that book are allowed by config. */
    private static final String PROGRESS = "progress";

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.ftbquests.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(MOD_ID);
    }

    @Override
    public CompatStatus status() {
        FtbqBridge bridge = FtbqBridge.Holder.get();
        if (!ModList.get().isLoaded(MOD_ID)) {
            return CompatStatus.ABSENT;
        }
        if (!bridge.isReal()) {
            return CompatStatus.DISABLED;
        }
        return bridge.isAvailable() ? CompatStatus.FULL : CompatStatus.DISABLED;
    }

    @Override
    public List<CompatCapability> capabilities() {
        FtbqBridge bridge = FtbqBridge.Holder.get();
        return List.of(
                new CompatCapability(BOOK, bridge.isReal(), CapabilityEvidence.ADAPTER_CONFIRMED),
                new CompatCapability(PROGRESS, bridge.isAvailable(), CapabilityEvidence.ADAPTER_CONFIRMED));
    }

    /**
     * Deliberately a no-op. The bridge is chosen once from {@code FtbqBootstrap} during mod
     * construction and the mod list cannot change afterwards; the config switch behind
     * {@link FtbqBridge#isAvailable()} is read live on every call, so it needs no probe either.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
    }
}
