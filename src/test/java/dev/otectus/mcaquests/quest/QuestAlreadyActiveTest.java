package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.quest.objective.ObtainItemObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reputation.QuestReputationBlock;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Already active" is a question about the player for a plain quest and about the villager for a chain.
 *
 * <p>It used to be about the villager for both, which is a double-reward exploit on every quest offered
 * by more than one profession: accept "bring me ten wheat" from two farmers and one harvest credits both
 * copies, because the progress events walk every active quest of the matching type. Arcs genuinely are
 * per-villager — that is what makes a relationship story a relationship story — so the two answers have
 * to stay different, which is the pair of cases below.
 */
class QuestAlreadyActiveTest {

    private static final ResourceLocation PLAIN = new ResourceLocation("testpack", "gather_wheat");
    private static final ResourceLocation ARC = new ResourceLocation("testpack", "arc_stage_1");
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");

    private static final UUID VILLAGER_A = UUID.randomUUID();
    private static final UUID VILLAGER_B = UUID.randomUUID();

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a quest with no chain is active for the player, not for one villager")
    void plainQuestIsGlobalOncePlayerHoldsIt() {
        PlayerQuestData data = new PlayerQuestData();
        QuestDefinition def = quest(PLAIN, Optional.empty());

        assertFalse(OfferFilters.alreadyActive(data, def, VILLAGER_A), "nothing accepted yet");

        data.add(accept(PLAIN, VILLAGER_A));

        assertTrue(OfferFilters.alreadyActive(data, def, VILLAGER_A), "held with the giver it came from");
        assertTrue(OfferFilters.alreadyActive(data, def, VILLAGER_B),
                "a second villager must not offer the same plain quest: one set of kills would complete "
                        + "both copies and pay twice");
    }

    @Test
    @DisplayName("a chain stage stays per-villager, so a second arc can start elsewhere")
    void chainQuestStaysPerVillager() {
        PlayerQuestData data = new PlayerQuestData();
        QuestDefinition def = quest(ARC, Optional.of(new ChainSpec("testpack_arc", 1, Optional.of(3),
                Optional.empty(), Optional.empty(), List.of(), List.of())));

        data.add(accept(ARC, VILLAGER_A));

        assertTrue(OfferFilters.alreadyActive(data, def, VILLAGER_A), "this villager's arc is underway");
        assertFalse(OfferFilters.alreadyActive(data, def, VILLAGER_B),
                "arcs are per-villager by design; another villager may start their own");
    }

    private static QuestDefinition quest(ResourceLocation id, Optional<ChainSpec> chain) {
        GiverSpec giver = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);
        List<QuestObjective> objectives =
                List.of(new ObtainItemObjective(new ItemTarget(Optional.of(Items.WHEAT), Optional.empty()), 10));
        return new QuestDefinition(id, true, 1, Optional.empty(), Optional.empty(), RepeatRule.DEFAULT,
                giver, Map.of(), objectives, List.of(), TurnInSpec.DEFAULT, Optional.empty(),
                chain, Optional.empty(), Optional.empty(), OfferShaping.NONE, QuestReputationBlock.NONE);
    }

    private static ActiveQuest accept(ResourceLocation id, UUID villager) {
        return ActiveQuest.create(id, villager, Component.literal("Anna"),
                new ResourceLocation("minecraft", "farmer"), DIMENSION, 0L, 1, null);
    }
}
