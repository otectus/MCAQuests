package dev.otectus.mcaquests.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqChapterCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqQuestCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqTaskCompletedCondition;
import dev.otectus.mcaquests.quest.condition.leaf.FtbqWhenMissing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code when_missing} truth table for the three {@code mcaquests:ftbq_*} conditions (spec §17,
 * §29.1 #6), driven through a stubbed {@link FtbqBridge} installed via the public
 * {@link FtbqBridge.Holder#set}. This is the whole point of the bridge seam: these conditions, their
 * codecs, and this test all run with no FTB jars anywhere on the classpath.
 *
 * <p>{@code Holder} is JVM-global (a {@code volatile static} on an interface), so every test restores
 * the previous bridge in {@code @AfterEach} to avoid leaking a stub into unrelated tests run in the
 * same JVM/worker.
 */
class FtbqConditionPolicyTest {

    private static final String HEX = "1A2B3C4D5E6F7081";
    private static final QuestContext CONTEXT =
            new QuestContext(null, null, null, new ResourceLocation("mcaquests", "dummy"), null);

    /** One condition constructor per type, so the truth table below runs against all three. */
    private interface Factory {
        QuestCondition create(String id, FtbqWhenMissing whenMissing);
    }

    private static final List<Factory> FACTORIES = List.of(
            FtbqQuestCompletedCondition::new,
            FtbqChapterCompletedCondition::new,
            FtbqTaskCompletedCondition::new);

    private FtbqBridge previous;

    @BeforeEach
    void captureExistingBridge() {
        previous = FtbqBridge.Holder.get();
    }

    @AfterEach
    void restoreExistingBridge() {
        FtbqBridge.Holder.set(previous);
    }

    /** A fully-controllable stub: same exists/completed answer regardless of quest/chapter/task kind. */
    private static final class StubBridge implements FtbqBridge {
        boolean available = true;
        boolean exists = true;
        boolean completed = false;
        boolean throwing = false;

        private void maybeThrow() {
            if (throwing) {
                throw new IllegalStateException("stubbed bridge failure");
            }
        }

        @Override
        public boolean isAvailable() {
            maybeThrow();
            return available;
        }

        @Override
        public boolean isQuestCompleted(ServerPlayer player, String hexId) {
            maybeThrow();
            return completed;
        }

        @Override
        public boolean isChapterCompleted(ServerPlayer player, String hexId) {
            maybeThrow();
            return completed;
        }

        @Override
        public boolean isTaskCompleted(ServerPlayer player, String hexId) {
            maybeThrow();
            return completed;
        }

        @Override
        public boolean questIdExists(String hexId) {
            maybeThrow();
            return exists;
        }

        @Override
        public boolean chapterIdExists(String hexId) {
            maybeThrow();
            return exists;
        }

        @Override
        public boolean taskIdExists(String hexId) {
            maybeThrow();
            return exists;
        }

        @Override
        public boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId) {
            return false;
        }

        @Override
        public void recheckAll(ServerPlayer player) {
        }

        @Override
        public int[] integrationObjectCounts() {
            return new int[]{0, 0};
        }
    }

    @Test
    void bridgeUnavailableFallsBackToWhenMissingBothWays() {
        StubBridge stub = new StubBridge();
        stub.available = false;
        stub.exists = true;
        stub.completed = true; // must be ignored: unavailable short-circuits before completion is read
        FtbqBridge.Holder.set(stub);

        for (Factory factory : FACTORIES) {
            assertFalse(factory.create(HEX, FtbqWhenMissing.NOT_MET).test(CONTEXT),
                    "unavailable + not_met -> false");
            assertTrue(factory.create(HEX, FtbqWhenMissing.MET).test(CONTEXT),
                    "unavailable + met -> true");
        }
    }

    @Test
    void availableAndIdExistsAndCompletedReturnsTrueRegardlessOfWhenMissing() {
        StubBridge stub = new StubBridge();
        stub.available = true;
        stub.exists = true;
        stub.completed = true;
        FtbqBridge.Holder.set(stub);

        for (Factory factory : FACTORIES) {
            assertTrue(factory.create(HEX, FtbqWhenMissing.NOT_MET).test(CONTEXT),
                    "completed -> true even though when_missing is not_met");
            assertTrue(factory.create(HEX, FtbqWhenMissing.MET).test(CONTEXT),
                    "completed -> true");
        }
    }

    @Test
    void availableAndIdExistsAndIncompleteReturnsFalseRegardlessOfWhenMissing() {
        StubBridge stub = new StubBridge();
        stub.available = true;
        stub.exists = true;
        stub.completed = false;
        FtbqBridge.Holder.set(stub);

        for (Factory factory : FACTORIES) {
            assertFalse(factory.create(HEX, FtbqWhenMissing.NOT_MET).test(CONTEXT),
                    "incomplete -> false");
            assertFalse(factory.create(HEX, FtbqWhenMissing.MET).test(CONTEXT),
                    "incomplete -> false even though when_missing is met "
                            + "(the bridge can positively distinguish incomplete from unknown here)");
        }
    }

    @Test
    void availableButIdDoesNotExistFallsBackToWhenMissing() {
        StubBridge stub = new StubBridge();
        stub.available = true;
        stub.exists = false;
        stub.completed = true; // must be ignored: existence gates whether completed is even consulted
        FtbqBridge.Holder.set(stub);

        for (Factory factory : FACTORIES) {
            assertFalse(factory.create(HEX, FtbqWhenMissing.NOT_MET).test(CONTEXT),
                    "unknown id + not_met -> false");
            assertTrue(factory.create(HEX, FtbqWhenMissing.MET).test(CONTEXT),
                    "unknown id + met -> true");
        }
    }

    @Test
    void bridgeThrowingFallsBackToWhenMissing() {
        StubBridge stub = new StubBridge();
        stub.available = true;
        stub.exists = true;
        stub.completed = true;
        stub.throwing = true;
        FtbqBridge.Holder.set(stub);

        for (Factory factory : FACTORIES) {
            assertFalse(factory.create(HEX, FtbqWhenMissing.NOT_MET).test(CONTEXT),
                    "bridge throws + not_met -> false");
            assertTrue(factory.create(HEX, FtbqWhenMissing.MET).test(CONTEXT),
                    "bridge throws + met -> true");
        }
    }

    // --- codec parsing: default when_missing, explicit met, bad hex -> parse error --------------------

    private static <T> DataResult<T> parse(Codec<T> codec, String json) {
        JsonElement element = JsonParser.parseString(json);
        return codec.parse(JsonOps.INSTANCE, element);
    }

    @Test
    void codecDefaultsWhenMissingToNotMet() {
        DataResult<FtbqQuestCompletedCondition> result =
                parse(FtbqQuestCompletedCondition.CODEC.codec(), "{\"quest\":\"" + HEX + "\"}");
        assertTrue(result.result().isPresent(), "expected parse success");
        assertTrue(result.result().get().whenMissing() == FtbqWhenMissing.NOT_MET, "default is not_met");
    }

    @Test
    void codecAcceptsExplicitMet() {
        DataResult<FtbqTaskCompletedCondition> result =
                parse(FtbqTaskCompletedCondition.CODEC.codec(), "{\"task\":\"" + HEX + "\",\"when_missing\":\"met\"}");
        assertTrue(result.result().isPresent(), "expected parse success");
        assertTrue(result.result().get().whenMissing() == FtbqWhenMissing.MET);
    }

    @Test
    void codecAcceptsLeadingHash() {
        DataResult<FtbqChapterCompletedCondition> result =
                parse(FtbqChapterCompletedCondition.CODEC.codec(), "{\"chapter\":\"#" + HEX + "\"}");
        assertTrue(result.result().isPresent(), "leading '#' must be tolerated");
        assertTrue(result.result().get().chapter().equals("#" + HEX));
    }

    @Test
    void codecRejectsBadHexFormat() {
        DataResult<FtbqQuestCompletedCondition> tooLong =
                parse(FtbqQuestCompletedCondition.CODEC.codec(), "{\"quest\":\"1A2B3C4D5E6F70812\"}");
        assertTrue(tooLong.error().isPresent(), "17 hex chars must be rejected");

        DataResult<FtbqChapterCompletedCondition> badChars =
                parse(FtbqChapterCompletedCondition.CODEC.codec(), "{\"chapter\":\"not-hex\"}");
        assertTrue(badChars.error().isPresent(), "non-hex characters must be rejected");

        DataResult<FtbqTaskCompletedCondition> missingField =
                parse(FtbqTaskCompletedCondition.CODEC.codec(), "{}");
        assertTrue(missingField.error().isPresent(), "the id field is required");
    }

    /**
     * NOT asserted: DFU 6.0.8's {@code OptionalFieldCodec} swallows a present-but-invalid optional
     * field's decode error and silently falls back to the field's default (verified against the
     * decompiled {@code OptionalFieldCodec.decode}) — so {@code {"when_missing":"sometimes"}} quietly
     * becomes {@code not_met} instead of failing to parse. This is a pre-existing systemic quirk of
     * every {@code optionalFieldOf}-defaulted enum in this codebase (e.g. {@code TimeCondition.period}),
     * not something introduced here, and fixing it is out of scope for this task — the required id
     * field uses {@code fieldOf} (not optional), which does propagate errors correctly, as
     * {@link #codecRejectsBadHexFormat()} demonstrates.
     */
}
