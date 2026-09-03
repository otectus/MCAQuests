package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-shot import of 1.20.1 Forge player quest data on world upgrade (spec section 15.3).
 *
 * <p>The Forge build stored {@link PlayerQuestData} at
 * {@code playerdata/<uuid>.dat -> ForgeCaps -> "mcaquests:player_quests"}. NeoForge reads attachments
 * from the {@code neoforge:attachments} tag and silently drops {@code ForgeCaps} — without this shim,
 * every player's quest log, offers, tracked quest, history and titles would reset when a Forge world
 * is opened under NeoForge. On login, if the player's attachment carries nothing yet, we read the raw
 * player file and import the old capability tag.
 *
 * <p>There is only one chance to get this right. Dropping {@code ForgeCaps} is not just a read
 * behaviour: NeoForge rewrites the player file without the unrecognised tag on the next save, so a
 * failed or skipped import is permanent, and retrying on the following login finds a file that no
 * longer holds the legacy blob. That is why a miss falls back to vanilla's {@code <uuid>.dat_old}
 * backup, which still carries the previous copy for one more save cycle.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} so the import lands before the ordinary login handlers
 * ({@code QuestProgressEvents.onPlayerLogin}) sync the quest log to the client. <strong>This ordering
 * is load-bearing across mods:</strong> MCA: Reputation's legacy import runs from its own
 * {@code PlayerLoggedInEvent} handler at NORMAL priority and judges eligibility by reading this mod's
 * quest attachment (see {@code QuestsLegacyImportProvider}). If either handler ever moves, a Forge
 * upgrader could be judged against a not-yet-migrated attachment and silently lose earned titles.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ForgeCapsMigration {

    /**
     * Players whose player file has already been inspected during this server session.
     *
     * <p>The attachment gate below ({@code isEmpty} / {@code migratedFromForge}) never becomes true for
     * a player who simply has no quest data, so without this set every one of their logins would pay
     * for a synchronous whole-file read on the login thread, forever. Cleared when the server stops so
     * an integrated server that opens a different world re-checks from scratch; the persisted
     * {@code migrated_from_forge} flag is what keeps the import itself idempotent.
     */
    private static final Set<UUID> CHECKED_THIS_SESSION = ConcurrentHashMap.newKeySet();

    /** What looking for the legacy blob in a player file established. */
    enum Outcome {
        /** Legacy data was found and loaded into the attachment. */
        IMPORTED,
        /** The file was read (or does not exist) and carries no legacy quest data. */
        ABSENT,
        /** The file could not be read, so whether it holds legacy data is unknown. */
        FAILED
    }

    private ForgeCapsMigration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            PlayerQuestData data = player.getData(QuestAttachments.PLAYER_QUESTS);
            if (!data.isEmpty() || data.migratedFromForge()) {
                return; // NeoForge-era data (or a prior import) already exists; never overwrite it.
            }
            MinecraftServer server = player.getServer();
            if (server == null || CHECKED_THIS_SESSION.contains(player.getUUID())) {
                return;
            }
            Outcome outcome = importLegacyData(
                    server.getWorldPath(LevelResource.PLAYER_DATA_DIR), player.getUUID(), data);
            if (outcome != Outcome.FAILED) {
                // Only a completed check counts. After a read error the next login should look again:
                // the answer is still unknown, and .dat_old may yet yield the blob.
                CHECKED_THIS_SESSION.add(player.getUUID());
            }
        } catch (Throwable t) {
            // A corrupt/unreadable player file must never block login; the player just starts fresh.
            McaQuests.LOGGER.error("[MCA: Quests] Failed to import legacy Forge quest data for {}; "
                    + "continuing with empty quest state.", event.getEntity().getUUID(), t);
        }
    }

    /** The session cache is only valid for the world it was built against. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHECKED_THIS_SESSION.clear();
    }

    /**
     * Looks for {@code uuid}'s legacy quest blob in {@code playerDataDir}, trying the live
     * {@code <uuid>.dat} first and vanilla's {@code <uuid>.dat_old} backup second, and loads it into
     * {@code data} if found. Package-visible so the unit test can drive the two-file fallback against a
     * temp directory.
     *
     * @return {@link Outcome#FAILED} only when neither file could be read, so the caller knows the
     *         question is still open and worth asking again on the next login
     */
    static Outcome importLegacyData(Path playerDataDir, UUID uuid, PlayerQuestData data) {
        Path live = playerDataDir.resolve(uuid + ".dat");
        Path backup = playerDataDir.resolve(uuid + ".dat_old");

        Outcome fromLive;
        try {
            fromLive = readLegacyBlob(live, uuid, data);
        } catch (Throwable t) {
            fromLive = Outcome.FAILED;
            McaQuests.LOGGER.error("[MCA: Quests] Could not read {} while looking for 1.20.1 Forge quest "
                            + "data for {}; trying the {} backup instead.",
                    live.getFileName(), uuid, backup.getFileName(), t);
        }
        if (fromLive == Outcome.IMPORTED) {
            return Outcome.IMPORTED;
        }

        // Also reached when the live file read cleanly but held no ForgeCaps: NeoForge may already have
        // rewritten it without the tag, in which case the backup is the only remaining copy.
        Outcome fromBackup;
        try {
            fromBackup = readLegacyBlob(backup, uuid, data);
        } catch (Throwable t) {
            fromBackup = Outcome.FAILED;
            if (fromLive == Outcome.FAILED) {
                McaQuests.LOGGER.error("[MCA: Quests] Could not read {} for {} either; giving up on the "
                                + "legacy import — the player continues with empty quest state.",
                        backup.getFileName(), uuid, t);
            }
        }
        if (fromBackup == Outcome.IMPORTED) {
            return Outcome.IMPORTED;
        }
        // One clean read that found no legacy data settles the question, even if the other failed.
        return fromLive == Outcome.ABSENT || fromBackup == Outcome.ABSENT ? Outcome.ABSENT : Outcome.FAILED;
    }

    /** Reads one raw player file and imports its legacy blob if it has one. */
    private static Outcome readLegacyBlob(Path file, UUID uuid, PlayerQuestData data) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Outcome.ABSENT;
        }
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (!importFromForgeCaps(root, data)) {
            return Outcome.ABSENT;
        }
        McaQuests.LOGGER.info("[MCA: Quests] Imported 1.20.1 Forge quest data (ForgeCaps -> attachment) "
                + "for player {} from {}", uuid, file.getFileName());
        return Outcome.IMPORTED;
    }

    /**
     * Loads the legacy {@code ForgeCaps -> mcaquests:player_quests} tag from a raw player-file root into
     * {@code data} and marks it migrated. The legacy blob is exactly what {@link PlayerQuestData#save()}
     * wrote in 1.5.3, so {@link PlayerQuestData#load(CompoundTag)} — the same method
     * {@code deserializeNBT} delegates to — reads every field of it; the marker is set separately
     * because a Forge-era tag has no {@code migrated_from_forge} key to carry it. Package-visible and
     * side-effect-free beyond {@code data} so the unit test can drive it with a canned 1.20.1 NBT blob.
     *
     * @return true when legacy data was found and imported
     */
    static boolean importFromForgeCaps(CompoundTag playerFileRoot, PlayerQuestData data) {
        if (!playerFileRoot.contains("ForgeCaps", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag caps = playerFileRoot.getCompound("ForgeCaps");
        String key = QuestAttachments.ID.toString();
        if (!caps.contains(key, Tag.TAG_COMPOUND)) {
            return false;
        }
        data.load(caps.getCompound(key));
        data.markMigratedFromForge();
        return true;
    }
}
