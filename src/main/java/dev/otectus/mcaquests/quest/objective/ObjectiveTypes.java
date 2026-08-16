package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of objective types and the dispatch {@link Codec} that parses a JSON objective by its
 * {@code "type"} field (spec section 14). Built-ins register here; add-ons call
 * {@link #register(ResourceLocation, Codec)} (re-exported through the public API in Phase 6).
 */
public final class ObjectiveTypes {

    private static final Map<ResourceLocation, QuestObjectiveType<?>> BY_ID = new LinkedHashMap<>();

    public static final QuestObjectiveType<ItemDeliveryObjective> ITEM_DELIVERY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "item_delivery"), ItemDeliveryObjective.CODEC);
    public static final QuestObjectiveType<ObtainItemObjective> OBTAIN_ITEM =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "obtain_item"), ObtainItemObjective.CODEC);
    public static final QuestObjectiveType<KillEntityObjective> KILL_ENTITY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "kill_entity"), KillEntityObjective.CODEC);
    public static final QuestObjectiveType<BreakBlockObjective> BREAK_BLOCK =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "break_block"), BreakBlockObjective.CODEC);
    public static final QuestObjectiveType<VisitBiomeObjective> VISIT_BIOME =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "visit_biome"), VisitBiomeObjective.CODEC);
    public static final QuestObjectiveType<VisitDimensionObjective> VISIT_DIMENSION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "visit_dimension"), VisitDimensionObjective.CODEC);
    public static final QuestObjectiveType<CraftItemObjective> CRAFT_ITEM =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "craft_item"), CraftItemObjective.CODEC);
    public static final QuestObjectiveType<PlaceBlockObjective> PLACE_BLOCK =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "place_block"), PlaceBlockObjective.CODEC);
    public static final QuestObjectiveType<FishItemObjective> FISH_ITEM =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "fish_item"), FishItemObjective.CODEC);
    public static final QuestObjectiveType<TalkToProfessionObjective> TALK_TO_PROFESSION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "talk_to_profession"), TalkToProfessionObjective.CODEC);

    // NPC/village-centered objective types (escort, protect, trade, heal, …).
    public static final QuestObjectiveType<EscortEntityObjective> ESCORT_ENTITY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "escort_entity"), EscortEntityObjective.CODEC);
    public static final QuestObjectiveType<ProtectEntityObjective> PROTECT_ENTITY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "protect_entity"), ProtectEntityObjective.CODEC);
    public static final QuestObjectiveType<DefendVillagerObjective> DEFEND_VILLAGER =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "defend_villager"), DefendVillagerObjective.CODEC);
    public static final QuestObjectiveType<TradeWithVillagerObjective> TRADE_WITH_VILLAGER =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "trade_with_villager"), TradeWithVillagerObjective.CODEC);
    public static final QuestObjectiveType<HealEntityObjective> HEAL_ENTITY =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "heal_entity"), HealEntityObjective.CODEC);
    public static final QuestObjectiveType<CureVillagerObjective> CURE_VILLAGER =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "cure_villager"), CureVillagerObjective.CODEC);
    public static final QuestObjectiveType<BreedAnimalsObjective> BREED_ANIMALS =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "breed_animals"), BreedAnimalsObjective.CODEC);
    public static final QuestObjectiveType<TameAnimalObjective> TAME_ANIMAL =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "tame_animal"), TameAnimalObjective.CODEC);
    public static final QuestObjectiveType<SleepOrRestObjective> SLEEP_OR_REST =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "sleep_or_rest"), SleepOrRestObjective.CODEC);
    public static final QuestObjectiveType<BuildNearLocationObjective> BUILD_NEAR_LOCATION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "build_near_location"), BuildNearLocationObjective.CODEC);
    public static final QuestObjectiveType<EnterStructureObjective> ENTER_STRUCTURE =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "enter_structure"), EnterStructureObjective.CODEC);
    public static final QuestObjectiveType<DeliverToVillagerObjective> DELIVER_TO_VILLAGER =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "deliver_to_villager"), DeliverToVillagerObjective.CODEC);

    // Travel + place-anchored combat objectives.
    public static final QuestObjectiveType<ReachLocationObjective> REACH_LOCATION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "reach_location"), ReachLocationObjective.CODEC);
    public static final QuestObjectiveType<DefendLocationObjective> DEFEND_LOCATION =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "defend_location"), DefendLocationObjective.CODEC);

    // FTB Quests bridge objective (spec section 18) — registered unconditionally, zero FTB imports.
    public static final QuestObjectiveType<FtbqCompleteQuestObjective> FTBQ_COMPLETE_QUEST =
            register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "ftbq_complete_quest"), FtbqCompleteQuestObjective.CODEC);

    /** Maps a type id to/from its registry entry, failing parsing on an unknown id. */
    public static final Codec<QuestObjectiveType<?>> TYPE_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                QuestObjectiveType<?> type = BY_ID.get(id);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown objective type: " + id);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<QuestObjective> CODEC =
            TYPE_CODEC.dispatch("type", QuestObjective::type, type -> dev.otectus.mcaquests.data.StrictCodecs.dispatchMap(type.codec()));

    private ObjectiveTypes() {
    }

    public static <T extends QuestObjective> QuestObjectiveType<T> register(ResourceLocation id, Codec<T> codec) {
        QuestObjectiveType<T> type = new QuestObjectiveType<>(id, codec);
        if (BY_ID.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate objective type id: " + id);
        }
        return type;
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    /** Forces class-load so the built-in types register before first use. */
    public static void bootstrap() {
    }
}
