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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-shot import of 1.20.1 Forge player quest data on world upgrade (spec section 16; port Phase 8.3).
 *
 * <p>The Forge build stored {@link PlayerQuestData} at
 * {@code playerdata/<uuid>.dat → ForgeCaps → "mcaquests:player_quests"}. NeoForge reads attachments
 * from the {@code neoforge:attachments} tag and silently drops {@code ForgeCaps} — without this
 * shim, every player's quest log / titles / history would reset when a Forge world is opened under
 * NeoForge. On login, if the player's attachment carries no data yet, we read the raw player file
 * (safe: the disk copy still holds {@code ForgeCaps} until NeoForge first rewrites it, and it is
 * only rewritten on the next save) and import the old capability tag.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} so the import lands before the ordinary login handlers
 * ({@code QuestProgressEvents.onPlayerLogin}) sync the quest log to the client.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class ForgeCapsMigration {

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
            if (server == null) {
                return;
            }
            Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getUUID() + ".dat");
            if (!Files.isRegularFile(file)) {
                return;
            }
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (importFromForgeCaps(root, data)) {
                McaQuests.LOGGER.info(
                        "[MCA: Quests] Imported 1.20.1 Forge quest data (ForgeCaps -> attachment) for player {}",
                        player.getGameProfile().getName());
            }
        } catch (Throwable t) {
            // A corrupt/unreadable player file must never block login; the player just starts fresh.
            McaQuests.LOGGER.error("[MCA: Quests] Failed to import legacy Forge quest data for {}; "
                    + "continuing with empty quest state.", event.getEntity().getScoreboardName(), t);
        }
    }

    /**
     * Loads the legacy {@code ForgeCaps → mcaquests:player_quests} tag from a raw player-file root
     * into {@code data} and marks it migrated. Package-visible and side-effect-free beyond
     * {@code data} so the unit test can drive it with a canned 1.20.1 NBT blob.
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
