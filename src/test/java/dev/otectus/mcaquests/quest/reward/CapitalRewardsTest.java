package dev.otectus.mcaquests.quest.reward;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.capitals.CapitalRole;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three MCA Capitals rewards: what a datapack may write, and which of Capitals' two spellings of a
 * rank each one grants.
 *
 * <p>The gendered mapping is the half worth pinning down. Capitals stores {@code DAME} and
 * {@code KNIGHT} as separate constants and accepts either without complaint, so getting it wrong is
 * invisible in the log and visible only as a woman being called a knight for the rest of the save.
 */
class CapitalRewardsTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static QuestReward parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<QuestReward> result = RewardTypes.CODEC.parse(JsonOps.INSTANCE, element);
        return result.result().orElseThrow(() -> new AssertionError(
                "expected " + json + " to parse, but got: " + result.error().orElseThrow().message()));
    }

    private static String error(String json) {
        return RewardTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .error()
                .orElseThrow(() -> new AssertionError("expected " + json + " to be rejected"))
                .message();
    }

    @Test
    @DisplayName("all three types are registered under the mcaquests namespace")
    void registered() {
        assertEquals("mcaquests:capital_title", RewardTypes.CAPITAL_TITLE.id().toString());
        assertEquals("mcaquests:capital_chronicle", RewardTypes.CAPITAL_CHRONICLE.id().toString());
        assertEquals("mcaquests:capital_villager_title",
                RewardTypes.CAPITAL_VILLAGER_TITLE.id().toString());
    }

    @Test
    @DisplayName("capital_title round-trips and names the four grantable ranks only")
    void titleCodec() {
        CapitalTitleReward reward = assertInstanceOf(CapitalTitleReward.class,
                parse("{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\"}"));

        assertEquals("knight", reward.title());
        assertEquals(Optional.empty(), reward.femaleTitle());
        assertTrue(error("{\"type\":\"mcaquests:capital_title\",\"title\":\"queen\"}")
                .contains("unknown title 'queen'"));
    }

    @Test
    @DisplayName("capital_title picks the feminine constant, and female_title overrides it")
    void titleGendering() {
        CapitalTitleReward knight = (CapitalTitleReward)
                parse("{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\"}");
        assertEquals("KNIGHT", knight.nobleTitleConstant(false));
        assertEquals("DAME", knight.nobleTitleConstant(true));

        CapitalTitleReward duke = (CapitalTitleReward)
                parse("{\"type\":\"mcaquests:capital_title\",\"title\":\"archduke\"}");
        assertEquals("ARCHDUKE", duke.nobleTitleConstant(false));
        assertEquals("ARCHDUCHESS", duke.nobleTitleConstant(true));

        CapitalTitleReward overridden = (CapitalTitleReward) parse(
                "{\"type\":\"mcaquests:capital_title\",\"title\":\"lord\",\"female_title\":\"duchess\"}");
        assertEquals("LORD", overridden.nobleTitleConstant(false));
        assertEquals("DUCHESS", overridden.nobleTitleConstant(true),
                "an explicit female_title is the server's choice and wins over the default pairing");
    }

    @Test
    @DisplayName("female_title rejects malformed fields and titles Capitals cannot grant")
    void invalidFemaleTitleIsNotSilentlyDropped() {
        assertTrue(error("{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\",\"female_title\":{}}")
                .contains("female_title"));
        assertTrue(error("{\"type\":\"mcaquests:capital_title\",\"title\":\"knight\",\"female_title\":\"queen\"}")
                .contains("unknown female_title 'queen'"));
    }

    @Test
    @DisplayName("capital_chronicle keeps its key and heralds by default")
    void chronicleCodec() {
        CapitalChronicleReward reward = assertInstanceOf(CapitalChronicleReward.class,
                parse("{\"type\":\"mcaquests:capital_chronicle\","
                        + "\"key\":\"mcaquests.chronicle.capitals.royal_escort\"}"));

        assertEquals("mcaquests.chronicle.capitals.royal_escort", reward.key());
        assertTrue(reward.herald());
        assertEquals(Optional.empty(), reward.fallback());
        assertFalse(((CapitalChronicleReward) parse("{\"type\":\"mcaquests:capital_chronicle\","
                + "\"key\":\"k\",\"herald\":false}")).herald());
    }

    @Test
    @DisplayName("chronicle entries render bundled translations on a dedicated server")
    void chronicleUsesBundledTextWithoutClientLanguage() {
        CapitalChronicleReward reward = new CapitalChronicleReward(
                "mcaquests.chronicle.capitals.royal_escort", true);
        String entry = reward.entryText("Alex", "Oakvale");
        assertTrue(entry.contains("Alex"), entry);
        assertTrue(entry.contains("Oakvale"), entry);
        assertFalse(entry.contains("mcaquests.chronicle."), entry);
    }

    @Test
    @DisplayName("custom chronicle rewards can provide server fallback text")
    void chronicleCustomFallback() {
        CapitalChronicleReward reward = (CapitalChronicleReward) parse(
                "{\"type\":\"mcaquests:capital_chronicle\",\"key\":\"example:service\","
                        + "\"fallback\":\"%s served %s faithfully.\"}");
        assertEquals("Alex served Oakvale faithfully.", reward.entryText("Alex", "Oakvale"));
        assertTrue(error("{\"type\":\"mcaquests:capital_chronicle\",\"key\":\"example:service\",\"fallback\":{}}")
                .contains("fallback"));
    }

    @Test
    @DisplayName("capital_villager_title defaults to the giver and refuses the player-only rank")
    void villagerTitleCodec() {
        CapitalVillagerTitleReward reward = assertInstanceOf(CapitalVillagerTitleReward.class,
                parse("{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"lord\"}"));

        assertEquals(VillagerTarget.SELF, reward.villager());
        assertEquals("LORD", reward.nobleTitleConstant(false));
        assertEquals("LADY", reward.nobleTitleConstant(true));
        assertTrue(error("{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"archduke\"}")
                .contains("unknown title 'archduke'"));
    }

    @Test
    @DisplayName("capital_villager_title can name a court office as its subject")
    void villagerTitleTakesATarget() {
        CapitalVillagerTitleReward reward = (CapitalVillagerTitleReward)
                parse("{\"type\":\"mcaquests:capital_villager_title\","
                        + "\"villager\":{\"mode\":\"capital_role\",\"role\":\"heir\"},\"title\":\"knight\"}");

        assertEquals(VillagerTarget.Mode.CAPITAL_ROLE, reward.villager().mode());
        assertEquals(Optional.of(CapitalRole.HEIR), reward.villager().role());
        assertEquals("DAME", reward.nobleTitleConstant(true));
    }

    @Test
    @DisplayName("villager title rewards validate target requirements during decoding")
    void villagerTitleRejectsUnresolvableTargets() {
        for (String target : new String[]{"{\"mode\":\"capital_role\"}",
                "{\"mode\":\"capital_role\",\"role\":\"archduke\"}", "{\"mode\":\"uuid\"}",
                "{\"mode\":\"profession\"}", "{\"mode\":\"family\",\"relation\":\"cousin\"}"}) {
            assertTrue(error("{\"type\":\"mcaquests:capital_villager_title\",\"villager\":"
                    + target + ",\"title\":\"knight\"}").contains("capital_villager_title"), target);
        }
    }

    @Test
    @DisplayName("a title's giver or explicit recipient need not be loaded when the reward lands")
    void persistentRecipientsDoNotNeedLoadedEntities() {
        UUID originalGiver = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID explicitRecipient = UUID.fromString("00000000-0000-0000-0000-000000000002");
        QuestReward.RewardContext context = new QuestReward.RewardContext(originalGiver,
                Component.literal("Original giver"), new ResourceLocation("minecraft:the_nether"),
                OptionalInt.of(1), new ResourceLocation("mcaquests:test"));
        CapitalVillagerTitleReward self = (CapitalVillagerTitleReward) parse(
                "{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"lord\"}");
        CapitalVillagerTitleReward explicit = (CapitalVillagerTitleReward) parse(
                "{\"type\":\"mcaquests:capital_villager_title\",\"title\":\"lord\","
                        + "\"villager\":{\"mode\":\"uuid\",\"uuid\":\"" + explicitRecipient + "\"}}");
        assertEquals(Optional.of(originalGiver), self.recipientUuid(null, null, null, context, null));
        assertEquals(Optional.of(explicitRecipient), explicit.recipientUuid(null, null, null, context, null));
    }
}
