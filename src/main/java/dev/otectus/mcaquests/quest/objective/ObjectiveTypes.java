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
            register(new ResourceLocation(McaQuests.MOD_ID, "item_delivery"), ItemDeliveryObjective.CODEC);

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
            TYPE_CODEC.dispatch("type", QuestObjective::type, QuestObjectiveType::codec);

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
