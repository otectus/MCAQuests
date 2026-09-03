package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.support.TestConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.project.objective.ProjectTalkObjective;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "talk to N cartographers" progression that used to stall at 0/3.
 *
 * <p>Two defects fed that report. Both objectives compared profession ids with {@code equals}, so a
 * datapack asking for {@code minecraft:cartographer} never matched a villager whose profession id
 * carried a different namespace, whatever {@code professionMatchingMode} was set to. And the regular
 * quest objective counted <em>interactions</em> rather than <em>distinct villagers</em>, so it drifted
 * the other way — three clicks on one cartographer finished a "three cartographers" objective.
 */
class TalkObjectiveTest {

    private static final ResourceLocation VANILLA_CARTOGRAPHER = ResourceLocation.withDefaultNamespace("cartographer");
    private static final ResourceLocation MCA_CARTOGRAPHER = ResourceLocation.fromNamespaceAndPath("mca", "cartographer");
    private static final ResourceLocation FARMER = ResourceLocation.withDefaultNamespace("farmer");

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @BeforeAll
    static void loadCommonConfigDefaults() {
        // PORT: 1.20.1's ModConfigSpec#setConfig(CommentedConfig) became acceptConfig(ILoadedConfig);
        // TestConfig owns that wrapping now, so this is the same attach in one call.
        TestConfig.ensureCommonLoaded();
    }

    @AfterEach
    void restoreDefaults() {
        McaQuestsConfig.COMMON.professionMatchingMode.set(ProfessionMatchingMode.NORMALIZED);
    }

    private static TalkToProfessionObjective quest(int count) {
        return new TalkToProfessionObjective(VANILLA_CARTOGRAPHER, count);
    }

    private static ProjectTalkObjective project(int count) {
        return new ProjectTalkObjective(VANILLA_CARTOGRAPHER, count);
    }

    @Nested
    @DisplayName("profession matching honours the configured mode")
    class Matching {

        @Test
        @DisplayName("an exact id matches in every mode")
        void exactAlwaysMatches() {
            for (ProfessionMatchingMode mode : ProfessionMatchingMode.values()) {
                McaQuestsConfig.COMMON.professionMatchingMode.set(mode);
                assertTrue(quest(1).matches(VANILLA_CARTOGRAPHER), "quest objective, mode " + mode);
                assertTrue(project(1).matches(VANILLA_CARTOGRAPHER), "project objective, mode " + mode);
            }
        }

        @Test
        @DisplayName("NORMALIZED matches across namespaces, for BOTH objective types")
        void normalizedIgnoresNamespace() {
            McaQuestsConfig.COMMON.professionMatchingMode.set(ProfessionMatchingMode.NORMALIZED);
            assertTrue(quest(1).matches(MCA_CARTOGRAPHER),
                    "minecraft:cartographer should match mca:cartographer under NORMALIZED");
            assertTrue(project(1).matches(MCA_CARTOGRAPHER),
                    "the project objective must use the same rule as the quest objective");
        }

        @Test
        @DisplayName("STRICT rejects a different namespace, for BOTH objective types")
        void strictRequiresExactId() {
            McaQuestsConfig.COMMON.professionMatchingMode.set(ProfessionMatchingMode.STRICT);
            assertFalse(quest(1).matches(MCA_CARTOGRAPHER));
            assertFalse(project(1).matches(MCA_CARTOGRAPHER));
        }

        @Test
        @DisplayName("LOOSE still matches across namespaces, for BOTH objective types")
        void looseMatchesAcrossNamespaces() {
            McaQuestsConfig.COMMON.professionMatchingMode.set(ProfessionMatchingMode.LOOSE);
            assertTrue(quest(1).matches(MCA_CARTOGRAPHER));
            assertTrue(project(1).matches(MCA_CARTOGRAPHER));
        }

        @Test
        @DisplayName("a different profession never matches, whatever the mode")
        void wrongProfessionNeverMatches() {
            for (ProfessionMatchingMode mode : ProfessionMatchingMode.values()) {
                McaQuestsConfig.COMMON.professionMatchingMode.set(mode);
                assertFalse(quest(1).matches(FARMER), "quest objective, mode " + mode);
                assertFalse(project(1).matches(FARMER), "project objective, mode " + mode);
            }
        }

        @Test
        @DisplayName("a villager with no profession never matches")
        void nullProfessionNeverMatches() {
            assertFalse(quest(1).matches(null));
            assertFalse(project(1).matches(null));
            assertFalse(ProfessionMatcher.matches(VANILLA_CARTOGRAPHER, null));
        }
    }

    /**
     * The counting rule the objective text now promises: "different villagers". Exercised against
     * {@link ObjectiveProgress} directly, which is where the dedupe lives — the event handler simply
     * refuses to increment when {@code markTalkedTo} reports a repeat.
     */
    @Nested
    @DisplayName("counts distinct villagers")
    class Distinctness {

        /** Mirrors QuestProgressEvents.creditTalk: only advance when this villager is new. */
        private void credit(ObjectiveProgress progress, TalkToProfessionObjective objective, UUID villager) {
            if (progress.count() >= objective.required()) {
                return;
            }
            if (progress.markTalkedTo(villager)) {
                progress.add(1);
            }
        }

        @Test
        @DisplayName("three different cartographers progress 0 -> 1 -> 2 -> 3")
        void threeDistinctVillagersComplete() {
            TalkToProfessionObjective objective = quest(3);
            ObjectiveProgress progress = new ObjectiveProgress();
            assertEquals(0, progress.count());

            credit(progress, objective, UUID.randomUUID());
            assertEquals(1, progress.count());
            credit(progress, objective, UUID.randomUUID());
            assertEquals(2, progress.count());
            credit(progress, objective, UUID.randomUUID());
            assertEquals(3, progress.count());
            assertTrue(progress.count() >= objective.required(), "0/3 must actually reach 3/3");
        }

        @Test
        @DisplayName("talking to the same cartographer repeatedly stays at 1")
        void repeatingOneVillagerStaysAtOne() {
            TalkToProfessionObjective objective = quest(3);
            ObjectiveProgress progress = new ObjectiveProgress();
            UUID sameVillager = UUID.randomUUID();

            for (int i = 0; i < 10; i++) {
                credit(progress, objective, sameVillager);
            }
            assertEquals(1, progress.count(), "one villager can only ever count once");
        }

        @Test
        @DisplayName("a duplicated notification for one conversation credits once")
        void duplicateSignalIsIdempotent() {
            // Both the interaction hook and an MCA: Conversations signal can report the same
            // conversation; dedupe by villager UUID is what makes that harmless.
            TalkToProfessionObjective objective = quest(3);
            ObjectiveProgress progress = new ObjectiveProgress();
            UUID villager = UUID.randomUUID();

            credit(progress, objective, villager); // base interaction hook
            credit(progress, objective, villager); // MCA: Conversations signal for the same talk
            assertEquals(1, progress.count());
        }

        @Test
        @DisplayName("the talked-to set survives a save/load round trip")
        void talkedToSurvivesReload() {
            ObjectiveProgress progress = new ObjectiveProgress();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            progress.markTalkedTo(first);
            progress.add(1);
            progress.markTalkedTo(second);
            progress.add(1);

            ObjectiveProgress reloaded = ObjectiveProgress.load(progress.save());

            assertEquals(2, reloaded.count());
            assertTrue(reloaded.hasTalkedTo(first), "a villager counted before the reload must stay counted");
            assertTrue(reloaded.hasTalkedTo(second));
            assertFalse(reloaded.markTalkedTo(first), "reloading must not let the same villager count again");
            assertTrue(reloaded.markTalkedTo(UUID.randomUUID()), "a genuinely new villager still counts");
        }

        @Test
        @DisplayName("a pre-1.1.0 progress tag with no talked_to list still loads")
        void legacyProgressLoads() {
            CompoundTag legacy = new CompoundTag();
            legacy.putInt("count", 2);

            ObjectiveProgress loaded = ObjectiveProgress.load(legacy);

            assertEquals(2, loaded.count(), "existing progress must be preserved on upgrade");
            assertTrue(loaded.markTalkedTo(UUID.randomUUID()), "and the objective keeps working afterwards");
        }

        @Test
        @DisplayName("an objective with no talked-to villagers writes no extra NBT")
        void emptySetIsNotSerialised() {
            // Keeps old saves byte-identical for the many objectives that never touch this field.
            CompoundTag tag = new ObjectiveProgress(3).save();
            assertFalse(tag.contains("talked_to"));
        }
    }
}
