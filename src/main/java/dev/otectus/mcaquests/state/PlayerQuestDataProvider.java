package dev.otectus.mcaquests.state;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Attaches and serialises a {@link PlayerQuestData} on a player entity. */
public final class PlayerQuestDataProvider implements ICapabilitySerializable<CompoundTag> {

    private final PlayerQuestData data = new PlayerQuestData();
    private LazyOptional<PlayerQuestData> holder = LazyOptional.of(() -> data);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return QuestCapabilities.PLAYER_QUESTS.orEmpty(cap, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
    }

    /**
     * Invalidates the optional handed out so far and replaces it with a live one over the same
     * {@link PlayerQuestData}.
     *
     * <p>Forge calls this when the player entity is removed, which on death happens <em>before</em>
     * {@code PlayerEvent.Clone} runs. Anything that cached the old optional must still see the
     * invalidation, so the old holder is invalidated as Forge's contract requires; but
     * {@code reviveCaps()} only flips the provider's valid flag, it does not un-invalidate an optional.
     * Without a fresh holder the clone handler would find nothing to copy and the respawned player
     * would start with empty quest data. The data instance itself is never replaced, so anything loaded
     * by {@link #deserializeNBT} before the invalidation is still there afterwards.
     */
    public void invalidate() {
        holder.invalidate();
        holder = LazyOptional.of(() -> data);
    }

    /** Test-only view of the current holder; production code goes through {@link #getCapability}. */
    LazyOptional<PlayerQuestData> holder() {
        return holder;
    }
}
