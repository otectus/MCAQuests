package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.target.FrozenLocation;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads saved player data written by an older release and proves it survives a load/save/load cycle.
 *
 * <p>The fixtures under {@code src/test/resources/migration} are hand-written SNBT in exactly the shape
 * {@link PlayerQuestData#save()} produced <em>before</em> 1.5.4. There is no numeric data version in
 * this save format: every field is versioned by a {@code tag.contains(...)} presence check, so the only
 * way to know an old save still reads is to keep an old save. 1.5.4 introduces no new NBT field, so no
 * fixture here carries one; a future release that adds a key has to add it to {@link #ALWAYS_WRITTEN}
 * or to a fixture deliberately, which is what the unexpected-key test below is for.
 *
 * <p>SNBT has no comment syntax, so that note lives here rather than at the top of each file.
 *
 * <p>{@code active_quest_tracked_entity.snbt} deliberately carries every optional
 * {@link ObjectiveProgress} field at once ({@code target}, {@code elapsed}, {@code visited},
 * {@code talked_to}, {@code extra}) rather than only the two an escort/protect quest would write, so
 * that one fixture covers the whole progress shape.
 */
class SaveFixtureRoundTripTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /** Top-level keys {@link PlayerQuestData#save()} writes unconditionally, old fixture or not. */
    private static final Set<String> ALWAYS_WRITTEN =
            Set.of("active", "history", "titles", "stats", "offers");

    private static final List<String> FIXTURES = List.of(
            "active_quest_ordinary.snbt",
            "active_quest_tracked_entity.snbt",
            "active_quest_missing_definition.snbt",
            "history_completed.snbt",
            "omit_new_fields.snbt");

    private static CompoundTag fixture(String name) {
        try (InputStream in = SaveFixtureRoundTripTest.class.getResourceAsStream("/migration/" + name)) {
            assertNotNull(in, "missing fixture /migration/" + name);
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read fixture " + name, e);
        }
    }

    private static PlayerQuestData load(CompoundTag tag) {
        PlayerQuestData data = new PlayerQuestData();
        data.load(tag);
        return data;
    }

    @Test
    @DisplayName("every fixture survives load -> save -> load unchanged")
    void everyFixtureRoundTrips() {
        for (String name : FIXTURES) {
            PlayerQuestData first = load(fixture(name));
            CompoundTag saved = first.save();
            PlayerQuestData second = load(saved);
            assertSameData(name, first, second, saved);
            // Saving is idempotent: the second pass must not add, drop or change anything.
            assertEquals(saved, second.save(), name + ": second save differs from the first");
        }
    }

    @Test
    @DisplayName("an ordinary quest keeps its progress, frozen reward and unclaimed state")
    void ordinaryQuest() {
        PlayerQuestData data = load(load(fixture("active_quest_ordinary.snbt")).save());
        assertEquals(1, data.activeCount());
        ActiveQuest quest = data.active().get(0);
        assertEquals(ResourceLocation.fromNamespaceAndPath("mcaquests", "archer_cull_from_afar"), quest.questId());
        assertEquals(3, quest.progress(0).count());
        assertEquals(42, quest.frozenReward(0).orElse(-1));
        assertFalse(quest.rewardClaimed());
        assertEquals(0L, quest.suspendedTicks());
    }

    @Test
    @DisplayName("a tracked-entity objective keeps its target, elapsed time and frozen location")
    void trackedEntityQuest() {
        PlayerQuestData data = load(load(fixture("active_quest_tracked_entity.snbt")).save());
        ActiveQuest quest = data.active().get(0);
        assertEquals(ResourceLocation.fromNamespaceAndPath("mcaquests", "road_caravan_through"), quest.questId());
        assertNotNull(quest.progress(0).targetUuid());
        assertEquals(quest.progress(0).targetUuid(), quest.progress(1).targetUuid());
        assertEquals(1200L, quest.progress(1).elapsedTicks());
        assertEquals(2, quest.progress(1).visitedCount());
        assertEquals(2400L, quest.suspendedTicks());
        FrozenLocation frozen = quest.frozenLocation("other_village/2048/nearest_to_giver");
        assertNotNull(frozen, "the escort destination was not kept");
        assertEquals(11, frozen.villageId().orElse(-1));
        assertTrue(data.tracked().isPresent(), "the tracked reference was not kept");
    }

    @Test
    @DisplayName("a quest whose definition is not loaded persists with its progress intact")
    void missingDefinitionQuest() {
        // The Ice & Fire pack is conditional and is not mounted in tests, so nothing can resolve this
        // id. Persistence must not care: an unmountable quest is paused, never dropped.
        PlayerQuestData data = load(load(fixture("active_quest_missing_definition.snbt")).save());
        assertEquals(1, data.activeCount());
        ActiveQuest quest = data.active().get(0);
        assertEquals(ResourceLocation.fromNamespaceAndPath("mcaquests", "compat/iceandfire/fire_dragon_hunt"),
                quest.questId());
        assertEquals(1, quest.progress(0).count());
    }

    @Test
    @DisplayName("history keeps completed, abandoned and declined outcomes and their cooldowns")
    void historyOutcomes() {
        PlayerQuestData data = load(load(fixture("history_completed.snbt")).save());
        assertEquals(0, data.activeCount());
        QuestHistory history = data.history();
        ResourceLocation cull = ResourceLocation.fromNamespaceAndPath("mcaquests", "archer_cull_from_afar");
        ResourceLocation child = ResourceLocation.fromNamespaceAndPath("mcaquests", "relations_protect_my_child");
        ResourceLocation caravan = ResourceLocation.fromNamespaceAndPath("mcaquests", "road_caravan_through");
        UUID giver = UUID.fromString("7b1c4b6e-1e5c-4a2f-9c3d-8a5f6e7d0b11");
        assertEquals(2, history.outcomeCount(cull, QuestHistory.Outcome.COMPLETED));
        assertEquals(1, history.outcomeCount(child, QuestHistory.Outcome.ABANDONED));
        assertEquals(3, history.outcomeCount(caravan, QuestHistory.Outcome.DECLINED));
        assertEquals(2, history.completionCountByGiver(cull, giver));
        assertEquals(1, history.outcomeCountByGiver(child, giver, QuestHistory.Outcome.ABANDONED));
        assertEquals(3, history.outcomeCountByGiver(caravan, giver, QuestHistory.Outcome.DECLINED));
        assertEquals(Optional.of(88000L), history.cooldownRemaining(cull, giver, 60000L));
    }

    @Test
    @DisplayName("a save written before the newer fields existed still loads")
    void omittedNewFieldsLoad() {
        PlayerQuestData data = load(fixture("omit_new_fields.snbt"));
        assertEquals(1, data.activeCount());
        ActiveQuest quest = data.active().get(0);
        assertEquals(5, quest.progress(0).count());
        assertTrue(quest.startDayTime().isEmpty());
        assertTrue(quest.villageId().isEmpty());
        assertFalse(quest.readyNotified());
        assertEquals(0L, quest.suspendedTicks());
        assertTrue(quest.frozenReward(0).isEmpty());
        assertTrue(data.tracked().isEmpty());
        assertEquals(1, data.history().completionCount(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "archer_cull_from_afar")));
    }

    @Test
    @DisplayName("saving an old fixture adds no top-level key beyond the ones save() always writes")
    void savedTagAddsNoUnexpectedTopLevelKey() {
        for (String name : FIXTURES) {
            CompoundTag original = fixture(name);
            CompoundTag saved = load(original).save();
            for (String key : saved.getAllKeys()) {
                assertTrue(original.contains(key) || ALWAYS_WRITTEN.contains(key),
                        name + ": save() wrote unexpected top-level key '" + key + "'");
            }
        }
    }

    // --- Comparison helpers ------------------------------------------------------------------------

    private static void assertSameData(String name, PlayerQuestData a, PlayerQuestData b,
                                       CompoundTag savedA) {
        assertEquals(a.activeCount(), b.activeCount(), name + ": active count");
        ListTag activeTags = savedA.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < a.activeCount(); i++) {
            assertSameQuest(name + " active[" + i + "]", a.active().get(i), b.active().get(i),
                    activeTags.getCompound(i));
        }
        assertEquals(a.tracked(), b.tracked(), name + ": tracked quest");
        assertEquals(a.history().save(), b.history().save(), name + ": history");
    }

    private static void assertSameQuest(String where, ActiveQuest a, ActiveQuest b, CompoundTag savedA) {
        assertEquals(a.questId(), b.questId(), where + ": quest id");
        assertEquals(a.villagerUuid(), b.villagerUuid(), where + ": villager");
        assertEquals(a.villagerName(), b.villagerName(), where + ": villager name");
        assertEquals(a.villagerProfession(), b.villagerProfession(), where + ": profession");
        assertEquals(a.dimension(), b.dimension(), where + ": dimension");
        assertEquals(a.startGameTime(), b.startGameTime(), where + ": start");
        assertEquals(a.startDayTime(), b.startDayTime(), where + ": start day");
        assertEquals(a.villageId(), b.villageId(), where + ": village");
        assertEquals(a.situationInstance(), b.situationInstance(), where + ": situation");
        assertEquals(a.rewardClaimed(), b.rewardClaimed(), where + ": claimed");
        assertEquals(a.readyNotified(), b.readyNotified(), where + ": ready notified");
        assertEquals(a.suspendedTicks(), b.suspendedTicks(), where + ": suspended ticks");

        // frozenRewards and frozenLocations have per-key accessors only, so the fixture's own saved tag
        // supplies the key sets to look up.
        CompoundTag rewards = savedA.getCompound("frozen_rewards");
        for (String key : rewards.getAllKeys()) {
            int index = Integer.parseInt(key);
            assertEquals(a.frozenReward(index), b.frozenReward(index), where + ": frozen reward " + key);
        }
        CompoundTag locations = savedA.getCompound("frozen_locations");
        for (String fingerprint : locations.getAllKeys()) {
            assertEquals(a.frozenLocation(fingerprint), b.frozenLocation(fingerprint),
                    where + ": frozen location " + fingerprint);
        }

        ListTag progressTags = savedA.getList("progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < progressTags.size(); i++) {
            assertSameProgress(where + " progress[" + i + "]", a.progress(i), b.progress(i),
                    progressTags.getCompound(i));
        }
    }

    private static void assertSameProgress(String where, ObjectiveProgress a, ObjectiveProgress b,
                                           CompoundTag savedA) {
        assertEquals(a.count(), b.count(), where + ": count");
        assertEquals(a.elapsedTicks(), b.elapsedTicks(), where + ": elapsed");
        assertEquals(a.targetUuid(), b.targetUuid(), where + ": target");
        assertEquals(a.visitedCount(), b.visitedCount(), where + ": visited count");
        assertEquals(a.extra(), b.extra(), where + ": extra");
        // visitedPositions and talkedTo are membership-only sets, so ask about the members the fixture
        // itself recorded.
        for (long packed : savedA.getLongArray("visited")) {
            assertTrue(a.hasVisited(BlockPos.of(packed)), where + ": missing visited position");
            assertTrue(b.hasVisited(BlockPos.of(packed)), where + ": visited position lost");
        }
        Set<UUID> talked = new HashSet<>();
        ListTag list = savedA.getList("talked_to", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            talked.add(UUID.fromString(list.getString(i)));
        }
        for (UUID villager : talked) {
            assertTrue(a.hasTalkedTo(villager), where + ": missing talked_to entry");
            assertTrue(b.hasTalkedTo(villager), where + ": talked_to entry lost");
        }
    }
}
