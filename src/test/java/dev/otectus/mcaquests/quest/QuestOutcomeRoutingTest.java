package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Where a finished quest's standing goes, and whether a paused quest can finish at all (1.7.1).
 *
 * <p>The first half is the bug a production server reproduced: a giver standing outside any village at
 * turn-in cost the quest its standing, although the village it came from was frozen on the quest when it
 * was accepted. The second half is the drift pause: a copy whose definition was edited under it was
 * reported paused and could still be completed against progress read through the new objectives.
 */
class QuestOutcomeRoutingTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final QuestReputation.Community FROZEN = new QuestReputation.Community(OVERWORLD, 7);
    private static final QuestReputation.Community LIVE = new QuestReputation.Community(OVERWORLD, 3);

    private static ActiveQuest quest(ResourceLocation id, OptionalInt village, int objectives) {
        return ActiveQuest.create(id, UUID.randomUUID(), Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"), OVERWORLD, 0L, OptionalLong.of(0L), village,
                objectives, null, null);
    }

    @Test
    @DisplayName("a giver who resolves to no village does not cost the quest its frozen village")
    void frozenVillageBacksAGiverWithNoVillage() {
        ActiveQuest active = quest(new ResourceLocation("testpack", "errand"), OptionalInt.of(7), 1);
        assertEquals(Optional.of(FROZEN), QuestManager.reputationCommunity(Optional.empty(), active,
                () -> fail("the frozen village answers before any resident scan")));
    }

    @Test
    @DisplayName("the village the giver lives in now wins over the one frozen at accept")
    void liveVillageWins() {
        ActiveQuest active = quest(new ResourceLocation("testpack", "errand"), OptionalInt.of(7), 1);
        assertEquals(Optional.of(LIVE), QuestManager.reputationCommunity(Optional.of(LIVE), active,
                () -> fail("nothing further is asked once the giver answers")));
    }

    @Test
    @DisplayName("a quest from before villages were frozen still falls back to the resident scan")
    void scanIsTheLastResort() {
        ActiveQuest active = quest(new ResourceLocation("testpack", "errand"), OptionalInt.empty(), 1);
        assertEquals(Optional.of(LIVE), QuestManager.reputationCommunity(Optional.empty(), active,
                () -> Optional.of(LIVE)));
        assertEquals(Optional.empty(), QuestManager.reputationCommunity(Optional.empty(), active,
                Optional::empty));
    }

    @Test
    @DisplayName("a copy whose definition drifted is not complete until it is rebased")
    void driftedCopyIsNotComplete() throws Exception {
        ResourceLocation id = new ResourceLocation("testpack", "show_me");
        // Shown, not given: completion is "are you carrying them", so nothing but drift can say no.
        QuestDefinition accepted = definition(id, new ItemDeliveryObjective(Items.BREAD, 2, false));
        QuestDefinition edited = definition(id, new ItemDeliveryObjective(Items.APPLE, 2, false));
        ActiveQuest active = quest(id, OptionalInt.empty(), 1);
        QuestDrift.capture(active, accepted);
        ServerPlayer player = inventoryOnlyPlayer();
        player.getInventory().setItem(0, new ItemStack(Items.APPLE, 2));

        assertTrue(QuestDrift.drifted(active, edited));
        assertFalse(QuestManager.isComplete(player, edited, active),
                "the log shows this copy paused; it must not also read as ready to hand in");

        QuestDrift.capture(active, edited); // what /mcaquests quest rebase ... confirm does
        assertTrue(QuestManager.isComplete(player, edited, active));
    }

    private static QuestDefinition definition(ResourceLocation id, ItemDeliveryObjective objective) {
        return new QuestDefinition(id, true, 1, Optional.empty(), Optional.empty(),
                RepeatRule.DEFAULT, new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Map.of(), List.of(objective), List.of(), TurnInSpec.DEFAULT, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE, QuestReputationBlock.NONE);
    }

    /** No world is used by these inventory-only objectives; initialize just their real inventory. */
    private static ServerPlayer inventoryOnlyPlayer() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field access = unsafeType.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        ServerPlayer player = (ServerPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(access.get(null), ServerPlayer.class);
        Field inventory = Player.class.getDeclaredField("inventory");
        inventory.setAccessible(true);
        inventory.set(player, new Inventory(player));
        return player;
    }
}
