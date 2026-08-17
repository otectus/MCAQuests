package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // --- the two-file fallback -------------------------------------------------------------
    //
    // NeoForge rewrites the player file without the unrecognised ForgeCaps tag on the very next
    // save, so an import missed on the first login is missed forever. Vanilla's <uuid>.dat_old
    // holds the previous copy for one more save cycle, which makes it the last line of defence
    // against a transient read failure costing a 1.20.1 upgrader their whole quest log.

    private static final UUID PLAYER = UUID.fromString("b6b6b6b6-0000-0000-0000-00000000000f");

    private static void writePlayerFile(Path file, CompoundTag root) throws IOException {
        NbtIo.writeCompressed(root, file);
    }

    private static void writeUnreadableFile(Path file) throws IOException {
        Files.write(file, "this is not a gzipped NBT compound".getBytes(StandardCharsets.UTF_8));
    }

    private static void assertLegacyDataPresent(PlayerQuestData data) {
        assertEquals(1, data.history().completionCount(QUEST));
        assertTrue(data.titles().hasGlobal(TITLE));
        assertTrue(data.migratedFromForge());
    }

    @Test
    void importsFromDatOldWhenTheLiveFileNoLongerCarriesTheBlob(@TempDir Path playerData) throws IOException {
        // The upgrade already happened once and NeoForge has rewritten <uuid>.dat without ForgeCaps.
        CompoundTag rewritten = new CompoundTag();
        rewritten.putInt("DataVersion", 3955);
        writePlayerFile(playerData.resolve(PLAYER + ".dat"), rewritten);
        writePlayerFile(playerData.resolve(PLAYER + ".dat_old"), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh);
    }

    @Test
    void importsFromDatOldWhenTheLiveFileCannotBeRead(@TempDir Path playerData) throws IOException {
        writeUnreadableFile(playerData.resolve(PLAYER + ".dat"));
        writePlayerFile(playerData.resolve(PLAYER + ".dat_old"), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh);
    }

    @Test
    void prefersTheLiveFileOverTheBackup(@TempDir Path playerData) throws IOException {
        writePlayerFile(playerData.resolve(PLAYER + ".dat"), legacyPlayerFile(legacyData()));
        writeUnreadableFile(playerData.resolve(PLAYER + ".dat_old"));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh);
    }

    @Test
    void reportsAbsentForAPlayerWithNoLegacyData(@TempDir Path playerData) throws IOException {
        CompoundTag plain = new CompoundTag();
        plain.putInt("DataVersion", 3955);
        writePlayerFile(playerData.resolve(PLAYER + ".dat"), plain);

        PlayerQuestData fresh = new PlayerQuestData();
        // ABSENT, not FAILED: the caller uses that to stop re-reading the file on every login.
        assertEquals(ForgeCapsMigration.Outcome.ABSENT,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertTrue(fresh.isEmpty());
        assertFalse(fresh.migratedFromForge(), "no legacy data must mean no NBT written");
    }

    @Test
    void reportsAbsentWhenThePlayerHasNoFilesAtAll(@TempDir Path playerData) {
        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.ABSENT,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertTrue(fresh.isEmpty());
    }

    @Test
    void reportsFailedWhenNeitherFileCanBeRead(@TempDir Path playerData) throws IOException {
        writeUnreadableFile(playerData.resolve(PLAYER + ".dat"));
        writeUnreadableFile(playerData.resolve(PLAYER + ".dat_old"));

        PlayerQuestData fresh = new PlayerQuestData();
        // FAILED keeps the question open so the next login tries again instead of writing the
        // player off as quest-less.
        assertEquals(ForgeCapsMigration.Outcome.FAILED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertTrue(fresh.isEmpty());
    }
}
