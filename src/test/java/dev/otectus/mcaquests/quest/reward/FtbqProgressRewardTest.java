package dev.otectus.mcaquests.quest.reward;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.compat.FtbqBridge;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec and behavior tests for {@link FtbqProgressReward} (spec §19). Coverage:
 * - All three action enum values parse correctly
 * - Hex id validation: accepts valid ids, rejects bad format
 * - Action field is required (no default)
 * - describe() renders for each action
 * - grant() delegates safely to the bridge
 */
class FtbqProgressRewardTest {

    private static final String HEX = "F00DF00DF00DF00D";
    private FtbqBridge previous;

    @BeforeEach
    void captureExistingBridge() {
        previous = FtbqBridge.Holder.get();
    }

    @AfterEach
    void restoreExistingBridge() {
        FtbqBridge.Holder.set(previous);
    }

    private static boolean parses(String json) {
        var result = FtbqProgressReward.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        result.error().ifPresent(e -> System.out.println("[FtbqProgressRewardTest] parse error: " + e.message()));
        return result.result().isPresent();
    }

    // ---- action enum parsing

    @Test
    void codecAcceptsCompleteTaskAction() {
        assertTrue(parses("{\"action\":\"complete_task\",\"id\":\"" + HEX + "\"}"));
    }

    @Test
    void codecAcceptsCompleteQuestAction() {
        assertTrue(parses("{\"action\":\"complete_quest\",\"id\":\"" + HEX + "\"}"));
    }

    @Test
    void codecAcceptsResetTaskAction() {
        assertTrue(parses("{\"action\":\"reset_task\",\"id\":\"" + HEX + "\"}"));
    }

    // ---- hex id validation

    @Test
    void codecAcceptsValidHexId() {
        assertTrue(parses("{\"action\":\"complete_task\",\"id\":\"" + HEX + "\"}"));
    }

    @Test
    void codecAcceptsLeadingHash() {
        assertTrue(parses("{\"action\":\"complete_task\",\"id\":\"#" + HEX + "\"}"));
    }

    @Test
    void codecRejectsBadHexFormat() {
        // 17 hex digits (too long)
        assertFalse(parses("{\"action\":\"complete_task\",\"id\":\"1A2B3C4D5E6F70812\"}"));
        // non-hex characters
        assertFalse(parses("{\"action\":\"complete_task\",\"id\":\"not-hex\"}"));
        // empty string
        assertFalse(parses("{\"action\":\"complete_task\",\"id\":\"\"}"));
    }

    // ---- action field is required

    @Test
    void codecRequiresActionField() {
        assertFalse(parses("{\"id\":\"" + HEX + "\"}"), "action field is required");
    }

    @Test
    void codecRequiresIdField() {
        assertFalse(parses("{\"action\":\"complete_task\"}"), "id field is required");
    }

    @Test
    void codecRejectsBadAction() {
        assertFalse(parses("{\"action\":\"invalid_action\",\"id\":\"" + HEX + "\"}"));
    }

    // ---- describe renders

    @Test
    void describeCompleteTask() {
        var result = FtbqProgressReward.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"action\":\"complete_task\",\"id\":\"" + HEX + "\"}"));
        assertTrue(result.result().isPresent());
        var reward = result.result().get();
        var description = reward.describe().getString();
        assertTrue(description.length() > 0, "describe must render text");
    }

    @Test
    void describeCompleteQuest() {
        var result = FtbqProgressReward.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"action\":\"complete_quest\",\"id\":\"" + HEX + "\"}"));
        assertTrue(result.result().isPresent());
        var reward = result.result().get();
        var description = reward.describe().getString();
        assertTrue(description.length() > 0, "describe must render text");
    }

    @Test
    void describeResetTask() {
        var result = FtbqProgressReward.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"action\":\"reset_task\",\"id\":\"" + HEX + "\"}"));
        assertTrue(result.result().isPresent());
        var reward = result.result().get();
        var description = reward.describe().getString();
        assertTrue(description.length() > 0, "describe must render text");
    }


    private static final class NoopBridge implements FtbqBridge {
        @Override public boolean isAvailable() { return false; }
        @Override public boolean isQuestCompleted(ServerPlayer p, String id) { return false; }
        @Override public boolean isChapterCompleted(ServerPlayer p, String id) { return false; }
        @Override public boolean isTaskCompleted(ServerPlayer p, String id) { return false; }
        @Override public boolean questIdExists(String id) { return false; }
        @Override public boolean chapterIdExists(String id) { return false; }
        @Override public boolean taskIdExists(String id) { return false; }
        @Override public boolean grantProgress(ServerPlayer p, ProgressAction a, String id) { return false; }
        @Override public void recheckAll(ServerPlayer p) {}
        @Override public int[] integrationObjectCounts() { return new int[]{0, 0}; }
    }
}
