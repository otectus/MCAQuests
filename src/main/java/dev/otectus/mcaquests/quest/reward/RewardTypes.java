package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of reward types and the dispatch {@link Codec} keyed on {@code "type"} (spec section 15).
 */
public final class RewardTypes {

    private static final Map<ResourceLocation, QuestRewardType<?>> BY_ID = new LinkedHashMap<>();

    public static final QuestRewardType<ItemReward> ITEM =
            register(new ResourceLocation(McaQuests.MOD_ID, "item"), ItemReward.CODEC);
    public static final QuestRewardType<XpReward> XP =
            register(new ResourceLocation(McaQuests.MOD_ID, "xp"), XpReward.CODEC);
    public static final QuestRewardType<FavorReward> FAVOR =
            register(new ResourceLocation(McaQuests.MOD_ID, "favor"), FavorReward.CODEC);

    public static final Codec<QuestRewardType<?>> TYPE_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                QuestRewardType<?> type = BY_ID.get(id);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown reward type: " + id);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<QuestReward> CODEC =
            TYPE_CODEC.dispatch("type", QuestReward::type, QuestRewardType::codec);

    private RewardTypes() {
    }

    public static <T extends QuestReward> QuestRewardType<T> register(ResourceLocation id, Codec<T> codec) {
        QuestRewardType<T> type = new QuestRewardType<>(id, codec);
        if (BY_ID.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate reward type id: " + id);
        }
        return type;
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    public static void bootstrap() {
    }
}
