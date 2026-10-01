package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.reward.QuestReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * @param frozen  the amount (currency) or entry (item pool) frozen at acceptance, when the reward had one
 * @param context what the quest knew about its giver when the reward failed (1.7.1): the giver's UUID,
 *                name, dimension and frozen village. A retry needs it for every reward that pays through
 *                the giver — hearts, a village title, a Capitals reward — because the operator's retry
 *                happens long after the turn-in, usually with the giver nowhere near. Empty on a reward
 *                held before 1.7.1, which retries exactly as it did then.
 */
public record HeldQuestReward(ResourceLocation questId, Optional<UUID> instance, int rewardIndex,
                              String rewardType, String fingerprint, OptionalInt frozen, long gameTime,
                              String error, Optional<QuestReward.RewardContext> context) {

    /** Most held rewards kept per player; the oldest is dropped past this. */
    public static final int MAX_PER_PLAYER = 64;

    public HeldQuestReward {
        context = context == null ? Optional.empty() : context;
    }

    /** The 1.7.0 shape, with no giver context. */
    public HeldQuestReward(ResourceLocation questId, Optional<UUID> instance, int rewardIndex,
                           String rewardType, String fingerprint, OptionalInt frozen, long gameTime,
                           String error) {
        this(questId, instance, rewardIndex, rewardType, fingerprint, frozen, gameTime, error, Optional.empty());
    }

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
        context.ifPresent(giver -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID("giver", giver.giverUuid());
            saved.putString("giver_name", NbtComponents.toJsonString(giver.giverName()));
            saved.putString("dimension", giver.dimension().toString());
            giver.villageId().ifPresent(village -> saved.putInt("village", village));
            tag.put("context", saved);
        });
        return tag;
    }

    public static Optional<HeldQuestReward> load(CompoundTag tag) {
        ResourceLocation quest = ResourceLocation.tryParse(tag.getString("quest"));
        if (quest == null) {
            return Optional.empty();
        }
        Optional<UUID> instance = tag.hasUUID("instance") ? Optional.of(tag.getUUID("instance")) : Optional.empty();
        return Optional.of(new HeldQuestReward(quest, instance,
                tag.getInt("index"), tag.getString("type"), tag.getString("fingerprint"),
                tag.contains("frozen") ? OptionalInt.of(tag.getInt("frozen")) : OptionalInt.empty(),
                tag.getLong("time"), tag.getString("error"), loadContext(tag.getCompound("context"), quest, instance)));
    }

    /** A saved context, or empty when there is none or it cannot be read; the reward stays retryable either way. */
    private static Optional<QuestReward.RewardContext> loadContext(CompoundTag saved, ResourceLocation quest,
                                                                   Optional<UUID> instance) {
        ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("dimension"));
        if (!saved.hasUUID("giver") || dimension == null) {
            return Optional.empty();
        }
        Component name;
        try {
            name = NbtComponents.fromJsonString(saved.getString("giver_name"));
        } catch (RuntimeException malformedName) {
            name = null;
        }
        return Optional.of(new QuestReward.RewardContext(saved.getUUID("giver"),
                name == null ? Component.literal(saved.getString("giver_name")) : name, dimension,
                saved.contains("village") ? OptionalInt.of(saved.getInt("village")) : OptionalInt.empty(),
                quest, instance));
    }
}
