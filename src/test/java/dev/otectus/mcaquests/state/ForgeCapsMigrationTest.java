package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.quest.target.FrozenLocation;
import dev.otectus.mcaquests.quest.template.ResolvedTemplate;
import dev.otectus.mcaquests.quest.template.ResolvedValue;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spec §15.3 world-upgrade matrix against {@link ForgeCapsMigration}.
 *
 * <p>The 1.20.1 Forge build stored {@link PlayerQuestData} at
 * {@code playerdata/<uuid>.dat -> ForgeCaps -> "mcaquests:player_quests"}. NeoForge reads attachments
 * from {@code neoforge:attachments} and drops {@code ForgeCaps} — and not only on read: it rewrites
 * the player file without the unrecognised tag on the very next save. So there is exactly one login
 * in which the import can happen, vanilla's {@code <uuid>.dat_old} is the only second chance, and a
 * read failure that got cached as "nothing to import" would be a player's whole quest log gone with
 * no way to notice.
 *
 * <p>That is why the outcome is three-valued rather than a boolean, and why the two cases this file
 * cares most about are the ones that distinguish {@code ABSENT} (a completed check: cacheable, stop
 * asking) from {@code FAILED} (the question is still open: ask again next login).
 *
 * <p>Fixtures are built in code rather than read from {@code src/test/resources}: this repo has no
 * test-resources tree, and a hand-placed binary fixture would in any case be less trustworthy than
 * one produced by {@code PlayerQuestData.save()} itself — the legacy blob is byte-for-byte what that
 * method wrote under Forge, because the field set did not change in the port.
 */
class ForgeCapsMigrationTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final UUID PLAYER = UUID.fromString("b6b6b6b6-0000-4000-8000-00000000000f");
    private static final UUID GIVER = UUID.fromString("a5a5a5a5-0000-4000-8000-000000000001");
    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "well_repair");
    private static final ResourceLocation OFFERED =
            ResourceLocation.fromNamespaceAndPath("mcaquests", "lost_child");
    private static final ResourceLocation DONE = ResourceLocation.fromNamespaceAndPath("mcaquests", "first_delivery");
    private static final ResourceLocation PROJECT = ResourceLocation.fromNamespaceAndPath("mcaquests", "guardhouse");
    private static final ResourceLocation TITLE =
            ResourceLocation.fromNamespaceAndPath("mcaquests", "village_friend");
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation FARMER = ResourceLocation.withDefaultNamespace("farmer");

    /** The DataVersion a 1.20.1 player file carried. Irrelevant to the shim; present so the fixture is real. */
    private static final int DATA_VERSION_1_20_1 = 3465;

    // ---------------------------------------------------------------- fixtures

    /**
     * A blob with something in all seven persisted fields, and with the parts of {@link ActiveQuest}
     * that a partial port is likeliest to lose: the frozen template values, the frozen reward amounts
     * and the frozen destinations, none of which can be recomputed once they are gone.
     */
    private static PlayerQuestData legacyData() {
        PlayerQuestData data = new PlayerQuestData();

        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        values.put("count", new ResolvedValue.IntValue(12));
        ActiveQuest quest = ActiveQuest.create(QUEST, GIVER, Component.literal("Anna"), FARMER, OVERWORLD,
                1234L, 2, new ResolvedTemplate(values));
        quest.progress(0).add(3);
        quest.progress(1).add(9);
        quest.freezeReward(0, 17);
        quest.freezeLocation("well:overworld", FrozenLocation.building(new BlockPos(120, 64, -40), OVERWORLD,
                7, 41, "mcaquests:well", 2));
        data.add(quest);

        data.history().recordCompletion(DONE, GIVER);
        data.titles().grantGlobal(TITLE);
        data.titles().grantVillage(OVERWORLD, 7, TITLE);
        ProgressionStats.increment(data.stats().projectCompletions(), PROJECT, 3);
        data.offers().get(GIVER).redraw(
                List.of(new OfferSession.Slot(OFFERED, new ResolvedTemplate(values),
                        Component.literal("A word with you?"))),
                1000L, 1, 4242L);
        data.setTracked(quest);
        return data;
    }

    /** The on-disk player-file shape the 1.20.1 Forge build wrote. */
    private static CompoundTag legacyPlayerFile(PlayerQuestData source) {
        CompoundTag caps = new CompoundTag();
        caps.put(QuestAttachments.ID.toString(), source.save());
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", DATA_VERSION_1_20_1);
        root.put("ForgeCaps", caps);
        return root;
    }

    /** A NeoForge-era player file: read cleanly, holds no legacy blob. */
    private static CompoundTag rewrittenPlayerFile() {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3955);
        return root;
    }

    private static void write(Path file, CompoundTag root) throws IOException {
        NbtIo.writeCompressed(root, file);
    }

    private static void writeCorrupt(Path file) throws IOException {
        Files.write(file, "this is not a gzipped NBT compound".getBytes(StandardCharsets.UTF_8));
    }

    private static Path live(Path playerData) {
        return playerData.resolve(PLAYER + ".dat");
    }

    private static Path backup(Path playerData) {
        return playerData.resolve(PLAYER + ".dat_old");
    }

    /** Every field of {@link #legacyData()}, read back off the imported attachment. */
    private static void assertLegacyDataPresent(PlayerQuestData data, String what) {
        assertEquals(1, data.activeCount(), what + ": active");
        ActiveQuest quest = data.find(QUEST, GIVER).orElseThrow();
        assertEquals(Component.literal("Anna"), quest.villagerName(), what + ": villager_name");
        assertEquals(FARMER, quest.villagerProfession(), what + ": profession");
        assertEquals(OVERWORLD, quest.dimension(), what + ": dimension");
        assertEquals(3, quest.progress(0).count(), what + ": objective 0 progress");
        assertEquals(9, quest.progress(1).count(), what + ": objective 1 progress");
        assertEquals(OptionalInt.of(17), quest.frozenReward(0), what + ": frozen rewards");
        FrozenLocation location = quest.frozenLocation("well:overworld");
        assertNotNull(location, what + ": frozen locations");
        assertEquals(new BlockPos(120, 64, -40), location.pos(), what + ": frozen location pos");
        assertEquals(OptionalInt.of(7), location.villageId(), what + ": frozen location village");
        assertEquals(Optional.of("mcaquests:well"), location.family(), what + ": frozen location family");
        // The frozen template has no accessor of its own -- it is only ever read through the resolver
        // that renders dialogue, which is exactly the thing that would break if it were lost.
        assertEquals("12", quest.textResolver((String) null).substituteLiteral("{count}").getString(),
                what + ": frozen template values");

        assertEquals(1, data.history().completionCountByGiver(DONE, GIVER), what + ": history");
        assertTrue(data.titles().hasGlobal(TITLE), what + ": global titles");
        assertTrue(data.titles().hasVillage(OVERWORLD, 7, TITLE), what + ": per-village titles");
        assertEquals(3, ProgressionStats.count(data.stats().projectCompletions(), PROJECT), what + ": stats");
        OfferSession offers = data.offers().find(GIVER).orElseThrow();
        assertEquals(1, offers.slots().size(), what + ": offers");
        assertEquals(OFFERED, offers.slots().get(0).questId(), what + ": offered quest id");
        assertEquals("12", offers.slots().get(0).frozenValues().get("count").orElseThrow().plain(),
                what + ": the offer keeps its frozen values, so a redrawn offer cannot reroll");
        assertEquals(QUEST, data.tracked().orElseThrow().questId(), what + ": tracked");
        assertTrue(data.migratedFromForge(), what + ": the one-shot import marker");
    }

    // ---------------------------------------------------------------- the blob reader

    @Test
    @DisplayName("every 1.5.3 field survives the ForgeCaps import")
    void importsEveryFieldOfTheLegacyBlob() {
        PlayerQuestData fresh = new PlayerQuestData();
        assertTrue(fresh.isEmpty(), "precondition: the attachment starts empty");

        assertTrue(ForgeCapsMigration.importFromForgeCaps(legacyPlayerFile(legacyData()), fresh));

        assertLegacyDataPresent(fresh, "import");
    }

    @Test
    @DisplayName("the migration marker survives the attachment round trip, so the import never runs twice")
    void migratedFlagPersists() {
        PlayerQuestData fresh = new PlayerQuestData();
        ForgeCapsMigration.importFromForgeCaps(legacyPlayerFile(legacyData()), fresh);

        // serializeNBT, not save(): save() is deliberately the marker-free 1.20.1 payload -- it is what
        // writes the legacy blob in the fixture above -- and the marker is added around it by the
        // attachment serialiser, which is the path the player file actually takes.
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.deserializeNBT(RegistryAccess.EMPTY, fresh.serializeNBT(RegistryAccess.EMPTY));

        assertTrue(reloaded.migratedFromForge(),
                "without this the shim would look at the rewritten file again on the next login");
        assertLegacyDataPresent(reloaded, "reloaded");
    }

    @Test
    @DisplayName("the legacy blob is exactly what save() writes, with the marker added only around it")
    void theLegacyBlobIsTheMarkerFreePayload() {
        // Why the fixture above is trustworthy: the Forge-era tag had no migrated_from_forge key, and
        // save() still writes none, so the blob this suite feeds the shim is the shape a 1.20.1 jar
        // wrote. The marker exists only in the attachment envelope.
        PlayerQuestData data = legacyData();
        assertFalse(data.save().contains("migrated_from_forge"),
                "save() must stay byte-for-byte the 1.20.1 payload");
        data.markMigratedFromForge();
        assertTrue(data.serializeNBT(RegistryAccess.EMPTY).contains("migrated_from_forge"));
    }

    @Test
    @DisplayName("a player file with no ForgeCaps tag imports nothing and marks nothing")
    void doesNothingWithoutForgeCaps() {
        PlayerQuestData fresh = new PlayerQuestData();

        assertFalse(ForgeCapsMigration.importFromForgeCaps(rewrittenPlayerFile(), fresh));

        assertTrue(fresh.isEmpty());
        assertFalse(fresh.migratedFromForge());
    }

    @Test
    @DisplayName("another mod's capability under ForgeCaps is not ours")
    void doesNothingWhenForgeCapsLacksOurKey() {
        PlayerQuestData fresh = new PlayerQuestData();
        CompoundTag caps = new CompoundTag();
        caps.put("somemod:other_cap", new CompoundTag());
        CompoundTag root = new CompoundTag();
        root.put("ForgeCaps", caps);

        assertFalse(ForgeCapsMigration.importFromForgeCaps(root, fresh));

        assertTrue(fresh.isEmpty());
    }

    // ---------------------------------------------------------------- the §15.3 file matrix

    @Test
    @DisplayName("live file carrying the blob: imported from the live file")
    void importsFromTheLiveFile(@TempDir Path playerData) throws IOException {
        write(live(playerData), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh, "live");
    }

    @Test
    @DisplayName("live file already rewritten without ForgeCaps: the backup is the fallback")
    void fallsBackToTheBackupWhenTheLiveFileNoLongerCarriesTheBlob(@TempDir Path playerData) throws IOException {
        // The exact shape of a second login after NeoForge saved the player once.
        write(live(playerData), rewrittenPlayerFile());
        write(backup(playerData), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh, "backup fallback");
    }

    @Test
    @DisplayName("no live file at all: the backup still yields the blob")
    void importsFromTheBackupWhenTheLiveFileIsAbsent(@TempDir Path playerData) throws IOException {
        write(backup(playerData), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh, "backup only");
    }

    @Test
    @DisplayName("both files absent: nothing to import, and the answer is settled")
    void reportsAbsentWhenNeitherFileExists(@TempDir Path playerData) {
        PlayerQuestData fresh = new PlayerQuestData();

        // ABSENT rather than FAILED: a brand-new player has no files, and re-reading the disk on every
        // one of their logins forever would be the cost of getting this wrong.
        assertEquals(ForgeCapsMigration.Outcome.ABSENT,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertTrue(fresh.isEmpty());
        assertFalse(fresh.migratedFromForge(), "no legacy data must mean no marker written");
    }

    @Test
    @DisplayName("a readable file with no legacy data: nothing to import, and the answer is settled")
    void reportsAbsentForAPlayerWithNoLegacyData(@TempDir Path playerData) throws IOException {
        write(live(playerData), rewrittenPlayerFile());

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.ABSENT,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertTrue(fresh.isEmpty());
    }

    @Test
    @DisplayName("corrupt live file, valid backup: imported from the backup")
    void importsFromTheBackupWhenTheLiveFileIsCorrupt(@TempDir Path playerData) throws IOException {
        writeCorrupt(live(playerData));
        write(backup(playerData), legacyPlayerFile(legacyData()));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh, "corrupt live");
    }

    @Test
    @DisplayName("valid live file, corrupt backup: the live file wins and the backup is never needed")
    void prefersTheLiveFileOverTheBackup(@TempDir Path playerData) throws IOException {
        write(live(playerData), legacyPlayerFile(legacyData()));
        writeCorrupt(backup(playerData));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
        assertLegacyDataPresent(fresh, "corrupt backup");
    }

    @Test
    @DisplayName("both files corrupt: FAILED, which is the outcome the caller must not cache")
    void reportsFailedWhenNeitherFileCanBeRead(@TempDir Path playerData) throws IOException {
        writeCorrupt(live(playerData));
        writeCorrupt(backup(playerData));

        PlayerQuestData fresh = new PlayerQuestData();

        // The distinction this whole enum exists for. FAILED means the question is still open: whether
        // this player has legacy data is unknown, so the next login must look again rather than write
        // them off as quest-less. ABSENT here would make one transient read error permanent.
        ForgeCapsMigration.Outcome outcome = ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh);
        assertEquals(ForgeCapsMigration.Outcome.FAILED, outcome);
        assertFalse(outcome == ForgeCapsMigration.Outcome.ABSENT,
                "a failed read must never be reported as a completed one");
        assertTrue(fresh.isEmpty());
    }

    @Test
    @DisplayName("one clean read that found nothing settles the question even if the other read failed")
    void oneCleanReadIsEnoughToSettleIt(@TempDir Path playerData) throws IOException {
        write(live(playerData), rewrittenPlayerFile());
        writeCorrupt(backup(playerData));

        PlayerQuestData fresh = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.ABSENT,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, fresh));
    }

    // ---------------------------------------------------------------- idempotence and overwrite protection

    @Test
    @DisplayName("importing twice imports once: the second pass finds the data already there")
    void importIsIdempotent(@TempDir Path playerData) throws IOException {
        write(live(playerData), legacyPlayerFile(legacyData()));

        PlayerQuestData data = new PlayerQuestData();
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, data));
        assertEquals(1, data.activeCount());
        assertTrue(data.migratedFromForge());

        // The login handler's gate -- non-empty, or already marked -- is what stops a second pass; both
        // halves of it are true here, which is the property that makes the import one-shot.
        assertFalse(data.isEmpty(), "a re-import would be gated out by isEmpty");
        assertTrue(data.migratedFromForge(), "and by the persisted marker, which survives a restart");

        // Driven a second time anyway, the quest set does not double: the blob names the same quest
        // from the same giver, so there is still exactly one.
        assertEquals(ForgeCapsMigration.Outcome.IMPORTED,
                ForgeCapsMigration.importLegacyData(playerData, PLAYER, data));
        assertEquals(1, data.activeCount(), "a repeated import must not duplicate the player's quests");
    }

    @Test
    @DisplayName("a player who already has NeoForge-era data is never overwritten")
    void nonEmptyAttachmentIsProtected() {
        // The gate the login handler applies before it reads anything at all: quest data that exists
        // under NeoForge is newer than anything in a Forge-era file, so the file must not be consulted.
        PlayerQuestData current = new PlayerQuestData();
        current.add(ActiveQuest.create(OFFERED, GIVER, Component.literal("Otto"), null, OVERWORLD,
                9000L, 1, null));

        assertFalse(current.isEmpty(), "the gate is isEmpty, and it is false here");
        assertFalse(current.migratedFromForge());
        assertEquals(1, current.activeCount());
        assertTrue(current.hasActive(OFFERED, GIVER),
                "the quest this player holds now is the one they must still hold afterwards");
    }
}
