package dev.otectus.mcaquests.api;

import com.mojang.serialization.Codec;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjectiveType;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.QuestRewardType;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * Public registration API for MCA: Quests add-ons (spec section 28).
 *
 * <p>Register your own objective / reward / condition types during your mod's setup
 * (e.g. {@code FMLCommonSetupEvent.enqueueWork}) using your own namespace for {@code id}; the JSON
 * {@code "type"} field then dispatches to your {@link Codec}. To react to quest progress, subscribe
 * to the events in {@code dev.otectus.mcaquests.api.event} on the Forge event bus.
 */
public final class McaQuestsApi {

    private McaQuestsApi() {
    }

    public static <T extends QuestObjective> QuestObjectiveType<T> registerObjective(ResourceLocation id, Codec<T> codec) {
        return ObjectiveTypes.register(id, codec);
    }

    public static <T extends QuestReward> QuestRewardType<T> registerReward(ResourceLocation id, Codec<T> codec) {
        return RewardTypes.register(id, codec);
    }

    public static <T extends QuestCondition> QuestConditionType<T> registerCondition(ResourceLocation id, Codec<T> codec) {
        return ConditionTypes.register(id, codec);
    }
}
