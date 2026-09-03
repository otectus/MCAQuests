package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.support.TestBootstrap;
import dev.otectus.mcaquests.support.TestPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §14.5, last clause: a server handler must not act on a UUID, entity or quest that is not the
 * sending player's.
 *
 * <p>What can and cannot be tested here is worth stating plainly. {@code IPayloadContext} cannot be
 * constructed in a unit test — it is a NeoForge-internal implementation over a live connection — and
 * neither can {@code ServerPlayer}, so the handlers themselves cannot be invoked. But the handlers
 * deliberately do no deciding: each is a {@code ServerPlayer} cast followed by a call into a service,
 * and every service re-resolves the identifiers against the <em>player's own</em> state instead of
 * trusting the packet. That lookup is the authorization, and it is a pure function over
 * {@link PlayerQuestData} — so it is exercised directly here with a forged packet's worth of
 * identifiers, which is the same question the handler asks.
 *
 * <p>The one thing the handler owns rather than delegates — the {@code ServerPlayer} guard that stops
 * a client-side or otherwise unauthenticated sender from reaching the service at all — is asserted by
 * reading the sources, because there is no way to reach it at runtime without a server.
 */
class ServerHandlerAuthorityTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "well_repair");
    private static final ResourceLocation OTHER_QUEST =
            ResourceLocation.fromNamespaceAndPath("mcaquests", "lost_child");
    private static final ResourceLocation TITLE =
            ResourceLocation.fromNamespaceAndPath("mcaquests", "village_friend");
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");

    private static final UUID GIVER = UUID.fromString("11111111-0000-4000-8000-000000000001");
    /** A UUID the player has never accepted anything from: the forged half of every case below. */
    private static final UUID STRANGER = UUID.fromString("99999999-0000-4000-8000-000000000009");

    /** A player holding exactly one quest, accepted from exactly one villager. */
    private static PlayerQuestData holderOfOneQuest() {
        PlayerQuestData data = new PlayerQuestData();
        data.add(ActiveQuest.create(QUEST, GIVER, Component.literal("Anna"),
                ResourceLocation.withDefaultNamespace("farmer"), OVERWORLD, 1000L, 1, null));
        return data;
    }

    // ---------------------------------------------------------------- quest_abandon_from_log / quest_track

    @Test
    @DisplayName("a quest id the player does not hold authorizes nothing")
    void unheldQuestIdIsNotFound() {
        // QuestAbandonFromLogC2SPacket and QuestTrackC2SPacket both reduce to PlayerQuestData.find:
        // "abandon this" and "follow this" are only ever performed on what find returns, so a quest the
        // player does not hold cannot be abandoned or followed no matter what the packet says.
        PlayerQuestData data = holderOfOneQuest();

        assertEquals(Optional.empty(), data.find(OTHER_QUEST, GIVER),
                "a quest the player never accepted must not resolve, even from a real giver");
        assertFalse(data.hasActive(OTHER_QUEST, GIVER));
    }

    @Test
    @DisplayName("a villager UUID the player never dealt with authorizes nothing")
    void forgedVillagerUuidIsNotFound() {
        PlayerQuestData data = holderOfOneQuest();

        assertEquals(Optional.empty(), data.find(QUEST, STRANGER),
                "the right quest id under the wrong giver must not resolve");
        assertFalse(data.hasActive(QUEST, STRANGER));
        assertTrue(data.byVillager(STRANGER).isEmpty(),
                "a villager the player holds nothing from must yield nothing");
    }

    @Test
    @DisplayName("the pair is the authorization: the same quest from a second giver is a different quest")
    void bothHalvesMustMatch() {
        // The same quest can be active from two villagers, which is exactly why the packets carry both
        // halves. A lookup that matched on the quest id alone would let a client abandon or follow the
        // copy it did not name.
        PlayerQuestData data = holderOfOneQuest();
        UUID secondGiver = UUID.fromString("22222222-0000-4000-8000-000000000002");
        data.add(ActiveQuest.create(QUEST, secondGiver, Component.literal("Otto"), null, OVERWORLD,
                2000L, 1, null));

        assertEquals(GIVER, data.find(QUEST, GIVER).orElseThrow().villagerUuid());
        assertEquals(secondGiver, data.find(QUEST, secondGiver).orElseThrow().villagerUuid());
        assertEquals(Optional.empty(), data.find(QUEST, STRANGER),
                "two live copies must still not make a third, forged one resolvable");
    }

    @Test
    @DisplayName("tracking a quest the player does not hold follows nothing rather than following it")
    void trackingAnUnheldQuestFollowsNothing() {
        // QuestManager.track is data.find(...) piped straight into setTracked, so an unresolvable pair
        // clears the marker instead of pinning a quest the player never accepted.
        PlayerQuestData data = holderOfOneQuest();

        data.setTracked(data.find(QUEST, STRANGER).filter(quest -> !data.isTracked(quest)).orElse(null));

        assertTrue(data.tracked().isEmpty(), "a forged track request must leave nothing followed");

        // The same expression with an owned pair does follow it, so the assertion above is about
        // authorization and not about the expression being inert.
        data.setTracked(data.find(QUEST, GIVER).filter(quest -> !data.isTracked(quest)).orElse(null));
        assertEquals(QUEST, data.tracked().orElseThrow().questId());
    }

    // ---------------------------------------------------------------- open_standing

    @Test
    @DisplayName("the View Deeds membership rule is per dimension and per village")
    void standingMembershipIsScopedToOneVillage() {
        // OpenStandingC2SPacket.knowsVillage falls back to "does this player hold a title here", and a
        // forged (dimension, villageId) pair must not satisfy it. The address is the only thing the
        // client supplies, so this is the whole of what a forged packet could reach.
        PlayerQuestData data = new PlayerQuestData();
        data.titles().grantVillage(OVERWORLD, 7, TITLE);

        assertFalse(data.titles().forVillage(OVERWORLD, 7).isEmpty(), "the village the player stands in");
        assertTrue(data.titles().forVillage(OVERWORLD, 8).isEmpty(), "a neighbouring village id");
        assertTrue(data.titles().forVillage(NETHER, 7).isEmpty(),
                "the same village id in another dimension is another village");
    }

    // ---------------------------------------------------------------- the guard the handlers own

    @Test
    @DisplayName("every C2S handler refuses to act unless the sender is a ServerPlayer")
    void everyC2SHandlerGuardsOnServerPlayer() {
        // Asserted from the source because a handler cannot be invoked without an IPayloadContext, and
        // one cannot be built here. What matters is that no handler body is reachable by a sender the
        // server has not authenticated as a player -- which is a syntactic property of every one of
        // the nine, and stays true only for as long as somebody checks.
        List<String> c2s = List.of("OpenQuestMenuC2SPacket", "QuestDecisionC2SPacket", "QuestTurnInC2SPacket",
                "QuestAbandonC2SPacket", "ProjectContributeC2SPacket", "RequestJournalC2SPacket",
                "QuestAbandonFromLogC2SPacket", "OpenStandingC2SPacket", "QuestTrackC2SPacket");

        for (String simpleName : c2s) {
            String source = read(TestPaths.of("src", "main", "java", "dev", "otectus", "mcaquests",
                    "network", simpleName + ".java"));
            assertTrue(source.contains("context.player() instanceof ServerPlayer"),
                    simpleName + ".handle must reject a sender that is not a ServerPlayer");
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file, e);
        }
    }
}
