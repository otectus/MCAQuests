package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Translates world state into {@link TriggerSignal}s for {@link SituationManager} (the "Living Village"
 * phase, 0.8.0). Two entry points:
 *
 * <ul>
 *   <li>{@link #onVillagerDeath} — event-driven, called from the {@code LivingDeathEvent} hook.</li>
 *   <li>{@link #scan} — the periodic sweep of villages near online players for tick-driven conditions
 *       (active raid, low food, nightfall, infected residents, missing kin).</li>
 * </ul>
 *
 * <p>Detection is intentionally player-proximity-driven: situations only matter where players are, and
 * this bounds the per-tick work. All MCA access goes through {@link McaCompat}; moon/night math is plain
 * vanilla. The throttle in {@link SituationManager} keeps repeated signals from spamming situations.
 */
public final class SituationDetectors {

    /** How far from a player to look for a village to scan. */
    private static final int DETECTION_RADIUS = 128;

    private SituationDetectors() {
    }

    /** Emits a {@code villager_death} signal for an MCA villager that just died (if it has a village). */
    public static void onVillagerDeath(MinecraftServer server, ServerLevel level, Entity dead) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(dead);
        if (villageId.isEmpty()) {
            return;
        }
        UUID familyRoot = McaCompat.getFamilyRootId(dead).orElse(null);
        SituationManager.onSignal(server,
                TriggerSignal.villagerDeath(level, villageId.getAsInt(), dead.getUUID(), familyRoot));
    }

    /** Sweeps every village near an online player once, emitting any tick-driven signals it detects. */
    public static void scan(MinecraftServer server) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return;
        }
        Set<String> scanned = new HashSet<>();
        int budget = McaQuestsConfig.COMMON.townsteadMaxVillagesPerPass.get();
        long rotation = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (scanned.size() >= budget) {
                break; // bounded per pass; the players not reached this time are reached the next
            }
            ServerLevel level = player.serverLevel();
            OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), DETECTION_RADIUS);
            if (villageId.isEmpty()) {
                continue;
            }
            String key = level.dimension().location() + "#" + villageId.getAsInt();
            if (scanned.add(key)) {
                scanVillage(server, level, villageId.getAsInt());
                TownsteadSituationDetector.scanVillage(server, level, villageId.getAsInt(), rotation);
            }
        }
    }

    private static void scanVillage(MinecraftServer server, ServerLevel level, int villageId) {
        McaCompat.villageCenter(level, villageId).ifPresent(center -> {
            if (McaCompat.isRaidActive(level, center)) {
                SituationManager.onSignal(server, TriggerSignal.raid(level, villageId));
            }
        });
        McaCompat.getVillageFoodCount(level, villageId).ifPresent(food ->
                SituationManager.onSignal(server, TriggerSignal.lowFood(level, villageId, food)));
        if (isNight(level)) {
            SituationManager.onSignal(server, TriggerSignal.night(level, villageId, isFullMoon(level)));
        }

        UUID infectedVillager = null;
        float infectionProgress = 0f;
        UUID missingKinRoot = null;
        for (Entity resident : McaCompat.loadedVillageResidents(level, villageId)) {
            if (infectedVillager == null) {
                float progress = McaCompat.getInfectionProgress(resident);
                if (progress > 0f) {
                    infectedVillager = resident.getUUID();
                    infectionProgress = progress;
                }
            }
            if (missingKinRoot == null && McaCompat.hasMissingRelative(level, resident)) {
                missingKinRoot = McaCompat.getFamilyRootId(resident).orElse(resident.getUUID());
            }
            if (infectedVillager != null && missingKinRoot != null) {
                break;
            }
        }
        if (infectedVillager != null) {
            SituationManager.onSignal(server,
                    TriggerSignal.infection(level, villageId, infectedVillager, infectionProgress));
        }
        if (missingKinRoot != null) {
            SituationManager.onSignal(server, TriggerSignal.missingKin(level, villageId, missingKinRoot));
        }
    }

    /** Dusk-to-dawn window (vanilla time-of-day 13000..23000). */
    private static boolean isNight(ServerLevel level) {
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        return timeOfDay >= 13000L && timeOfDay < 23000L;
    }

    /** Vanilla moon phase 0 (full moon). */
    private static boolean isFullMoon(ServerLevel level) {
        return Math.floorMod(Math.floorDiv(level.getDayTime(), 24000L), 8L) == 0L;
    }
}
