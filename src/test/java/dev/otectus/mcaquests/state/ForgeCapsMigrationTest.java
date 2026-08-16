package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 8.3 world-upgrade shim against a canned 1.20.1 {@code ForgeCaps} NBT blob: Forge stored
 * {@link PlayerQuestData} at {@code playerdata/<uuid>.dat → ForgeCaps → "mcaquests:player_quests"};
 * {@link ForgeCapsMigration#importFromForgeCaps} must lift that tag into the NeoForge attachment
 * exactly once and never invent data when the blob is absent.
 */
class ForgeCapsMigrationTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "well_repair");
    private static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath("mcaquests", "village_friend");

    /** Builds the on-disk player-file shape the 1.20.1 Forge build wrote. */
    private static CompoundTag legacyPlayerFile(PlayerQuestData source) {
        CompoundTag caps = new CompoundTag();
        caps.put("mcaquests:player_quests", source.save());
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3465); // 1.20.1 — irrelevant to the shim, present for realism
        root.put("ForgeCaps", caps);
        return root;
    }

    private static PlayerQuestData legacyData() {
        PlayerQuestData source = new PlayerQuestData();
        source.history().recordCompletion(QUEST, UUID.fromString("a5a5a5a5-0000-0000-0000-000000000001"));
        source.titles().grantGlobal(TITLE);
        source.titles().grantVillage(7, TITLE);
        return source;
    }

    @Test
    void importsLegacyForgeCapsBlobAndMarksMigrated() {
        PlayerQuestData fresh = new PlayerQuestData();
        assertTrue(fresh.isEmpty(), "precondition: attachment starts empty");

        boolean imported = ForgeCapsMigration.importFromForgeCaps(legacyPlayerFile(legacyData()), fresh);

        assertTrue(imported);
        assertFalse(fresh.isEmpty());
        assertEquals(1, fresh.history().completionCount(QUEST));
        assertTrue(fresh.titles().hasGlobal(TITLE));
        assertTrue(fresh.titles().hasVillage(7, TITLE));
        assertTrue(fresh.migratedFromForge(), "shim must mark the one-shot import");
    }

    @Test
    void migratedFlagSurvivesAttachmentSaveLoadRoundTrip() {
        PlayerQuestData fresh = new PlayerQuestData();
        ForgeCapsMigration.importFromForgeCaps(legacyPlayerFile(legacyData()), fresh);

        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(fresh.save());

        assertTrue(reloaded.migratedFromForge(),
                "the flag must persist so the import never runs twice across sessions");
        assertEquals(1, reloaded.history().completionCount(QUEST));
    }

    @Test
    void doesNothingWithoutForgeCaps() {
        PlayerQuestData fresh = new PlayerQuestData();
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3465);

        assertFalse(ForgeCapsMigration.importFromForgeCaps(root, fresh));
        assertTrue(fresh.isEmpty());
        assertFalse(fresh.migratedFromForge());
    }

    @Test
    void doesNothingWhenForgeCapsLacksOurKey() {
        PlayerQuestData fresh = new PlayerQuestData();
        CompoundTag caps = new CompoundTag();
        caps.put("somemod:other_cap", new CompoundTag());
        CompoundTag root = new CompoundTag();
        root.put("ForgeCaps", caps);

        assertFalse(ForgeCapsMigration.importFromForgeCaps(root, fresh));
        assertTrue(fresh.isEmpty());
    }
}
