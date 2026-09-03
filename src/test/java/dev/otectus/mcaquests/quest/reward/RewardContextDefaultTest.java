package dev.otectus.mcaquests.quest.reward;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code QuestReward} is public add-on API, so the giver-less reward path had to be added without
 * breaking anything already written against it: a reward that implements only the two-argument
 * {@code grant} must still be granted when the three-argument form is the one called.
 */
class RewardContextDefaultTest {

    /** A reward of the shape an add-on written before 1.5.1 has: two-argument {@code grant} only. */
    private static final class TwoArgOnlyReward implements QuestReward {

        private static final QuestRewardType<TwoArgOnlyReward> TYPE = new QuestRewardType<>(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "test_two_arg_only"), MapCodec.unit(TwoArgOnlyReward::new));

        private int grants;

        @Override
        public QuestRewardType<?> type() {
            return TYPE;
        }

        @Override
        public Component describe() {
            return Component.literal("test");
        }

        @Override
        public void grant(ServerPlayer player, @Nullable Entity villager) {
            grants++;
        }
    }

    @Test
    @DisplayName("the three-argument grant delegates to a reward that only implements the two-argument one")
    void defaultDelegatesToTheTwoArgumentForm() {
        TwoArgOnlyReward reward = new TwoArgOnlyReward();
        QuestReward.RewardContext context = new QuestReward.RewardContext(UUID.randomUUID(),
                Component.literal("Anna"), ResourceLocation.withDefaultNamespace("overworld"),
                OptionalInt.of(3), ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest"));

        reward.grant(null, null, context);

        assertEquals(1, reward.grants, "an add-on reward must still be granted through the new overload");
    }

    @Test
    @DisplayName("a context carrying a village resolves to that community")
    void contextResolvesItsCommunity() {
        QuestReward.RewardContext withVillage = new QuestReward.RewardContext(UUID.randomUUID(),
                Component.literal("Anna"), ResourceLocation.withDefaultNamespace("overworld"),
                OptionalInt.of(3), ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest"));
        QuestReward.RewardContext withoutVillage = new QuestReward.RewardContext(UUID.randomUUID(),
                Component.literal("Anna"), ResourceLocation.withDefaultNamespace("overworld"),
                OptionalInt.empty(), ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest"));

        assertTrue(withVillage.community().isPresent(), "a frozen village names a community");
        assertEquals(3, withVillage.community().orElseThrow().villageId());
        assertTrue(withoutVillage.community().isEmpty(),
                "a quest accepted before 1.5.1 froze no village and must not invent one");
    }
}
