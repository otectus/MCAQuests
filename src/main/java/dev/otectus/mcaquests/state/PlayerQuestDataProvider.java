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
    private final LazyOptional<PlayerQuestData> holder = LazyOptional.of(() -> data);

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

    public void invalidate() {
        holder.invalidate();
    }
}
