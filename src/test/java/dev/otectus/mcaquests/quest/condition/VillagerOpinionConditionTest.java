package dev.otectus.mcaquests.quest.condition;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.compat.VillagerOpinionView;
import dev.otectus.mcaquests.quest.condition.leaf.VillagerOpinionCondition;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code mcareputation:villager_opinion} — the parse surface a datapack author sees, and the answer
 * the condition gets when MCA: Reputation is not there to ask.
 *
 * <p>Evaluation itself needs a live giver and a village, so it belongs to the production matrix; what
 * is checkable here is that every documented spelling of the JSON parses, and that the backend a
 * Quests-only install runs on reports no opinion at all rather than a made-up one.
 */
class VillagerOpinionConditionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @AfterEach
    void reset() {
        ReputationBridge.resetForTest();
    }

    private static VillagerOpinionCondition parse(String json) {
        return VillagerOpinionCondition.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(AssertionError::new);
    }

    @Test
    @DisplayName("a tier band parses both ends")
    void tierBand() {
        VillagerOpinionCondition condition =
                parse("{\"min_tier\":\"friend\",\"max_tier\":\"revered\"}");
        assertEquals(Optional.of("friend"), condition.minTier());
        assertEquals(Optional.of("revered"), condition.maxTier());
        assertEquals(List.of(), condition.basis());
    }

    @Test
    @DisplayName("basis takes a list, lower-cased")
    void basisList() {
        assertEquals(List.of("involved", "witnessed"),
                parse("{\"basis\":[\"involved\",\"WITNESSED\"]}").basis());
    }

    @Test
    @DisplayName("basis also takes a bare string, which is the common case")
    void basisSingleString() {
        assertEquals(List.of("hearsay"), parse("{\"basis\":\"hearsay\"}").basis());
    }

    @Test
    @DisplayName("every field is optional; an empty block parses")
    void emptyBlock() {
        VillagerOpinionCondition condition = parse("{}");
        assertTrue(condition.minTier().isEmpty());
        assertTrue(condition.maxTier().isEmpty());
        assertTrue(condition.basis().isEmpty());
    }

    /**
     * The fail-safe half. Without MCA: Reputation nothing knows what one villager saw, so the backend
     * answers empty and the condition is unmet — never met-by-default, which would have villagers
     * reacting to deeds nobody witnessed.
     */
    @Test
    @DisplayName("the fallback backend reports no opinion at all")
    void fallbackBackendHasNoOpinion() {
        ReputationBridge.resetForTest();
        Optional<VillagerOpinionView> opinion = ReputationBridge.backend().villagerOpinion(
                null, UUID.randomUUID(), UUID.randomUUID(),
                ResourceLocation.withDefaultNamespace("overworld"), 3);
        assertTrue(opinion.isEmpty());
    }

    @Test
    @DisplayName("the type is registered under mcareputation:villager_opinion, mod present or not")
    void registered() {
        assertEquals("mcareputation:villager_opinion", ConditionTypes.VILLAGER_OPINION.id().toString());
    }
}
