package dev.otectus.mcaquests.project.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * A player-targeted phase reward owed to a player who was offline when a phase completed. Delivered on
 * next login. Stores only coordinates into the definition (project + phase + reward index), so no
 * {@code QuestReward} needs serialising.
 */
public record PendingReward(ResourceLocation projectId, int phase, int rewardIndex) {

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("project", projectId.toString());
        tag.putInt("phase", phase);
        tag.putInt("reward", rewardIndex);
        return tag;
    }

    public static PendingReward load(CompoundTag tag) {
        return new PendingReward(new ResourceLocation(tag.getString("project")),
                tag.getInt("phase"), tag.getInt("reward"));
    }
}
