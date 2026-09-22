package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A quest reward that threw while it was being granted, held for an operator (1.7.0).
 *
 * <p>The quest had already completed — its items were consumed and every other reward paid — so the
 * turn-in cannot be undone, and until 1.7.0 the failed reward was simply gone. It is recorded here with
 * enough to pay it again from the current definition ({@code /mcaquests rewards retry}), and a fingerprint
 * of the reward as it stood, so a retry never pays a reward a datapack has since changed. A reward that
 * threw may already have paid part of itself, which is why nothing retries automatically.
 *
 * @param frozen the amount (currency) or entry (item pool) frozen at acceptance, when the reward had one
 */
public record HeldQuestReward(ResourceLocation questId, Optional<UUID> instance, int rewardIndex,
                              String rewardType, String fingerprint, OptionalInt frozen, long gameTime,
                              String error) {

    /** Most held rewards kept per player; the oldest is dropped past this. */
    public static final int MAX_PER_PLAYER = 64;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", questId.toString());
        instance.ifPresent(id -> tag.putUUID("instance", id));
        tag.putInt("index", rewardIndex);
        tag.putString("type", rewardType);
        tag.putString("fingerprint", fingerprint);
        frozen.ifPresent(value -> tag.putInt("frozen", value));
        tag.putLong("time", gameTime);
        tag.putString("error", error);
        return tag;
    }

    public static Optional<HeldQuestReward> load(CompoundTag tag) {
        ResourceLocation quest = ResourceLocation.tryParse(tag.getString("quest"));
        if (quest == null) {
            return Optional.empty();
        }
        return Optional.of(new HeldQuestReward(quest,
                tag.hasUUID("instance") ? Optional.of(tag.getUUID("instance")) : Optional.empty(),
                tag.getInt("index"), tag.getString("type"), tag.getString("fingerprint"),
                tag.contains("frozen") ? OptionalInt.of(tag.getInt("frozen")) : OptionalInt.empty(),
                tag.getLong("time"), tag.getString("error")));
    }
}
