package dev.otectus.mcaquests.api;

import com.mojang.serialization.Codec;
import dev.otectus.mcaquests.event.QuestEventHandlers;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Public registration API for MCA: Quests add-ons (spec section 28).
 *
 * <p>Register your own objective / reward / condition types during your mod's setup
 * (e.g. {@code FMLCommonSetupEvent.enqueueWork}) using your own namespace for {@code id}; the JSON
 * {@code "type"} field then dispatches to your {@link Codec}. To react to quest progress, subscribe
 * to the events in {@code dev.otectus.mcaquests.api.event} on the NeoForge game bus.
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

    /**
     * Signals that {@code player} genuinely held a conversation with {@code villager}, advancing every
     * matching {@code talk_to_profession} objective (quest and project) by one.
     *
     * <p>For MCA: Conversations, which knows about real dialogue that MCA: Quests' own empty-hand
     * interaction hook cannot see. Safe to call for a conversation the interaction hook also observed:
     * credit is deduped by villager UUID, so the same villager never counts twice for one objective.
     * Server-side only.
     */
    public static void notifyVillagerConversation(ServerPlayer player, Entity villager) {
        QuestEventHandlers.creditConversation(player, villager);
    }
}
