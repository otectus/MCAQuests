package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
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
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "item"), ItemReward.CODEC);
    public static final QuestRewardType<XpReward> XP =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "xp"), XpReward.CODEC);
    public static final QuestRewardType<HeartsReward> HEARTS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "hearts"), HeartsReward.CODEC);
    public static final QuestRewardType<XpLevelsReward> XP_LEVELS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "xp_levels"), XpLevelsReward.CODEC);
    public static final QuestRewardType<EffectReward> EFFECT =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "effect"), EffectReward.CODEC);
    public static final QuestRewardType<LootTableReward> LOOT_TABLE =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "loot_table"), LootTableReward.CODEC);
    public static final QuestRewardType<CommandReward> COMMAND =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "command"), CommandReward.CODEC);

    // v0.4.0 — community project rewards. The distributor supplies the project/scope context the
    // last three need; hearts_with_sponsor also works through the normal grant path.
    public static final QuestRewardType<HeartsWithSponsorReward> HEARTS_WITH_SPONSOR =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "hearts_with_sponsor"), HeartsWithSponsorReward.CODEC);
    public static final QuestRewardType<HeartsWithParticipantsReward> HEARTS_WITH_PARTICIPANTS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "hearts_with_participants"), HeartsWithParticipantsReward.CODEC);
    /**
     * {@code mcareputation:resolve_incident} and {@code mcareputation:record_incident}.
     *
     * <p>Registered under the <b>mcareputation</b> namespace so their JSON reads naturally, and
     * registered unconditionally so a pack that uses them loads on a Quests-only install. Both are
     * silent no-ops without that mod (spec §29.6).
     */
    public static final QuestRewardType<ResolveIncidentReward> RESOLVE_INCIDENT =
            register(ResourceLocation.fromNamespaceAndPath("mcareputation", "resolve_incident"),
                    ResolveIncidentReward.CODEC);
    public static final QuestRewardType<RecordIncidentReward> RECORD_INCIDENT =
            register(ResourceLocation.fromNamespaceAndPath("mcareputation", "record_incident"),
                    RecordIncidentReward.CODEC);

    public static final QuestRewardType<VillageReputationReward> VILLAGE_REPUTATION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "village_reputation"), VillageReputationReward.CODEC);
    public static final QuestRewardType<UnlockReward> UNLOCK =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "unlock"), UnlockReward.CODEC);

    // Townstead (Townstead spec 5.5). Registered unconditionally so a datapack parses identically with
    // or without Townstead; each reward gates itself on its capability and its own config switch.
    public static final QuestRewardType<TownsteadNeedsReward> TOWNSTEAD_NEEDS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "townstead_needs"), TownsteadNeedsReward.CODEC);
    public static final QuestRewardType<TownsteadProfessionXpReward> TOWNSTEAD_PROFESSION_XP =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "townstead_profession_xp"),
                    TownsteadProfessionXpReward.CODEC);
    public static final QuestRewardType<TownsteadSkillReward> TOWNSTEAD_SKILL =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "townstead_skill"), TownsteadSkillReward.CODEC);
    public static final QuestRewardType<TownsteadReactionReward> TOWNSTEAD_REACTION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "townstead_reaction"),
                    TownsteadReactionReward.CODEC);

    // v0.7.0 — grants a player title (village-scoped or global).
    public static final QuestRewardType<GrantTitleReward> GRANT_TITLE =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "grant_title"), GrantTitleReward.CODEC);

    // v1.1.0 — semantic money. The item paid out is a server config choice (emeralds, Numismatics, custom),
    // so a datapack asks for "some currency" and never hard-codes another mod's item.
    public static final QuestRewardType<CurrencyReward> CURRENCY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "currency"), CurrencyReward.CODEC);

    // M4 — grants FTB Quests progress for the player's team (complete task, complete quest, or reset task).
    public static final QuestRewardType<FtbqProgressReward> FTBQ_PROGRESS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "ftbq_progress"), FtbqProgressReward.CODEC);

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

    public static <T extends QuestReward> QuestRewardType<T> register(ResourceLocation id, MapCodec<T> codec) {
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
