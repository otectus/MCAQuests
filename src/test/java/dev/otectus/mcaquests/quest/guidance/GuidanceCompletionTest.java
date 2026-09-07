package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuidanceCompletionTest {
    static { TestBootstrap.ensureBootstrapped(); }

    @Test
    void handInGuidanceCannotSpendOneStackForTwoDeliveryRows() throws Exception {
        ServerPlayer player = inventoryOnlyPlayer();
        player.getInventory().setItem(0, new ItemStack(Items.BREAD, 8));
        ItemDeliveryObjective delivery = new ItemDeliveryObjective(Items.BREAD, 6, true);
        ResourceLocation id = new ResourceLocation("testpack", "two_deliveries");
        QuestDefinition definition = new QuestDefinition(id, true, 1, Optional.empty(), Optional.empty(),
                RepeatRule.DEFAULT, new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Map.of(), List.of(delivery, delivery), List.of(), TurnInSpec.DEFAULT, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), OfferShaping.NONE, QuestReputationBlock.NONE);
        ActiveQuest active = ActiveQuest.create(id, UUID.randomUUID(), Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"), new ResourceLocation("minecraft", "overworld"),
                0, 2, null);

        assertTrue(delivery.isSatisfied(player, active.progress(0)));
        assertTrue(delivery.isSatisfied(player, active.progress(1)));
        assertFalse(GuidanceService.isComplete(player, active, definition),
                "individually satisfied rows must not produce a ready giver highlight without their combined payment");
        assertEquals(8, player.getInventory().getItem(0).getCount(), "guidance never consumes goods");
        player.getInventory().setItem(0, new ItemStack(Items.BREAD, 12));
        assertTrue(GuidanceService.isComplete(player, active, definition));
        assertEquals(12, player.getInventory().getItem(0).getCount());
    }

    /** No world is used by these consumption-only objectives; initialize just their real inventory. */
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
