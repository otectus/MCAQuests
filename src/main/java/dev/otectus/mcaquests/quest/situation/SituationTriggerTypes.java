package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.situation.trigger.HostilesNearHomeTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.InfectionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.LowFoodTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.MissingKinTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.NightTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadBuildingTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadCalendarTransitionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadCollapseTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadLifeTransitionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadNeedTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadProfessionTierTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadScheduleDisruptionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.TownsteadSpiritTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.RaidTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerStrandedTrigger;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of {@link SituationTrigger} leaf types plus the dispatching {@link Codec}, mirroring the
 * objective/condition/reward registries. The {@code "type"} field on a trigger object selects the leaf
 * codec (0.8.0).
 */
public final class SituationTriggerTypes {

    private static final Map<ResourceLocation, SituationTriggerType<?>> BY_ID = new LinkedHashMap<>();

    public static final SituationTriggerType<RaidTrigger> RAID = register("raid", RaidTrigger.CODEC);
    public static final SituationTriggerType<VillagerDeathTrigger> VILLAGER_DEATH =
            register("villager_death", VillagerDeathTrigger.CODEC);
    public static final SituationTriggerType<InfectionTrigger> INFECTION = register("infection", InfectionTrigger.CODEC);
    public static final SituationTriggerType<MissingKinTrigger> MISSING_KIN = register("missing_kin", MissingKinTrigger.CODEC);
    public static final SituationTriggerType<LowFoodTrigger> LOW_FOOD = register("low_food", LowFoodTrigger.CODEC);
    public static final SituationTriggerType<NightTrigger> NIGHT = register("night", NightTrigger.CODEC);

    // Townstead (Townstead spec 7.3). Registered unconditionally so a datapack parses either way; the
    // detector that produces these signals simply never runs when Townstead is absent.
    public static final SituationTriggerType<TownsteadNeedTrigger> TOWNSTEAD_NEED =
            register("townstead_need", TownsteadNeedTrigger.CODEC);
    public static final SituationTriggerType<TownsteadCollapseTrigger> TOWNSTEAD_COLLAPSE =
            register("townstead_collapse", TownsteadCollapseTrigger.CODEC);
    public static final SituationTriggerType<TownsteadProfessionTierTrigger> TOWNSTEAD_PROFESSION_TIER =
            register("townstead_profession_tier", TownsteadProfessionTierTrigger.CODEC);
    public static final SituationTriggerType<TownsteadSpiritTrigger> TOWNSTEAD_SPIRIT =
            register("townstead_spirit", TownsteadSpiritTrigger.CODEC);
    public static final SituationTriggerType<TownsteadBuildingTrigger> TOWNSTEAD_BUILDING =
            register("townstead_building", TownsteadBuildingTrigger.CODEC);

    // 1.4.1 transition signals (spec 5.8). The first three read Townstead; the last two are MCA-only
    // and work on an install that has never had Townstead.
    public static final SituationTriggerType<TownsteadCalendarTransitionTrigger> TOWNSTEAD_CALENDAR_TRANSITION =
            register("townstead_calendar_transition", TownsteadCalendarTransitionTrigger.CODEC);
    public static final SituationTriggerType<TownsteadLifeTransitionTrigger> TOWNSTEAD_LIFE_TRANSITION =
            register("townstead_life_transition", TownsteadLifeTransitionTrigger.CODEC);
    public static final SituationTriggerType<TownsteadScheduleDisruptionTrigger> TOWNSTEAD_SCHEDULE_DISRUPTION =
            register("townstead_schedule_disruption", TownsteadScheduleDisruptionTrigger.CODEC);
    public static final SituationTriggerType<VillagerStrandedTrigger> VILLAGER_STRANDED =
            register("villager_stranded", VillagerStrandedTrigger.CODEC);
    public static final SituationTriggerType<HostilesNearHomeTrigger> HOSTILES_NEAR_HOME =
            register("hostiles_near_home", HostilesNearHomeTrigger.CODEC);

    public static final Codec<SituationTriggerType<?>> TYPE_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                SituationTriggerType<?> type = BY_ID.get(id);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown situation trigger type: " + id);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<SituationTrigger> CODEC =
            TYPE_CODEC.dispatch("type", SituationTrigger::type, SituationTriggerType::codec);

    private SituationTriggerTypes() {
    }

    public static <T extends SituationTrigger> SituationTriggerType<T> register(ResourceLocation id, MapCodec<T> codec) {
        SituationTriggerType<T> type = new SituationTriggerType<>(id, codec);
        if (BY_ID.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate situation trigger type id: " + id);
        }
        return type;
    }

    public static <T extends SituationTrigger> SituationTriggerType<T> register(String path, MapCodec<T> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, path), codec);
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    /** Forces static initialization so the built-in trigger types are registered (called at mod setup). */
    public static void bootstrap() {
    }
}
