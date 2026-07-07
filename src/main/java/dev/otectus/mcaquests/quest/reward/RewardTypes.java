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
    public static final QuestRewardType<HeartsReward> HEARTS =
            register(new ResourceLocation(McaQuests.MOD_ID, "hearts"), HeartsReward.CODEC);
    public static final QuestRewardType<XpLevelsReward> XP_LEVELS =
            register(new ResourceLocation(McaQuests.MOD_ID, "xp_levels"), XpLevelsReward.CODEC);
    public static final QuestRewardType<EffectReward> EFFECT =
            register(new ResourceLocation(McaQuests.MOD_ID, "effect"), EffectReward.CODEC);
    public static final QuestRewardType<LootTableReward> LOOT_TABLE =
            register(new ResourceLocation(McaQuests.MOD_ID, "loot_table"), LootTableReward.CODEC);
    public static final QuestRewardType<CommandReward> COMMAND =
            register(new ResourceLocation(McaQuests.MOD_ID, "command"), CommandReward.CODEC);

    // v0.4.0 — community project rewards. The distributor supplies the project/scope context the
    // last three need; hearts_with_sponsor also works through the normal grant path.
    public static final QuestRewardType<HeartsWithSponsorReward> HEARTS_WITH_SPONSOR =
            register(new ResourceLocation(McaQuests.MOD_ID, "hearts_with_sponsor"), HeartsWithSponsorReward.CODEC);
    public static final QuestRewardType<HeartsWithParticipantsReward> HEARTS_WITH_PARTICIPANTS =
            register(new ResourceLocation(McaQuests.MOD_ID, "hearts_with_participants"), HeartsWithParticipantsReward.CODEC);
    public static final QuestRewardType<VillageReputationReward> VILLAGE_REPUTATION =
            register(new ResourceLocation(McaQuests.MOD_ID, "village_reputation"), VillageReputationReward.CODEC);
    public static final QuestRewardType<UnlockReward> UNLOCK =
            register(new ResourceLocation(McaQuests.MOD_ID, "unlock"), UnlockReward.CODEC);

    // v0.7.0 — grants a player title (village-scoped or global).
    public static final QuestRewardType<GrantTitleReward> GRANT_TITLE =
            register(new ResourceLocation(McaQuests.MOD_ID, "grant_title"), GrantTitleReward.CODEC);

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
