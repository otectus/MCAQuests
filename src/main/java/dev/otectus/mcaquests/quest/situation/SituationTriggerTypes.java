package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.situation.trigger.InfectionTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.LowFoodTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.MissingKinTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.NightTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.RaidTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
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

    public static final Codec<SituationTriggerType<?>> TYPE_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                SituationTriggerType<?> type = BY_ID.get(id);
                return type != null
                        ? DataResult.success(type)
                        : DataResult.error(() -> "Unknown situation trigger type: " + id);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<SituationTrigger> CODEC =
            TYPE_CODEC.dispatch("type", SituationTrigger::type, type -> dev.otectus.mcaquests.data.StrictCodecs.dispatchMap(type.codec()));

    private SituationTriggerTypes() {
    }

    public static <T extends SituationTrigger> SituationTriggerType<T> register(ResourceLocation id, Codec<T> codec) {
        SituationTriggerType<T> type = new SituationTriggerType<>(id, codec);
        if (BY_ID.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("Duplicate situation trigger type id: " + id);
        }
        return type;
    }

    public static <T extends SituationTrigger> SituationTriggerType<T> register(String path, Codec<T> codec) {
        return register(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, path), codec);
    }

    public static boolean exists(ResourceLocation id) {
        return BY_ID.containsKey(id);
    }

    /** Forces static initialization so the built-in trigger types are registered (called at mod setup). */
    public static void bootstrap() {
    }
}
